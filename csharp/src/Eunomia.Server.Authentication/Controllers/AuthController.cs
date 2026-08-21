// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Security.Claims;
using Eunomia.Server.Authentication.Configuration;
using Eunomia.Server.Authentication.Handlers;
using Eunomia.Server.Authentication.Helpers;
using Eunomia.Server.Authentication.Providers;
using Eunomia.Server.Authentication.Resources;
using Eunomia.Server.Authentication.Services;
using Eunomia.Server.Data.Entities;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Serilog;

namespace Eunomia.Server.Authentication.Controllers;

/// <summary>
/// The server's own OAuth token surface: it maps a provider callback code to the issuing provider, then
/// exchanges it and mints a first-party JWT (+ refresh token). Also honors <c>grant_type=refresh</c>.
/// </summary>
public class AuthController : ControllerBase
{
    private readonly EunomiaAuthSettings settings;
    private readonly ISecurityTokenHandler tokenHandler;
    private readonly IRefreshTokenHandler refreshTokenHandler;
    private readonly RedirectUriProvider redirectUriProvider;
    private readonly CodeBasedAuthProvider codeBasedAuthProvider;
    private readonly ICurrentUserService currentUserService;
    private readonly IAccountLinkService accountLinkService;

    public AuthController(
        EunomiaAuthSettings settings,
        ISecurityTokenHandler tokenHandler,
        IRefreshTokenHandler refreshTokenHandler,
        RedirectUriProvider redirectUriProvider,
        CodeBasedAuthProvider codeBasedAuthProvider,
        ICurrentUserService currentUserService,
        IAccountLinkService accountLinkService)
    {
        this.settings = settings;
        this.tokenHandler = tokenHandler;
        this.refreshTokenHandler = refreshTokenHandler;
        this.redirectUriProvider = redirectUriProvider;
        this.codeBasedAuthProvider = codeBasedAuthProvider;
        this.currentUserService = currentUserService;
        this.accountLinkService = accountLinkService;
    }

    private string BaseUrl => $"{Request.Scheme}://{Request.Host}";

    [HttpGet("/oauth-callback")]
    [AllowAnonymous]
    public IActionResult OAuthCallback(
        [FromQuery(Name = "state")] string state,
        [FromQuery(Name = "code")] string code)
    {
        if (string.IsNullOrEmpty(state))
        {
            return BadRequest("Missing state parameter.");
        }

        if (!redirectUriProvider.GetRedirectUri(state, out RedirectSettings redirectUri))
        {
            return BadRequest("Unknown state parameter.");
        }

        if (string.IsNullOrEmpty(code))
        {
            return BadRequest("Missing code parameter.");
        }

        codeBasedAuthProvider.AddCodeIdentityProvider(code, redirectUri.Provider);
        return Redirect($"/account/callback?state={Uri.EscapeDataString(state)}&code={Uri.EscapeDataString(code)}");
    }

    [HttpPost("/token")]
    [AllowAnonymous]
    [Produces("application/json")]
    public async Task<IActionResult> Token(
        [FromForm] string code,
        [FromForm(Name = "grant_type")] string grantType,
        [FromForm(Name = "refresh_token")] string refreshToken = "")
    {
        if (string.IsNullOrEmpty(settings.JwtKey))
        {
            return StatusCode(500, "Missing JWT Secret");
        }

        if (grantType.Contains("refresh", StringComparison.OrdinalIgnoreCase) && !string.IsNullOrEmpty(refreshToken))
        {
            return await RefreshAsync(refreshToken);
        }

        if (!codeBasedAuthProvider.GetIdentityProviderByCode(code, out string? provider) || string.IsNullOrEmpty(provider))
        {
            return StatusCode(401, "Invalid code");
        }

        VerificationResult result = await VerifyProviderAsync(provider, code);
        if (!result.Success)
        {
            return StatusCode(401);
        }

        if (string.IsNullOrEmpty(result.UserId) || string.IsNullOrEmpty(result.UserName))
        {
            return StatusCode(500, "Invalid user data received from OAuth provider.");
        }

        string identifier = ClaimsHelper.ToUserIdentifier(result.UserName, result.UserId);
        User user = await currentUserService.EnsureUserAsync(identifier);
        if (provider == AccountLinkService.Modrinth)
        {
            await accountLinkService.UpsertLinkAsync(user.Id, AccountLinkService.Modrinth, result.UserId, result.UserName);
        }

        ClaimsIdentity identity = ClaimsHelper.ClaimsIdentityFromUserNameAndId(result.UserName, result.UserId);
        AccessTokenResult token = await tokenHandler.GenerateJwtTokenAsync(
            settings.JwtKey, settings.Server.JwtIssuer, settings.JwtTokenLifetime, identity);
        return Ok(token);
    }

    private async Task<IActionResult> RefreshAsync(string refreshToken)
    {
        if (await refreshTokenHandler.ValidateRefreshTokenAsync(refreshToken) is not { } claimsIdentity)
        {
            return StatusCode(401, "Invalid refresh token");
        }

        try
        {
            await refreshTokenHandler.InvalidateRefreshTokenAsync(refreshToken);
        }
        catch (Exception ex)
        {
            Log.Error(ex, "Failed to invalidate refresh token");
        }

        return Ok(await tokenHandler.GenerateJwtTokenAsync(
            settings.JwtKey, settings.Server.JwtIssuer, settings.JwtTokenLifetime, claimsIdentity));
    }

    private Task<VerificationResult> VerifyProviderAsync(string provider, string code)
    {
        string redirectUri = $"{BaseUrl}/oauth-callback";
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
