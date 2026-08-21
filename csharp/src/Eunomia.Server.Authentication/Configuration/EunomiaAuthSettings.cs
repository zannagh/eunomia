// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Security.Cryptography;
using Microsoft.Extensions.Configuration;

namespace Eunomia.Server.Authentication.Configuration;

/// <summary>
/// Authentication settings bound from the <c>Eunomia</c> configuration section, each key falling back
/// to a double-underscore environment variable (e.g. <c>JWT__KEY</c>, <c>GITHUB__CLIENTID</c>) to match
/// the docker-compose env layout. No real secrets are committed: placeholders only, and the JWT key is
/// generated at startup when neither config nor env supply one.
/// </summary>
public class EunomiaAuthSettings
{
    public string JwtKey { get; private set; } = string.Empty;

    public TimeSpan JwtTokenLifetime { get; private set; } = TimeSpan.FromHours(1);

    public AuthServerSettings Server { get; private set; } = new();

    public OAuthProviderSettings GitHubOAuth { get; private set; } = new();

    public OAuthProviderSettings GoogleOAuth { get; private set; } = new();

    public OAuthProviderSettings MicrosoftOAuth { get; private set; } = new();

    public OAuthProviderSettings ModrinthOAuth { get; private set; } = new();

    /// <summary>Discord is link-only (never a login provider); still bound so the Profile page can offer it.</summary>
    public OAuthProviderSettings DiscordOAuth { get; private set; } = new();

    /// <summary>Login identifiers (<c>{name}__{providerId}</c>) promoted to Admin on sign-in.</summary>
    public List<string> AdminIdentifiers { get; private set; } = [];

    public EunomiaAuthSettings(IConfiguration configuration)
    {
        IConfigurationSection section = configuration.GetSection("Eunomia");

        string? jwtKey = section["JwtKey"];
        if (string.IsNullOrEmpty(jwtKey))
        {
            jwtKey = Environment.GetEnvironmentVariable("JWT__KEY");
        }

        JwtKey = string.IsNullOrEmpty(jwtKey) ? GenerateRandomKey() : jwtKey;

        if (TimeSpan.TryParse(
                section["JwtTokenLifetime"] ?? Environment.GetEnvironmentVariable("SECURITY__TOKENLIFETIME"),
                out TimeSpan lifetime))
        {
            JwtTokenLifetime = lifetime;
        }

        Server = new AuthServerSettings
        {
            Url = section["Server:Url"]
                  ?? Environment.GetEnvironmentVariable("SERVER__URL")
                  ?? "https://localhost:5001",
            Port = int.TryParse(
                    section["Server:Port"] ?? Environment.GetEnvironmentVariable("SERVER__PORT"),
                    out int port)
                ? port
                : 5001,
        };

        GitHubOAuth = BindOAuthProvider(section, "GitHub", "https://github.com/login/oauth/authorize");
        GoogleOAuth = BindOAuthProvider(section, "Google", "https://accounts.google.com/o/oauth2/v2/auth");
        MicrosoftOAuth = BindOAuthProvider(section, "Microsoft", "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize");
        ModrinthOAuth = BindOAuthProvider(section, "Modrinth", "https://modrinth.com/auth/authorize");
        DiscordOAuth = BindOAuthProvider(section, "Discord", "https://discord.com/oauth2/authorize");

        List<string>? admins = section.GetSection("AdminIdentifiers").Get<List<string>>();
        if (admins is not null)
        {
            AdminIdentifiers = admins;
        }
    }

    private static OAuthProviderSettings BindOAuthProvider(IConfigurationSection section, string name, string defaultUrl)
    {
        string prefix = $"{name}OAuth";
        string envPrefix = name.ToUpperInvariant();

        return new OAuthProviderSettings
        {
            Enabled = bool.TryParse(
                          section[$"{prefix}:Enabled"] ?? Environment.GetEnvironmentVariable($"{envPrefix}__ENABLED"),
                          out bool enabled) && enabled,
            ClientId = section[$"{prefix}:ClientId"]
                       ?? Environment.GetEnvironmentVariable($"{envPrefix}__CLIENTID")
                       ?? string.Empty,
            ClientSecret = section[$"{prefix}:ClientSecret"]
                           ?? Environment.GetEnvironmentVariable($"{envPrefix}__CLIENTSECRET")
                           ?? string.Empty,
            OAuthUrl = section[$"{prefix}:OAuthUrl"]
                       ?? Environment.GetEnvironmentVariable($"{envPrefix}__OAUTHURL")
                       ?? defaultUrl,
        };
    }

    private static string GenerateRandomKey()
    {
        byte[] bytes = new byte[32];
        RandomNumberGenerator.Fill(bytes);
        return Convert.ToHexStringLower(bytes);
    }
}
