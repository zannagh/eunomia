// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Security.Claims;
using System.Text.Json;
using Eunomia.Server.Authentication.Configuration;
using Eunomia.Server.Authentication.Helpers;
using Eunomia.Server.Authentication.Providers;
using Eunomia.Server.Authentication.Resources;
using Eunomia.Server.Authentication.Services;
using Eunomia.Server.Data.Entities;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Authentication.Cookies;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Serilog;

namespace Eunomia.Server.Authentication.Controllers;

/// <summary>
/// Browser-facing cookie login: kicks off the provider redirect, then on return exchanges the code via
/// the local <c>/token</c> endpoint and signs the user into a cookie carrying their role claim.
/// </summary>
public class AccountController : Controller
{
    private readonly EunomiaAuthSettings settings;
    private readonly RedirectUriProvider redirectUriProvider;
    private readonly IHttpClientFactory httpClientFactory;
    private readonly ICurrentUserService currentUserService;

    public AccountController(
        EunomiaAuthSettings settings,
        RedirectUriProvider redirectUriProvider,
        IHttpClientFactory httpClientFactory,
        ICurrentUserService currentUserService)
    {
        this.settings = settings;
        this.redirectUriProvider = redirectUriProvider;
        this.httpClientFactory = httpClientFactory;
        this.currentUserService = currentUserService;
    }

    private string BaseUrl => $"{Request.Scheme}://{Request.Host}";

    [HttpGet("/account/login")]
    [AllowAnonymous]
    public IActionResult Login(string? error = null)
    {
        return Redirect(string.IsNullOrEmpty(error)
            ? "/oauth-select"
            : $"/oauth-select?error={Uri.EscapeDataString(error)}");
    }

    [HttpGet("/account/external")]
    [AllowAnonymous]
    public IActionResult ExternalLogin([FromQuery] string provider)
    {
        (string AuthUrl, string ClientId)? config = GetProviderAuthConfig(provider);
        if (config is null)
        {
            return Redirect("/oauth-select");
        }

        string state = Guid.NewGuid().ToString();
        redirectUriProvider.AddRedirectUri(state, new RedirectSettings
        {
            Uri = $"{BaseUrl}/account/callback",
            Provider = provider,
        });

        Dictionary<string, string> parameters = new()
        {
            ["client_id"] = config.Value.ClientId,
            ["state"] = state,
            ["redirect_uri"] = $"{BaseUrl}/oauth-callback",
        };

        ApplyProviderScopes(provider, parameters);

        string queryString = string.Join("&", parameters.Select(kvp =>
            $"{Uri.EscapeDataString(kvp.Key)}={Uri.EscapeDataString(kvp.Value)}"));

        return Redirect($"{config.Value.AuthUrl}?{queryString}");
    }

    [HttpGet("/account/callback")]
    [AllowAnonymous]
    public async Task<IActionResult> Callback([FromQuery] string code, [FromQuery] string state)
    {
        if (string.IsNullOrEmpty(code))
        {
            return BadRequest("Missing authorization code");
        }

        try
        {
            FormUrlEncodedContent tokenRequest = new(
            [
                new KeyValuePair<string, string>("grant_type", "authorization_code"),
                new KeyValuePair<string, string>("code", code),
                new KeyValuePair<string, string>("redirect_uri", $"{BaseUrl}/oauth-callback"),
            ]);

            using HttpClient httpClient = httpClientFactory.CreateClient(nameof(AccountController));
            HttpResponseMessage tokenResponse = await httpClient.PostAsync($"{BaseUrl}/token", tokenRequest);

            if (!tokenResponse.IsSuccessStatusCode)
            {
                Log.Error("[Authentication] Token exchange failed with status {StatusCode}", tokenResponse.StatusCode);
                return Redirect("/account/login?error=auth_failed");
            }

            JsonElement tokenData = JsonSerializer.Deserialize<JsonElement>(await tokenResponse.Content.ReadAsStringAsync());
            if (!tokenData.TryGetProperty("access_token", out JsonElement accessTokenElement)
                || accessTokenElement.GetString() is not { Length: > 0 } accessToken)
            {
                return Redirect("/account/login?error=token_missing");
            }

            return await SignInFromAccessTokenAsync(accessToken);
        }
        catch (Exception ex)
        {
            Log.Error("[Authentication] Authentication callback error: {Message}", ex.Message);
            return Redirect("/account/login?error=callback_failed");
        }
    }

    [HttpGet("/account/logout")]
    [AllowAnonymous]
    public async Task<IActionResult> Logout()
    {
        await HttpContext.SignOutAsync(CookieAuthenticationDefaults.AuthenticationScheme);
        return LocalRedirect("/");
    }

    private async Task<IActionResult> SignInFromAccessTokenAsync(string accessToken)
    {
        System.IdentityModel.Tokens.Jwt.JwtSecurityTokenHandler handler = new();
        System.IdentityModel.Tokens.Jwt.JwtSecurityToken jsonToken = handler.ReadJwtToken(accessToken);

        string userName = jsonToken.Claims.FirstOrDefault(x => x.Type == "unique_name")?.Value ?? string.Empty;
        string userId = jsonToken.Claims.FirstOrDefault(x => x.Type == "nameid")?.Value ?? string.Empty;
        string identifier = ClaimsHelper.ToUserIdentifier(userName, userId);

        // Role lives on the User row, not the OAuth token: resolve (and admin-promote) the user now so the
        // cookie principal carries a ClaimTypes.Role claim that the StaffOnly/AdminOnly policies read.
        User user = await currentUserService.EnsureUserAsync(identifier);

        List<Claim> claims =
        [
            new(ClaimTypes.NameIdentifier, userId),
            new(ClaimTypes.Name, userName),
            new("Name", userName),
            new(ClaimTypes.Role, user.Role.ToString()),
        ];

        ClaimsIdentity identity = new(claims, CookieAuthenticationDefaults.AuthenticationScheme);
        await HttpContext.SignInAsync(CookieAuthenticationDefaults.AuthenticationScheme, new ClaimsPrincipal(identity));
        return LocalRedirect("/");
    }

    private void ApplyProviderScopes(string provider, Dictionary<string, string> parameters)
    {
        switch (provider)
        {
            case "google":
                parameters["response_type"] = "code";
                parameters["scope"] = "https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/userinfo.profile";
                break;
            case "microsoft":
                parameters["response_type"] = "code";
                parameters["scope"] = "User.Read";
                break;
            case "modrinth":
                parameters["response_type"] = "code";
                parameters["scope"] = "USER_READ";
                break;
        }
    }

    private (string AuthUrl, string ClientId)? GetProviderAuthConfig(string provider) => provider switch
    {
        "github" when settings.GitHubOAuth.Enabled => (settings.GitHubOAuth.OAuthUrl, settings.GitHubOAuth.ClientId),
        "microsoft" when settings.MicrosoftOAuth.Enabled => (settings.MicrosoftOAuth.OAuthUrl, settings.MicrosoftOAuth.ClientId),
        "google" when settings.GoogleOAuth.Enabled => (settings.GoogleOAuth.OAuthUrl, settings.GoogleOAuth.ClientId),
        "modrinth" when settings.ModrinthOAuth.Enabled => (settings.ModrinthOAuth.OAuthUrl, settings.ModrinthOAuth.ClientId),
        _ => null,
    };
}
