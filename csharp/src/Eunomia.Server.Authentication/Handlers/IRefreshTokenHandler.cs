// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Security.Claims;
using Eunomia.Server.Data.Entities;

namespace Eunomia.Server.Authentication.Handlers;

/// <summary>Issues, validates, and rotates the single-use refresh tokens paired with each JWT.</summary>
public interface IRefreshTokenHandler
{
    Task<RefreshToken> GenerateRefreshTokenAsync(string jwtSecret, string jwtIssuer, ClaimsIdentity? claimsIdentity, TimeSpan lifetime);

    Task<ClaimsIdentity?> ValidateRefreshTokenAsync(string refreshToken);

    Task<bool> ValidateRefreshTokenExpiryAsync(string refreshToken);

    Task InvalidateRefreshTokenAsync(string refreshToken);
}
