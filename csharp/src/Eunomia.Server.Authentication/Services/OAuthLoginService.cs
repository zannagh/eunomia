// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using Eunomia.Server.Authentication.Configuration;
using Eunomia.Server.Authentication.Handlers;
using Eunomia.Server.Authentication.Helpers;
using Eunomia.Server.Authentication.Providers;
using Eunomia.Server.Authentication.Resources;
using Eunomia.Server.Data.Entities;

namespace Eunomia.Server.Authentication.Services;

/// <summary>
/// Default <see cref="IOAuthLoginService"/>: looks up which provider issued the code, runs that
/// provider's exchange through <see cref="ISecurityTokenHandler"/>, and resolves the profile to a
/// <see cref="User"/> row (refreshing the Modrinth link on Modrinth sign-in).
/// </summary>
public class OAuthLoginService : IOAuthLoginService
{
    private readonly EunomiaAuthSettings settings;
    private readonly ISecurityTokenHandler tokenHandler;
    private readonly CodeBasedAuthProvider codeBasedAuthProvider;
    private readonly ICurrentUserService currentUserService;
    private readonly IAccountLinkService accountLinkService;

    public OAuthLoginService(
        EunomiaAuthSettings settings,
        ISecurityTokenHandler tokenHandler,
        CodeBasedAuthProvider codeBasedAuthProvider,
        ICurrentUserService currentUserService,
        IAccountLinkService accountLinkService)
    {
        this.settings = settings;
        this.tokenHandler = tokenHandler;
        this.codeBasedAuthProvider = codeBasedAuthProvider;
        this.currentUserService = currentUserService;
        this.accountLinkService = accountLinkService;
    }

    public async Task<OAuthLoginResult> ResolveCodeAsync(string code, string redirectUri)
    {
        if (string.IsNullOrEmpty(code))
        {
            return OAuthLoginResult.Failed(OAuthLoginStatus.UnknownCode);
        }

        if (!codeBasedAuthProvider.GetIdentityProviderByCode(code, out string? provider)
            || string.IsNullOrEmpty(provider))
        {
            return OAuthLoginResult.Failed(OAuthLoginStatus.UnknownCode);
        }

        VerificationResult result = await VerifyProviderAsync(provider, code, redirectUri);
        if (!result.Success)
        {
            return OAuthLoginResult.Failed(OAuthLoginStatus.ProviderRejected);
        }

        if (string.IsNullOrEmpty(result.UserId) || string.IsNullOrEmpty(result.UserName))
        {
            return OAuthLoginResult.Failed(OAuthLoginStatus.IncompleteProfile);
        }

        string identifier = ClaimsHelper.ToUserIdentifier(result.UserName, result.UserId);
        User user = await currentUserService.EnsureUserAsync(identifier);

        if (provider == AccountLinkService.Modrinth)
        {
            await accountLinkService.UpsertLinkAsync(user.Id, AccountLinkService.Modrinth, result.UserId, result.UserName);
        }

        return OAuthLoginResult.Succeeded(user, result.UserName, result.UserId);
    }

    private Task<VerificationResult> VerifyProviderAsync(string provider, string code, string redirectUri)
    {
        return provider switch
        {
            "google" => tokenHandler.VerifyGoogleAuthentication(
                settings.GoogleOAuth.ClientId, settings.GoogleOAuth.ClientSecret, code, redirectUri, "authorization_code"),
            "microsoft" => tokenHandler.VerifyMicrosoftAuthentication(
                settings.MicrosoftOAuth.ClientId, settings.MicrosoftOAuth.ClientSecret, code, redirectUri, "authorization_code"),
            "github" => tokenHandler.VerifyGitHubAuthentication(
                settings.GitHubOAuth.ClientId, settings.GitHubOAuth.ClientSecret, code),
            "modrinth" => tokenHandler.VerifyModrinthAuthentication(
                settings.ModrinthOAuth.ClientId, settings.ModrinthOAuth.ClientSecret, code, redirectUri),
            _ => Task.FromResult(new VerificationResult { Success = false, UserId = string.Empty, UserName = string.Empty }),
        };
    }
}
