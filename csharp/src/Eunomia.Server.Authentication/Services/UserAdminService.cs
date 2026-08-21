// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using Eunomia.Server.Data;
using Eunomia.Server.Data.Entities;
using Microsoft.EntityFrameworkCore;

namespace Eunomia.Server.Authentication.Services;

/// <summary>
/// EF Core-backed <see cref="IUserAdminService"/>. Reads users with their external links for the admin
/// view and gates role changes behind an Admin caller check (in addition to the AdminOnly page policy).
/// </summary>
public sealed class UserAdminService : IUserAdminService
{
    private readonly IDbContextFactory<EunomiaDbContext> contextFactory;
    private readonly ICurrentUserService currentUserService;

    public UserAdminService(
        IDbContextFactory<EunomiaDbContext> contextFactory,
        ICurrentUserService currentUserService)
    {
        this.contextFactory = contextFactory;
        this.currentUserService = currentUserService;
    }

    public async Task<IReadOnlyList<UserAdminRecord>> ListUsersAsync(CancellationToken cancellationToken = default)
    {
        await using EunomiaDbContext context = await contextFactory.CreateDbContextAsync(cancellationToken);
        List<User> users = await context.Users.AsNoTracking()
            .Include(u => u.ExternalLinks)
            .OrderBy(u => u.DisplayName)
            .ToListAsync(cancellationToken);

        return users.Select(ToRecord).ToList();
    }

    public async Task SetRoleAsync(
        Guid userId,
        IdentityRole role,
        User actingAdmin,
        CancellationToken cancellationToken = default)
    {
        if (actingAdmin.Role != IdentityRole.Admin)
        {
            throw new UnauthorizedAccessException("Only an administrator may change user roles.");
        }

        await using EunomiaDbContext context = await contextFactory.CreateDbContextAsync(cancellationToken);
        User? target = await context.Users.FirstOrDefaultAsync(u => u.Id == userId, cancellationToken);
        if (target is null)
        {
            return;
        }

        target.Role = role;
        await context.SaveChangesAsync(cancellationToken);

        // Drop the cached current user so a follow-up read (e.g. the admin editing their own row) reflects it.
        currentUserService.InvalidateCache();
    }

    private static UserAdminRecord ToRecord(User user)
    {
        IReadOnlyList<UserAdminLink> links = user.ExternalLinks
            .Select(l => new UserAdminLink(l.Provider, l.Handle, l.ExternalId))
            .ToList();

        return new UserAdminRecord(user.Id, user.Identifier, user.DisplayName, user.Role, links);
    }
}
