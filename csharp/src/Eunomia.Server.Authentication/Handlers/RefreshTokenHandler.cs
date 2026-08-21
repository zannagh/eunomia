// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text;
using Eunomia.Server.Authentication.Helpers;
using Eunomia.Server.Data;
using Eunomia.Server.Data.Entities;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;

namespace Eunomia.Server.Authentication.Handlers;

/// <summary>
/// Persists refresh tokens in the Eunomia database, one row per issued JWT. Tokens are single-use:
/// consumed on rotation and swept once expired or spent.
/// </summary>
public class RefreshTokenHandler : IRefreshTokenHandler
{
    private readonly IDbContextFactory<EunomiaDbContext> dbContextFactory;

    public RefreshTokenHandler(IDbContextFactory<EunomiaDbContext> dbContextFactory)
    {
        this.dbContextFactory = dbContextFactory;
    }

    public async Task<RefreshToken> GenerateRefreshTokenAsync(string jwtSecret, string jwtIssuer, ClaimsIdentity? claimsIdentity, TimeSpan lifetime)
    {
        await CleanExpiredAndConsumedTokensAsync();

        JwtSecurityTokenHandler tokenHandler = new();
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
        string returnString = tokenHandler.WriteToken(jwt) ?? Guid.NewGuid().ToString("N");

        RefreshToken refreshTokenData = new()
        {
            Id = Guid.NewGuid(),
            UserId = claimsIdentity?.ToUserId() ?? string.Empty,
            UserName = claimsIdentity?.ToUserName() ?? string.Empty,
            ExpiresAt = DateTime.UtcNow.Add(lifetime),
            Token = returnString,
            IsConsumed = false,
        };

        await using EunomiaDbContext dbContext = await dbContextFactory.CreateDbContextAsync();
        await dbContext.RefreshTokens.AddAsync(refreshTokenData);
        await dbContext.SaveChangesAsync();

        return refreshTokenData;
    }

    public async Task<ClaimsIdentity?> ValidateRefreshTokenAsync(string refreshToken)
    {
        if (string.IsNullOrEmpty(refreshToken))
        {
            return null;
        }

        await using EunomiaDbContext dbContext = await dbContextFactory.CreateDbContextAsync();
        RefreshToken? token = await dbContext.RefreshTokens.FirstOrDefaultAsync(t => t.Token == refreshToken);

        // A row existing is not enough: a consumed token must not be replayable in the window before the
        // sweep deletes it, and an expired one must not outlive its lifetime just because nothing has
        // generated a new token (and therefore run the sweep) since.
        if (token is null || token.IsConsumed || token.IsExpired)
        {
            return null;
        }

        ClaimsIdentity claimsIdentity = new();
        claimsIdentity.AddClaim(new Claim(ClaimTypes.NameIdentifier, token.UserId));
        claimsIdentity.AddClaim(new Claim(ClaimTypes.Name, token.UserName));
        return claimsIdentity;
    }

    public async Task<bool> ValidateRefreshTokenExpiryAsync(string refreshToken)
    {
        await using EunomiaDbContext dbContext = await dbContextFactory.CreateDbContextAsync();
        RefreshToken? token = await dbContext.RefreshTokens.FirstOrDefaultAsync(t => t.Token == refreshToken);
        return token is { IsExpired: false, IsConsumed: false };
    }

    public async Task<bool> InvalidateRefreshTokenAsync(string refreshToken)
    {
        await using EunomiaDbContext dbContext = await dbContextFactory.CreateDbContextAsync();
        RefreshToken? token = await dbContext.RefreshTokens.FirstOrDefaultAsync(t => t.Token == refreshToken);
        if (token is null || token.IsConsumed)
        {
            return false;
        }

        token.IsConsumed = true;
        await dbContext.SaveChangesAsync();
        return true;
    }

    private async Task CleanExpiredAndConsumedTokensAsync()
    {
        await using EunomiaDbContext dbContext = await dbContextFactory.CreateDbContextAsync();
        List<RefreshToken> invalidTokens = await dbContext.RefreshTokens
            .Where(t => t.IsConsumed || t.ExpiresAt < DateTime.UtcNow)
            .ToListAsync();

        dbContext.RefreshTokens.RemoveRange(invalidTokens);
        await dbContext.SaveChangesAsync();
    }
}
