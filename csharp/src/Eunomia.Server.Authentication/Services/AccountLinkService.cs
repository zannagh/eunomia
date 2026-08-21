// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using Eunomia.Server.Authentication.Configuration;
using Eunomia.Server.Authentication.Handlers;
using Eunomia.Server.Authentication.Resources;
using Eunomia.Server.Data;
using Eunomia.Server.Data.Entities;
using Microsoft.EntityFrameworkCore;

namespace Eunomia.Server.Authentication.Services;

/// <summary>
/// Drives the Modrinth/Discord account-link OAuth flow and persists the resulting
/// <see cref="UserExternalLink"/> rows. Reuses <see cref="ISecurityTokenHandler"/> for the provider
/// code exchanges so the HTTP shape matches the login path.
/// </summary>
public class AccountLinkService : IAccountLinkService
{
    public const string Modrinth = "modrinth";
    public const string Discord = "discord";

    private readonly EunomiaAuthSettings settings;
    private readonly ISecurityTokenHandler tokenHandler;
    private readonly IDbContextFactory<EunomiaDbContext> dbContextFactory;

    public AccountLinkService(
        EunomiaAuthSettings settings,
        ISecurityTokenHandler tokenHandler,
        IDbContextFactory<EunomiaDbContext> dbContextFactory)
    {
        this.settings = settings;
        this.tokenHandler = tokenHandler;
        this.dbContextFactory = dbContextFactory;
    }

    public bool IsLinkable(string provider)
    {
        return GetProvider(provider) is { Enabled: true };
    }

    public string? BuildAuthorizeUrl(string provider, string state, string redirectUri)
    {
        OAuthProviderSettings? config = GetProvider(provider);
        if (config is not { Enabled: true })
        {
            return null;
        }

        Dictionary<string, string> parameters = new()
        {
            ["response_type"] = "code",
            ["client_id"] = config.ClientId,
            ["scope"] = ScopeFor(provider),
            ["redirect_uri"] = redirectUri,
            ["state"] = state,
        };

        string query = string.Join("&", parameters.Select(kvp =>
            $"{Uri.EscapeDataString(kvp.Key)}={Uri.EscapeDataString(kvp.Value)}"));
        return $"{config.OAuthUrl}?{query}";
    }

    public async Task<UserExternalLink?> CompleteLinkAsync(string provider, string code, string redirectUri, Guid userId)
    {
        OAuthProviderSettings? config = GetProvider(provider);
        if (config is not { Enabled: true })
        {
            return null;
        }

        VerificationResult result = provider switch
        {
            Modrinth => await tokenHandler.VerifyModrinthAuthentication(config.ClientId, config.ClientSecret, code, redirectUri),
            Discord => await tokenHandler.VerifyDiscordAuthentication(config.ClientId, config.ClientSecret, code, redirectUri),
            _ => new VerificationResult { Success = false, UserName = string.Empty, UserId = string.Empty },
        };

        if (!result.Success || string.IsNullOrEmpty(result.UserId))
        {
            return null;
        }

        return await UpsertLinkAsync(userId, provider, result.UserId, result.UserName);
    }

    public async Task<UserExternalLink> UpsertLinkAsync(Guid userId, string provider, string externalId, string handle)
    {
        await using EunomiaDbContext dbContext = await dbContextFactory.CreateDbContextAsync();

        // A provider identity maps to at most one Eunomia user. If this external account is already held by
        // a different user (e.g. it was claimed earlier by a standalone provider login), release that claim
        // before (re)linking so linking succeeds cleanly instead of silently forking the identity in two.
        List<UserExternalLink> conflicting = await dbContext.UserExternalLinks
            .Where(l => l.Provider == provider && l.ExternalId == externalId && l.UserId != userId)
            .ToListAsync();
        if (conflicting.Count > 0)
        {
            dbContext.UserExternalLinks.RemoveRange(conflicting);
        }

        UserExternalLink? link = await dbContext.UserExternalLinks
            .FirstOrDefaultAsync(l => l.UserId == userId && l.Provider == provider);

        if (link == null)
        {
            link = new UserExternalLink
            {
                UserId = userId,
                Provider = provider,
                ExternalId = externalId,
                Handle = handle,
                LinkedAt = DateTime.UtcNow,
            };
            await dbContext.UserExternalLinks.AddAsync(link);
        }
        else
        {
            link.ExternalId = externalId;
            link.Handle = handle;
            link.LinkedAt = DateTime.UtcNow;
        }

        await dbContext.SaveChangesAsync();
        return link;
    }

    public async Task<IReadOnlyList<UserExternalLink>> GetLinksAsync(Guid userId)
    {
        await using EunomiaDbContext dbContext = await dbContextFactory.CreateDbContextAsync();
        return await dbContext.UserExternalLinks
            .Where(l => l.UserId == userId)
            .ToListAsync();
    }

    private OAuthProviderSettings? GetProvider(string provider) => provider switch
    {
        Modrinth => settings.ModrinthOAuth,
        Discord => settings.DiscordOAuth,
        _ => null,
    };

    private static string ScopeFor(string provider) => provider switch
    {
        Modrinth => "USER_READ",
        Discord => "identify",
        _ => string.Empty,
    };
}
