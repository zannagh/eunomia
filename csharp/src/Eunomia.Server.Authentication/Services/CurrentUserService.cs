// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Security.Claims;
using Eunomia.Server.Authentication.Configuration;
using Eunomia.Server.Authentication.Helpers;
using Eunomia.Server.Data;
using Eunomia.Server.Data.Entities;
using Microsoft.AspNetCore.Components.Authorization;
using Microsoft.AspNetCore.Http;
using Microsoft.EntityFrameworkCore;

namespace Eunomia.Server.Authentication.Services;

/// <summary>
/// Reads the current principal (Blazor auth state first, then <c>HttpContext</c>), resolves it to a
/// persisted <see cref="User"/>, and auto-promotes configured admin identifiers. The resolved user is
/// cached for the scope's lifetime to avoid re-querying on every render.
/// </summary>
public class CurrentUserService : ICurrentUserService
{
    private readonly IHttpContextAccessor? _accessor;
    private readonly AuthenticationStateProvider? _authenticationStateProvider;
    private readonly IDbContextFactory<EunomiaDbContext> _dbContextFactory;
    private readonly EunomiaAuthSettings _settings;

    private User? _cachedUser;

    public CurrentUserService(
        EunomiaAuthSettings settings,
        IDbContextFactory<EunomiaDbContext> dbContextFactory,
        AuthenticationStateProvider? authenticationStateProvider = null,
        IHttpContextAccessor? accessor = null)
    {
        _accessor = accessor;
        _authenticationStateProvider = authenticationStateProvider;
        _dbContextFactory = dbContextFactory;
        _settings = settings;
    }

    public void InvalidateCache() => _cachedUser = null;

    public async Task<User> GetCurrentUserAsync()
    {
        if (_cachedUser is not null)
        {
            return _cachedUser;
        }

        ClaimsIdentity? claimsIdentity = await TryGetClaimsIdentityFromCookie()
                                         ?? TryGetClaimsIdentityFromHttpContext();

        if (claimsIdentity == null)
        {
            throw new UnauthorizedAccessException();
        }

        string identifier = claimsIdentity.ToUserIdentifier();

        if (string.IsNullOrEmpty(identifier))
        {
            throw new UnauthorizedAccessException();
        }

        _cachedUser = await EnsureUserAsync(identifier);
        return _cachedUser;
    }

    public async Task<User> EnsureUserAsync(string identifier)
    {
        if (string.IsNullOrEmpty(identifier))
        {
            throw new UnauthorizedAccessException();
        }

        await using EunomiaDbContext dbContext = await _dbContextFactory.CreateDbContextAsync();

        User? user = await dbContext.Users.FirstOrDefaultAsync(u => u.Identifier == identifier);
        if (user == null)
        {
            user = new User
            {
                Identifier = identifier,
                DisplayName = identifier.Split("__").FirstOrDefault() ?? identifier,
                Role = IdentityRole.User,
            };
            await dbContext.Users.AddAsync(user);
            await dbContext.SaveChangesAsync();
        }

        if (_settings.AdminIdentifiers.Contains(identifier) && user.Role != IdentityRole.Admin)
        {
            user.Role = IdentityRole.Admin;
            await dbContext.SaveChangesAsync();
        }

        return user;
    }

    private async Task<ClaimsIdentity?> TryGetClaimsIdentityFromCookie()
    {
        if (_authenticationStateProvider == null)
        {
            return null;
        }

        AuthenticationState cookieState = await _authenticationStateProvider.GetAuthenticationStateAsync();

        if (cookieState.User is not { Identity.IsAuthenticated: true }
            || cookieState.User.FindFirst(ClaimTypes.NameIdentifier) is not { } nameIdentifier
            || cookieState.User.FindFirst(ClaimTypes.Name) is not { } name)
        {
            return null;
        }

        ClaimsIdentity cookieClaim = new();
        cookieClaim.AddClaim(new Claim(ClaimTypes.NameIdentifier, nameIdentifier.Value));
        cookieClaim.AddClaim(new Claim(ClaimTypes.Name, name.Value));
        return cookieClaim;
    }

    private ClaimsIdentity? TryGetClaimsIdentityFromHttpContext()
    {
        if (_accessor?.HttpContext is not { } httpContext)
        {
            return null;
        }

        if (httpContext.User.Identity is ClaimsIdentity { IsAuthenticated: true } httpClaimsIdentity)
        {
            return httpClaimsIdentity;
        }

        return null;
    }
}
