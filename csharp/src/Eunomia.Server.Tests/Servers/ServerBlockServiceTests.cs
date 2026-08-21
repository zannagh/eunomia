// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using Eunomia.Server.Core.Communication;
using Eunomia.Server.Data;
using Eunomia.Server.Data.Entities;
using Eunomia.Server.Data.Servers;
using Eunomia.Server.Tests.TestSupport;
using Microsoft.Extensions.Logging.Abstractions;

namespace Eunomia.Server.Tests.Servers;

/// <summary>
/// Exercises the cached blocked-scope set against an in-process SQLite database: block/unblock keeps the
/// in-memory cache and the persisted flag in lockstep, and the startup load rehydrates the cache.
/// </summary>
public class ServerBlockServiceTests : IDisposable
{
    private const string Scope = "mc.block-tests:25565";

    private readonly SqliteDbContextFactory _factory = new();

    public void Dispose()
    {
        _factory.Dispose();
    }

    [Fact]
    public async Task BlockAsync_MarksScopeBlocked_AndPersistsReason()
    {
        ServerBlockService service = NewService();

        Assert.False(service.IsBlocked(Scope));

        await service.BlockAsync(Scope, "spamming");

        Assert.True(service.IsBlocked(Scope));
        await using EunomiaDbContext context = _factory.CreateDbContext();
        ServerRecord record = Assert.Single(context.Servers);
        Assert.True(record.IsBlocked);
        Assert.Equal("spamming", record.BlockReason);
    }

    [Fact]
    public async Task UnblockAsync_ClearsBlockedStateAndReason()
    {
        ServerBlockService service = NewService();
        await service.BlockAsync(Scope, "spamming");

        await service.UnblockAsync(Scope);

        Assert.False(service.IsBlocked(Scope));
        await using EunomiaDbContext context = _factory.CreateDbContext();
        ServerRecord record = Assert.Single(context.Servers);
        Assert.False(record.IsBlocked);
        Assert.Null(record.BlockReason);
    }

    [Fact]
    public async Task InitializeAsync_LoadsPreviouslyBlockedScopesIntoCache()
    {
        await using (EunomiaDbContext seed = _factory.CreateDbContext())
        {
            seed.Servers.Add(new ServerRecord
            {
                Scope = Scope,
                FirstSeen = DateTime.UtcNow,
                LastSeen = DateTime.UtcNow,
                IsBlocked = true,
            });
            await seed.SaveChangesAsync();
        }

        ServerBlockService service = NewService();
        Assert.False(service.IsBlocked(Scope));

        await service.InitializeAsync();

        Assert.True(service.IsBlocked(Scope));
    }

    private ServerBlockService NewService()
    {
        return new ServerBlockService(_factory, new ConnectionManager(), NullLogger<ServerBlockService>.Instance);
    }
}
