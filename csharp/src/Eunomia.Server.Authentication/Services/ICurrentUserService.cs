// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using Eunomia.Server.Data.Entities;

namespace Eunomia.Server.Authentication.Services;

/// <summary>
/// Resolves (and lazily provisions) the signed-in <see cref="User"/> row for the current request/circuit.
/// </summary>
public interface ICurrentUserService
{
    /// <summary>Resolves the signed-in user from the cookie/JWT principal, throwing if unauthenticated.</summary>
    Task<User> GetCurrentUserAsync();

    /// <summary>
    /// Gets or creates the <see cref="User"/> for an explicit <c>{name}__{providerId}</c> identifier,
    /// promoting it to <see cref="IdentityRole.Admin"/> when configured as an admin. Used at sign-in
    /// time (before the cookie exists) to stamp the role claim.
    /// </summary>
    Task<User> EnsureUserAsync(string identifier);

    /// <summary>
    /// Drops the per-scope cached user so the next <see cref="GetCurrentUserAsync"/> re-reads from the
    /// database. Call after mutating the current user's own row so a follow-up read sees the new values.
    /// </summary>
    void InvalidateCache();
}
