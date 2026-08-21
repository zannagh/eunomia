// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using Eunomia.Server.Data.Entities;

namespace Eunomia.Server.Authentication.Services;

/// <summary>
/// Admin-only user directory and role management. Listing surfaces every dashboard user with their
/// linked external accounts; <see cref="SetRoleAsync"/> changes a user's role and independently
/// verifies the caller is an <see cref="IdentityRole.Admin"/> as defense in depth beyond the page gate.
/// </summary>
public interface IUserAdminService
{
    /// <summary>Lists every user with their linked external accounts, ordered by display name.</summary>
    Task<IReadOnlyList<UserAdminRecord>> ListUsersAsync(CancellationToken cancellationToken = default);

    /// <summary>
    /// Sets <paramref name="userId"/>'s role to <paramref name="role"/>. Throws
    /// <see cref="UnauthorizedAccessException"/> unless <paramref name="actingAdmin"/> is an Admin, so a
    /// non-admin can never change roles even if it reached this call. Invalidates the current-user cache.
    /// </summary>
    Task SetRoleAsync(
        Guid userId,
        IdentityRole role,
        User actingAdmin,
        CancellationToken cancellationToken = default);
}
