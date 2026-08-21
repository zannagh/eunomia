// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Security.Claims;
using Eunomia.Server.Data.Entities;

namespace Eunomia.Server.Authentication.Handlers;

/// <summary>Issues, validates, and rotates the single-use refresh tokens paired with each JWT.</summary>
public interface IRefreshTokenHandler
{
    Task<RefreshToken> GenerateRefreshTokenAsync(string jwtSecret, string jwtIssuer, ClaimsIdentity? claimsIdentity, TimeSpan lifetime);

    /// <summary>
    /// Returns the identity carried by <paramref name="refreshToken"/>, or null when the token is
    /// unknown, already consumed, or past its expiry.
    /// </summary>
    Task<ClaimsIdentity?> ValidateRefreshTokenAsync(string refreshToken);

    /// <summary>Whether the token exists and is still live (neither consumed nor expired).</summary>
    Task<bool> ValidateRefreshTokenExpiryAsync(string refreshToken);

    /// <summary>
    /// Marks the token spent. Returns false when there was no live token to consume, so callers can
    /// refuse to rotate rather than issuing a replacement for a token they could not retire.
    /// </summary>
    Task<bool> InvalidateRefreshTokenAsync(string refreshToken);
}
