// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Net.Http.Headers;
using System.Security.Claims;
using System.Text;
using System.Text.Json;
using Eunomia.Server.Authentication.Resources;
using Eunomia.Server.Data.Entities;
using Microsoft.IdentityModel.Tokens;
using Serilog;
using TokenHandler = System.IdentityModel.Tokens.Jwt.JwtSecurityTokenHandler;

namespace Eunomia.Server.Authentication.Handlers;

/// <summary>
/// Performs the provider-specific OAuth code exchanges (GitHub, Google, Microsoft, Modrinth, Discord)
/// and mints the server's HS256 JWTs. HTTP goes through the injected <see cref="IHttpClientFactory"/>
/// so the exchanges are unit-testable against a stub handler.
/// </summary>
public class JwtTokenHandler : ISecurityTokenHandler
{
    private const string UserAgent = "eunomia-auth";

    private readonly IHttpClientFactory httpClientFactory;
    private readonly IRefreshTokenHandler refreshTokenHandler;

    public JwtTokenHandler(IHttpClientFactory httpClientFactory, IRefreshTokenHandler refreshTokenHandler)
    {
        this.httpClientFactory = httpClientFactory;
        this.refreshTokenHandler = refreshTokenHandler;
    }

    public async Task<VerificationResult> VerifyMicrosoftAuthentication(string clientId, string clientSecret,
        string code, string redirectUri, string grantType = "", string codeVerifier = "")
    {
        Dictionary<string, string> parameters = BuildTokenForm(clientId, clientSecret, code, redirectUri, grantType, codeVerifier);
        string? accessToken = await ExchangeCodeAsync("https://login.microsoftonline.com/consumers/oauth2/v2.0/token", parameters);
        using JsonDocument? user = await FetchUserAsync("https://graph.microsoft.com/v1.0/me", accessToken, useBearer: true);
        return ReadProfile(user, idProperty: "id", nameProperty: "displayName");
    }

    public async Task<VerificationResult> VerifyGoogleAuthentication(string clientId, string clientSecret,
        string code, string redirectUri, string grantType = "", string codeVerifier = "")
    {
        Dictionary<string, string> parameters = BuildTokenForm(clientId, clientSecret, code, redirectUri, grantType, codeVerifier);
        string? accessToken = await ExchangeCodeAsync("https://oauth2.googleapis.com/token", parameters);
        using JsonDocument? user = await FetchUserAsync("https://www.googleapis.com/oauth2/v1/userinfo?alt=json", accessToken, useBearer: true);
        return ReadProfile(user, idProperty: "id", nameProperty: "name");
    }

    public async Task<VerificationResult> VerifyGitHubAuthentication(string clientId, string clientSecret,
        string code, string grantType = "", string codeVerifier = "")
    {
        Dictionary<string, string> parameters = new()
        {
            { "client_id", clientId },
            { "client_secret", clientSecret },
            { "code", code },
        };
        AddOptional(parameters, grantType, codeVerifier);

        string? accessToken = await ExchangeCodeAsync("https://github.com/login/oauth/access_token", parameters);
        using JsonDocument? user = await FetchUserAsync("https://api.github.com/user", accessToken, useBearer: true);
        if (user is null || !user.RootElement.TryGetProperty("login", out JsonElement login) || login.GetString() is not { } githubLogin)
        {
            return Failed();
        }

        return new VerificationResult { Success = true, UserName = githubLogin, UserId = user.RootElement.GetProperty("id").GetInt64().ToString() };
    }

    public async Task<VerificationResult> VerifyModrinthAuthentication(string clientId, string clientSecret, string code, string redirectUri)
    {
        Dictionary<string, string> parameters = new()
        {
            { "code", code },
            { "client_id", clientId },
            { "redirect_uri", redirectUri },
            { "grant_type", "authorization_code" },
        };

        // Modrinth expects the client secret in the Authorization header of the token request, and its
        // API reads bearer tokens as a raw Authorization value (no "Bearer " scheme prefix).
        string? accessToken = await ExchangeCodeAsync("https://api.modrinth.com/_internal/oauth/token", parameters, authorization: clientSecret);
        using JsonDocument? user = await FetchUserAsync("https://api.modrinth.com/v2/user", accessToken, useBearer: false);
        return ReadProfile(user, idProperty: "id", nameProperty: "username");
    }

    public async Task<VerificationResult> VerifyDiscordAuthentication(string clientId, string clientSecret, string code, string redirectUri)
    {
        Dictionary<string, string> parameters = new()
        {
            { "grant_type", "authorization_code" },
            { "code", code },
            { "redirect_uri", redirectUri },
            { "client_id", clientId },
            { "client_secret", clientSecret },
        };

        string? accessToken = await ExchangeCodeAsync("https://discord.com/api/oauth2/token", parameters);
        using JsonDocument? user = await FetchUserAsync("https://discord.com/api/users/@me", accessToken, useBearer: true);
        return ReadProfile(user, idProperty: "id", nameProperty: "username");
    }

    public async Task<AccessTokenResult> GenerateJwtTokenAsync(string jwtSecret, string jwtIssuer,
        TimeSpan lifetime, ClaimsIdentity? claimsIdentity)
    {
        TokenHandler tokenHandler = new();
        byte[] key = Encoding.UTF8.GetBytes(jwtSecret);

        SecurityTokenDescriptor tokenDescriptor = new()
        {
            Subject = claimsIdentity,
            Expires = DateTime.UtcNow.Add(lifetime),
            Issuer = jwtIssuer,
            SigningCredentials = new SigningCredentials(new SymmetricSecurityKey(key), SecurityAlgorithms.HmacSha256Signature),
            IssuedAt = DateTime.UtcNow,
        };

        SecurityToken? jwt = tokenHandler.CreateToken(tokenDescriptor);
        string? jwtString = tokenHandler.WriteToken(jwt);

        RefreshToken refreshToken = await refreshTokenHandler.GenerateRefreshTokenAsync(jwtSecret, jwtIssuer, claimsIdentity, TimeSpan.FromDays(14));

        return new AccessTokenResult
        {
            AccessToken = jwtString,
            RefreshToken = refreshToken.Token,
            TokenType = "Bearer",

            // TotalSeconds, not Seconds: the latter is the 0-59 component, which reports a 1h token as
            // expiring in 59 seconds and sends conforming clients into a refresh loop.
            ExpiresIn = (int)(tokenDescriptor.Expires.Value.ToUniversalTime() - DateTime.UtcNow).TotalSeconds,
        };
    }

    private static Dictionary<string, string> BuildTokenForm(string clientId, string clientSecret,
        string code, string redirectUri, string grantType, string codeVerifier)
    {
        Dictionary<string, string> parameters = new()
        {
            { "client_id", clientId },
            { "client_secret", clientSecret },
            { "code", code },
            { "redirect_uri", redirectUri },
        };
        AddOptional(parameters, grantType, codeVerifier);
        return parameters;
    }

    private static void AddOptional(Dictionary<string, string> parameters, string grantType, string codeVerifier)
    {
        if (!string.IsNullOrEmpty(grantType))
        {
            parameters["grant_type"] = grantType;
        }

        if (!string.IsNullOrEmpty(codeVerifier))
        {
            parameters["code_verifier"] = codeVerifier;
        }
    }

    private async Task<string?> ExchangeCodeAsync(string tokenUrl, Dictionary<string, string> parameters, string? authorization = null)
    {
        using HttpRequestMessage request = new(HttpMethod.Post, tokenUrl)
        {
            Content = new FormUrlEncodedContent(parameters),
        };
        request.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));
        if (!string.IsNullOrEmpty(authorization))
        {
            request.Headers.TryAddWithoutValidation("Authorization", authorization);
        }

        HttpClient client = httpClientFactory.CreateClient(nameof(JwtTokenHandler));
        using HttpResponseMessage response = await client.SendAsync(request);
        using JsonDocument? payload = await ReadJsonAsync(response, tokenUrl);
        if (payload is null)
        {
            return null;
        }

        return payload.RootElement.TryGetProperty("access_token", out JsonElement token) ? token.GetString() : null;
    }

    private async Task<JsonDocument?> FetchUserAsync(string userUrl, string? accessToken, bool useBearer)
    {
        if (string.IsNullOrEmpty(accessToken))
        {
            return null;
        }

        using HttpRequestMessage request = new(HttpMethod.Get, userUrl);
        request.Headers.TryAddWithoutValidation("Authorization", useBearer ? $"Bearer {accessToken}" : accessToken);
        request.Headers.UserAgent.ParseAdd(UserAgent);

        HttpClient client = httpClientFactory.CreateClient(nameof(JwtTokenHandler));
        using HttpResponseMessage response = await client.SendAsync(request);
        return await ReadJsonAsync(response, userUrl);
    }

    /// <summary>
    /// Reads a provider response as JSON, or null when the call failed or the body is not JSON at all.
    /// Providers answer errors and rate limits with HTML or plain text often enough that parsing
    /// unconditionally turns a failed login into an unhandled 500.
    /// </summary>
    private static async Task<JsonDocument?> ReadJsonAsync(HttpResponseMessage response, string url)
    {
        string body = await response.Content.ReadAsStringAsync();
        if (!response.IsSuccessStatusCode)
        {
            Log.Warning("[Authentication] {Url} returned {StatusCode}", url, (int)response.StatusCode);
            return null;
        }

        try
        {
            return JsonDocument.Parse(body);
        }
        catch (JsonException ex)
        {
            Log.Warning(ex, "[Authentication] {Url} returned a non-JSON body", url);
            return null;
        }
    }

    private static VerificationResult ReadProfile(JsonDocument? user, string idProperty, string nameProperty)
    {
        if (user is null
            || !user.RootElement.TryGetProperty(nameProperty, out JsonElement nameElement)
            || nameElement.GetString() is not { } name
            || !user.RootElement.TryGetProperty(idProperty, out JsonElement idElement)
            || idElement.GetString() is not { } id)
        {
            return Failed();
        }

        return new VerificationResult { Success = true, UserName = name, UserId = id };
    }

    private static VerificationResult Failed()
    {
        return new VerificationResult { Success = false, UserName = string.Empty, UserId = string.Empty };
    }
}
