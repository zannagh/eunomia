// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Security.Claims;
using Eunomia.Server.Authentication.Configuration;
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
/// Browser-facing cookie login: kicks off the provider redirect, then on return resolves the code
/// through <see cref="IOAuthLoginService"/> and signs the user into a cookie carrying their role claim.
/// The exchange runs in-process; this deliberately does not call the server's own <c>/token</c> endpoint
/// over HTTP, because that URL would have to be built from the request's (client-controlled) Host header.
/// </summary>
public class AccountController : Controller
{
    private readonly EunomiaAuthSettings settings;
    private readonly RedirectUriProvider redirectUriProvider;
    private readonly IOAuthLoginService oauthLoginService;

    public AccountController(
        EunomiaAuthSettings settings,
        RedirectUriProvider redirectUriProvider,
        IOAuthLoginService oauthLoginService)
    {
        this.settings = settings;
        this.redirectUriProvider = redirectUriProvider;
        this.oauthLoginService = oauthLoginService;
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
    public IActionResult ExternalLogin([FromQuery] string? provider)
    {
        if (string.IsNullOrEmpty(provider))
        {
            return Redirect("/oauth-select");
        }

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
    public async Task<IActionResult> Callback([FromQuery] string? code)
    {
        if (string.IsNullOrEmpty(code))
        {
            return BadRequest("Missing authorization code");
        }

        try
        {
            OAuthLoginResult login = await oauthLoginService.ResolveCodeAsync(code, $"{BaseUrl}/oauth-callback");
            if (login.Status != OAuthLoginStatus.Success || login.User is not { } user)
            {
                Log.Error("[Authentication] Code exchange failed with status {Status}", login.Status);
                return Redirect("/account/login?error=auth_failed");
            }

            return await SignInAsync(user, login.UserName, login.UserId);
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

    private async Task<IActionResult> SignInAsync(User user, string userName, string userId)
    {
        // Role lives on the User row, not the OAuth profile: the resolved user already carries the
        // (possibly admin-promoted) role, so the cookie principal gets a ClaimTypes.Role claim that the
        // StaffOnly/AdminOnly policies read.
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
