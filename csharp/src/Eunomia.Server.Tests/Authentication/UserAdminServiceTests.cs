// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using Eunomia.Server.Authentication.Services;
using Eunomia.Server.Data;
using Eunomia.Server.Data.Entities;
using Eunomia.Server.Tests.TestSupport;
using Microsoft.EntityFrameworkCore;
using NSubstitute;

namespace Eunomia.Server.Tests.Authentication;

public class UserAdminServiceTests
{
    [Fact]
    public async Task SetRoleAsync_AdminCanPromoteToModerator()
    {
        using SqliteDbContextFactory factory = new();
        Guid targetId = await SeedUserAsync(factory, "Target__1", IdentityRole.User);
        ICurrentUserService currentUser = Substitute.For<ICurrentUserService>();
        UserAdminService service = new(factory, currentUser);

        await service.SetRoleAsync(targetId, IdentityRole.Moderator, Admin());

        await using EunomiaDbContext db = factory.CreateDbContext();
        User updated = await db.Users.SingleAsync(u => u.Id == targetId);
        Assert.Equal(IdentityRole.Moderator, updated.Role);
        currentUser.Received(1).InvalidateCache();
    }

    [Fact]
    public async Task SetRoleAsync_AdminCanPromoteToAdmin()
    {
        using SqliteDbContextFactory factory = new();
        Guid targetId = await SeedUserAsync(factory, "Target__2", IdentityRole.Moderator);
        UserAdminService service = new(factory, Substitute.For<ICurrentUserService>());

        await service.SetRoleAsync(targetId, IdentityRole.Admin, Admin());

        await using EunomiaDbContext db = factory.CreateDbContext();
        Assert.Equal(IdentityRole.Admin, (await db.Users.SingleAsync(u => u.Id == targetId)).Role);
    }

    [Fact]
    public async Task SetRoleAsync_NonAdminIsRefused_AndDoesNotChangeRole()
    {
        using SqliteDbContextFactory factory = new();
        Guid targetId = await SeedUserAsync(factory, "Target__3", IdentityRole.User);
        ICurrentUserService currentUser = Substitute.For<ICurrentUserService>();
        UserAdminService service = new(factory, currentUser);

        await Assert.ThrowsAsync<UnauthorizedAccessException>(() =>
            service.SetRoleAsync(targetId, IdentityRole.Admin, Moderator()));

        await using EunomiaDbContext db = factory.CreateDbContext();
        Assert.Equal(IdentityRole.User, (await db.Users.SingleAsync(u => u.Id == targetId)).Role);
        currentUser.DidNotReceive().InvalidateCache();
    }

    [Fact]
    public async Task ListUsersAsync_ReturnsUsersWithLinks()
    {
        using SqliteDbContextFactory factory = new();
        Guid userId = await SeedUserAsync(factory, "Linked__4", IdentityRole.User);
        await using (EunomiaDbContext seed = factory.CreateDbContext())
        {
            seed.UserExternalLinks.Add(new UserExternalLink
            {
                UserId = userId,
                Provider = "modrinth",
                ExternalId = "mr-99",
                Handle = "steve",
            });
            await seed.SaveChangesAsync();
        }

        UserAdminService service = new(factory, Substitute.For<ICurrentUserService>());
        IReadOnlyList<UserAdminRecord> users = await service.ListUsersAsync();

        UserAdminRecord record = Assert.Single(users, u => u.Id == userId);
        UserAdminLink link = Assert.Single(record.Links);
        Assert.Equal("modrinth", link.Provider);
        Assert.Equal("steve", link.Handle);
    }

    private static User Admin() => new() { Identifier = "Admin__0", Role = IdentityRole.Admin };

    private static User Moderator() => new() { Identifier = "Mod__0", Role = IdentityRole.Moderator };

    private static async Task<Guid> SeedUserAsync(
        SqliteDbContextFactory factory,
        string identifier,
        IdentityRole role)
    {
        await using EunomiaDbContext db = factory.CreateDbContext();
        User user = new()
        {
            Identifier = identifier,
            DisplayName = identifier.Split("__").First(),
            Role = role,
        };
        db.Users.Add(user);
        await db.SaveChangesAsync();
        return user.Id;
    }
}
