// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Security.Claims;
using Eunomia.Server.Authentication.Configuration;
using Eunomia.Server.Authentication.Handlers;
using Eunomia.Server.Authentication.Helpers;
using Eunomia.Server.Authentication.Providers;
using Eunomia.Server.Authentication.Resources;
using Eunomia.Server.Authentication.Services;
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
    private readonly EunomiaAuthSettings _settings;
    private readonly ISecurityTokenHandler _tokenHandler;
    private readonly IRefreshTokenHandler _refreshTokenHandler;
    private readonly RedirectUriProvider _redirectUriProvider;
    private readonly CodeBasedAuthProvider _codeBasedAuthProvider;
    private readonly IOAuthLoginService _oauthLoginService;

    public AuthController(
        EunomiaAuthSettings settings,
        ISecurityTokenHandler tokenHandler,
        IRefreshTokenHandler refreshTokenHandler,
        RedirectUriProvider redirectUriProvider,
        CodeBasedAuthProvider codeBasedAuthProvider,
        IOAuthLoginService oauthLoginService)
    {
        _settings = settings;
        _tokenHandler = tokenHandler;
        _refreshTokenHandler = refreshTokenHandler;
        _redirectUriProvider = redirectUriProvider;
        _codeBasedAuthProvider = codeBasedAuthProvider;
        _oauthLoginService = oauthLoginService;
    }

    private string BaseUrl => $"{Request.Scheme}://{Request.Host}";

    [HttpGet("/oauth-callback")]
    [AllowAnonymous]
    public IActionResult OAuthCallback(
        [FromQuery(Name = "state")] string? state,
        [FromQuery(Name = "code")] string? code)
    {
        if (string.IsNullOrEmpty(state))
        {
            return BadRequest("Missing state parameter.");
        }

        if (!_redirectUriProvider.GetRedirectUri(state, out RedirectSettings redirectUri))
        {
            return BadRequest("Unknown state parameter.");
        }

        if (string.IsNullOrEmpty(code))
        {
            return BadRequest("Missing code parameter.");
        }

        _codeBasedAuthProvider.AddCodeIdentityProvider(code, redirectUri.Provider);
        return Redirect($"/account/callback?state={Uri.EscapeDataString(state)}&code={Uri.EscapeDataString(code)}");
    }

    [HttpPost("/token")]
    [AllowAnonymous]
    [Produces("application/json")]
    public async Task<IActionResult> Token(
        [FromForm] string? code,
        [FromForm(Name = "grant_type")] string? grantType,
        [FromForm(Name = "refresh_token")] string? refreshToken = "")
    {
        if (string.IsNullOrEmpty(_settings.JwtKey))
        {
            return StatusCode(500, "Missing JWT Secret");
        }

        // No [ApiController] on this type, so model binding never rejects a malformed form for us:
        // treat every missing field as a client error instead of dereferencing null.
        if (!string.IsNullOrEmpty(grantType)
            && grantType.Contains("refresh", StringComparison.OrdinalIgnoreCase)
            && !string.IsNullOrEmpty(refreshToken))
        {
            return await RefreshAsync(refreshToken);
        }

        if (string.IsNullOrEmpty(code))
        {
            return BadRequest("Missing code parameter.");
        }

        OAuthLoginResult login = await _oauthLoginService.ResolveCodeAsync(code, $"{BaseUrl}/oauth-callback");
        switch (login.Status)
        {
            case OAuthLoginStatus.UnknownCode:
                return StatusCode(401, "Invalid code");
            case OAuthLoginStatus.ProviderRejected:
                return StatusCode(401);
            case OAuthLoginStatus.IncompleteProfile:
                return StatusCode(500, "Invalid user data received from OAuth provider.");
        }

        ClaimsIdentity identity = ClaimsHelper.ClaimsIdentityFromUserNameAndId(login.UserName, login.UserId);
        AccessTokenResult token = await _tokenHandler.GenerateJwtTokenAsync(
            _settings.JwtKey, _settings.Server.JwtIssuer, _settings.JwtTokenLifetime, identity);
        return Ok(token);
    }

    private async Task<IActionResult> RefreshAsync(string refreshToken)
    {
        if (await _refreshTokenHandler.ValidateRefreshTokenAsync(refreshToken) is not { } claimsIdentity)
        {
            return StatusCode(401, "Invalid refresh token");
        }

        // Rotation must be atomic with acceptance: if we cannot mark the presented token spent, we must
        // not hand out a replacement, or the same token stays replayable until the next sweep.
        if (!await _refreshTokenHandler.InvalidateRefreshTokenAsync(refreshToken))
        {
            Log.Error("Refused refresh: could not consume presented refresh token");
            return StatusCode(401, "Invalid refresh token");
        }

        return Ok(await _tokenHandler.GenerateJwtTokenAsync(
            _settings.JwtKey, _settings.Server.JwtIssuer, _settings.JwtTokenLifetime, claimsIdentity));
    }
}
