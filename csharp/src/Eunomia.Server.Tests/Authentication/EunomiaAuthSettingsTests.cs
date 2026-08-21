// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using Eunomia.Server.Authentication.Configuration;
using Microsoft.Extensions.Configuration;

namespace Eunomia.Server.Tests.Authentication;

public class EunomiaAuthSettingsTests
{
    [Fact]
    public void BindsProvidersAndAdminsFromConfiguration()
    {
        EunomiaAuthSettings settings = BuildSettings(new Dictionary<string, string?>
        {
            ["Eunomia:JwtKey"] = "config-key",
            ["Eunomia:GitHubOAuth:Enabled"] = "true",
            ["Eunomia:GitHubOAuth:ClientId"] = "gh-id",
            ["Eunomia:GitHubOAuth:ClientSecret"] = "gh-secret",
            ["Eunomia:ModrinthOAuth:Enabled"] = "true",
            ["Eunomia:ModrinthOAuth:ClientId"] = "mr-id",
            ["Eunomia:AdminIdentifiers:0"] = "Alice__42",
            ["Eunomia:AdminIdentifiers:1"] = "Bob__7",
        });

        Assert.Equal("config-key", settings.JwtKey);
        Assert.True(settings.GitHubOAuth.Enabled);
        Assert.Equal("gh-id", settings.GitHubOAuth.ClientId);
        Assert.Equal("gh-secret", settings.GitHubOAuth.ClientSecret);
        Assert.True(settings.ModrinthOAuth.Enabled);
        Assert.False(settings.DiscordOAuth.Enabled);
        Assert.Equal(["Alice__42", "Bob__7"], settings.AdminIdentifiers);
    }

    [Fact]
    public void DefaultsAuthorizeUrlsWhenNotConfigured()
    {
        EunomiaAuthSettings settings = BuildSettings(new Dictionary<string, string?>());

        Assert.Equal("https://modrinth.com/auth/authorize", settings.ModrinthOAuth.OAuthUrl);
        Assert.Equal("https://discord.com/oauth2/authorize", settings.DiscordOAuth.OAuthUrl);
        Assert.Equal("https://github.com/login/oauth/authorize", settings.GitHubOAuth.OAuthUrl);
    }

    [Fact]
    public void GeneratesRandomJwtKeyWhenNeitherConfigNorEnvSupplyOne()
    {
        EunomiaAuthSettings settings = BuildSettings(new Dictionary<string, string?>());

        Assert.False(string.IsNullOrWhiteSpace(settings.JwtKey));
        Assert.Equal(64, settings.JwtKey.Length);
    }

    [Fact]
    public void FallsBackToEnvironmentVariableForClientId()
    {
        Environment.SetEnvironmentVariable("DISCORD__CLIENTID", "env-discord-id");
        try
        {
            EunomiaAuthSettings settings = BuildSettings(new Dictionary<string, string?>());
            Assert.Equal("env-discord-id", settings.DiscordOAuth.ClientId);
        }
        finally
        {
            Environment.SetEnvironmentVariable("DISCORD__CLIENTID", null);
        }
    }

    private static EunomiaAuthSettings BuildSettings(Dictionary<string, string?> values)
    {
        IConfiguration configuration = new ConfigurationBuilder()
            .AddInMemoryCollection(values)
            .Build();
        return new EunomiaAuthSettings(configuration);
    }
}
