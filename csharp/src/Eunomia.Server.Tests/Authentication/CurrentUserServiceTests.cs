// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using Eunomia.Server.Authentication.Configuration;
using Eunomia.Server.Authentication.Services;
using Eunomia.Server.Data;
using Eunomia.Server.Data.Entities;
using Eunomia.Server.Tests.TestSupport;
using Microsoft.Extensions.Configuration;

namespace Eunomia.Server.Tests.Authentication;

public class CurrentUserServiceTests
{
    [Fact]
    public async Task EnsureUserAsync_CreatesUnknownUser_AsPlainUser()
    {
        using SqliteDbContextFactory factory = new();
        CurrentUserService service = NewService(factory);

        User user = await service.EnsureUserAsync("Fresh__100");

        Assert.Equal("Fresh__100", user.Identifier);
        Assert.Equal("Fresh", user.DisplayName);
        Assert.Equal(IdentityRole.User, user.Role);
    }

    [Fact]
    public async Task EnsureUserAsync_PromotesConfiguredAdminIdentifier()
    {
        using SqliteDbContextFactory factory = new();
        CurrentUserService service = NewService(factory, "Boss__9");

        User user = await service.EnsureUserAsync("Boss__9");

        Assert.Equal(IdentityRole.Admin, user.Role);
    }

    [Fact]
    public async Task EnsureUserAsync_ReturnsExistingUser_WithoutDuplicating()
    {
        using SqliteDbContextFactory factory = new();
        CurrentUserService service = NewService(factory);

        User first = await service.EnsureUserAsync("Same__1");
        User second = await service.EnsureUserAsync("Same__1");

        Assert.Equal(first.Id, second.Id);

        await using EunomiaDbContext db = factory.CreateDbContext();
        Assert.Single(db.Users, u => u.Identifier == "Same__1");
    }

    [Fact]
    public async Task EnsureUserAsync_DoesNotPromoteNonAdmin()
    {
        using SqliteDbContextFactory factory = new();
        CurrentUserService service = NewService(factory, "Boss__9");

        User user = await service.EnsureUserAsync("Regular__2");

        Assert.Equal(IdentityRole.User, user.Role);
    }

    private static CurrentUserService NewService(SqliteDbContextFactory factory, params string[] adminIdentifiers)
    {
        Dictionary<string, string?> values = new();
        for (int i = 0; i < adminIdentifiers.Length; i++)
        {
            values[$"Eunomia:AdminIdentifiers:{i}"] = adminIdentifiers[i];
        }

        EunomiaAuthSettings settings = new(new ConfigurationBuilder().AddInMemoryCollection(values).Build());
        return new CurrentUserService(settings, factory);
    }
}
