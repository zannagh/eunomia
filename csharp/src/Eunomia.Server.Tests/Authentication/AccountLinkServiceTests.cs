// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using Eunomia.Server.Authentication.Configuration;
using Eunomia.Server.Authentication.Handlers;
using Eunomia.Server.Authentication.Resources;
using Eunomia.Server.Authentication.Services;
using Eunomia.Server.Data;
using Eunomia.Server.Data.Entities;
using Eunomia.Server.Tests.TestSupport;
using Microsoft.Extensions.Configuration;
using NSubstitute;

namespace Eunomia.Server.Tests.Authentication;

public class AccountLinkServiceTests
{
    [Fact]
    public void BuildAuthorizeUrl_UsesProviderScope_AndState()
    {
        using SqliteDbContextFactory factory = new();
        AccountLinkService service = NewService(factory, Substitute.For<ISecurityTokenHandler>());

        string? modrinth = service.BuildAuthorizeUrl(AccountLinkService.Modrinth, "state123", "https://host/account/link/modrinth/callback");
        string? discord = service.BuildAuthorizeUrl(AccountLinkService.Discord, "state123", "https://host/account/link/discord/callback");

        Assert.NotNull(modrinth);
        Assert.StartsWith("https://modrinth.com/auth/authorize?", modrinth);
        Assert.Contains("scope=USER_READ", modrinth);
        Assert.Contains("state=state123", modrinth);
        Assert.Contains("client_id=mr-id", modrinth);

        Assert.NotNull(discord);
        Assert.Contains("scope=identify", discord);
    }

    [Fact]
    public void IsLinkable_FalseForDisabledOrUnknownProvider()
    {
        using SqliteDbContextFactory factory = new();
        AccountLinkService service = NewService(factory, Substitute.For<ISecurityTokenHandler>(), modrinthEnabled: false);

        Assert.False(service.IsLinkable(AccountLinkService.Modrinth));
        Assert.False(service.IsLinkable("steam"));
    }

    [Fact]
    public async Task CompleteLinkAsync_UpsertsLink_FromVerifiedProfile()
    {
        using SqliteDbContextFactory factory = new();
        Guid userId = await SeedUserAsync(factory, "Owner__1");

        ISecurityTokenHandler tokenHandler = Substitute.For<ISecurityTokenHandler>();
        tokenHandler.VerifyModrinthAuthentication(Arg.Any<string>(), Arg.Any<string>(), Arg.Any<string>(), Arg.Any<string>())
            .Returns(new VerificationResult { Success = true, UserName = "ModName", UserId = "mod-external-9" });

        AccountLinkService service = NewService(factory, tokenHandler);

        UserExternalLink? link = await service.CompleteLinkAsync(AccountLinkService.Modrinth, "code", "https://host/cb", userId);

        Assert.NotNull(link);
        Assert.Equal("mod-external-9", link!.ExternalId);
        Assert.Equal("ModName", link.Handle);

        IReadOnlyList<UserExternalLink> links = await service.GetLinksAsync(userId);
        UserExternalLink stored = Assert.Single(links);
        Assert.Equal(AccountLinkService.Modrinth, stored.Provider);
    }

    [Fact]
    public async Task UpsertLinkAsync_RefreshesExistingLink_WithoutDuplicating()
    {
        using SqliteDbContextFactory factory = new();
        Guid userId = await SeedUserAsync(factory, "Owner__1");
        AccountLinkService service = NewService(factory, Substitute.For<ISecurityTokenHandler>());

        await service.UpsertLinkAsync(userId, AccountLinkService.Discord, "d1", "OldHandle");
        await service.UpsertLinkAsync(userId, AccountLinkService.Discord, "d1", "NewHandle");

        UserExternalLink link = Assert.Single(await service.GetLinksAsync(userId));
        Assert.Equal("NewHandle", link.Handle);
    }

    private static async Task<Guid> SeedUserAsync(SqliteDbContextFactory factory, string identifier)
    {
        await using EunomiaDbContext db = factory.CreateDbContext();
        User user = new() { Identifier = identifier, DisplayName = identifier };
        db.Users.Add(user);
        await db.SaveChangesAsync();
        return user.Id;
    }

    private static AccountLinkService NewService(
        SqliteDbContextFactory factory,
        ISecurityTokenHandler tokenHandler,
        bool modrinthEnabled = true)
    {
        Dictionary<string, string?> values = new()
        {
            ["Eunomia:ModrinthOAuth:Enabled"] = modrinthEnabled ? "true" : "false",
            ["Eunomia:ModrinthOAuth:ClientId"] = "mr-id",
            ["Eunomia:ModrinthOAuth:ClientSecret"] = "mr-secret",
            ["Eunomia:DiscordOAuth:Enabled"] = "true",
            ["Eunomia:DiscordOAuth:ClientId"] = "dc-id",
            ["Eunomia:DiscordOAuth:ClientSecret"] = "dc-secret",
        };
        EunomiaAuthSettings settings = new(new ConfigurationBuilder().AddInMemoryCollection(values).Build());
        return new AccountLinkService(settings, tokenHandler, factory);
    }
}
