// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Security.Claims;
using Eunomia.Server.Authentication.Resources;

namespace Eunomia.Server.Authentication.Handlers;

/// <summary>
/// Exchanges provider authorization codes for user profiles and mints the server's own JWTs. Modrinth
/// and Discord double as account-link providers; the others are login-only.
/// </summary>
public interface ISecurityTokenHandler
{
    Task<VerificationResult> VerifyMicrosoftAuthentication(string clientId, string clientSecret, string code, string redirectUri, string grantType = "", string codeVerifier = "");

    Task<VerificationResult> VerifyGoogleAuthentication(string clientId, string clientSecret, string code, string redirectUri, string grantType = "", string codeVerifier = "");

    Task<VerificationResult> VerifyGitHubAuthentication(string clientId, string clientSecret, string code, string grantType = "", string codeVerifier = "");

    Task<VerificationResult> VerifyModrinthAuthentication(string clientId, string clientSecret, string code, string redirectUri);

    Task<VerificationResult> VerifyDiscordAuthentication(string clientId, string clientSecret, string code, string redirectUri);

    Task<AccessTokenResult> GenerateJwtTokenAsync(string jwtSecret, string jwtIssuer, TimeSpan lifetime, ClaimsIdentity? claimsIdentity);
}
