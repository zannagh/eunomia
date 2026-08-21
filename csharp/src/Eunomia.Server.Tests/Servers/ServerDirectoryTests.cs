// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using Eunomia.Server.Core.Clients;
using Eunomia.Server.Core.Communication;
using Eunomia.Server.Core.Servers;
using Eunomia.Server.Data;
using Eunomia.Server.Data.Entities;
using Eunomia.Server.Data.Servers;
using Eunomia.Server.Tests.TestSupport;

namespace Eunomia.Server.Tests.Servers;

/// <summary>
/// Exercises the directory's presence upsert and dashboard aggregation against SQLite: presence never
/// touches UpdateCount, and list/detail fold the live connection count in from the connection manager.
/// </summary>
public class ServerDirectoryTests : IDisposable
{
    private const string Scope = "mc.directory-tests:25565";

    private readonly SqliteDbContextFactory _factory = new();
    private readonly ConnectionManager _connectionManager = new();

    public void Dispose()
    {
        _factory.Dispose();
    }

    [Fact]
    public async Task TouchPresenceAsync_CreatesRecordWithName_AndDoesNotTouchUpdateCount()
    {
        ServerDirectory directory = NewDirectory();

        await directory.TouchPresenceAsync(Scope, "Hypixel");
        await directory.TouchPresenceAsync(Scope, "Hypixel");

        await using EunomiaDbContext context = _factory.CreateDbContext();
        ServerRecord record = Assert.Single(context.Servers);
        Assert.Equal("Hypixel", record.Name);
        Assert.Equal(0, record.UpdateCount);
    }

    [Fact]
    public async Task ListAsync_FoldsInLiveUserCountAndPreservesUpdateCount()
    {
        await SeedRecord(updateCount: 7);
        Guid clientId = Guid.NewGuid();
        _connectionManager.OnConnectionAdded(new EunomiaClient(clientId) { Scope = Scope });
        ServerDirectory directory = NewDirectory();

        IReadOnlyList<ServerSummary> summaries = await directory.ListAsync();

        ServerSummary summary = Assert.Single(summaries);
        Assert.Equal(Scope, summary.Scope);
        Assert.Equal(1, summary.LiveUserCount);
        Assert.True(summary.Online);
        Assert.Equal(7, summary.UpdateCount);
    }

    [Fact]
    public async Task ListAsync_RecordWithoutLiveConnection_IsMarkedOffline()
    {
        await SeedRecord(updateCount: 3);
        ServerDirectory directory = NewDirectory();

        ServerSummary summary = Assert.Single(await directory.ListAsync());

        Assert.Equal(0, summary.LiveUserCount);
        Assert.False(summary.Online);
    }

    [Fact]
    public async Task GetAsync_ReturnsPerChannelEntryCounts()
    {
        await SeedRecord(updateCount: 2);
        await using (EunomiaDbContext seed = _factory.CreateDbContext())
        {
            seed.KeyedEntries.Add(NewEntry("eunomia:a", "k1"));
            seed.KeyedEntries.Add(NewEntry("eunomia:a", "k2"));
            seed.KeyedEntries.Add(NewEntry("eunomia:b", "k1"));
            await seed.SaveChangesAsync();
        }

        ServerDirectory directory = NewDirectory();
        ServerDetail? detail = await directory.GetAsync(Scope);

        Assert.NotNull(detail);
        Assert.Equal(3, detail!.TotalEntries);
        Assert.Equal(2, Assert.Single(detail.Channels, c => c.Channel == "eunomia:a").EntryCount);
        Assert.Equal(1, Assert.Single(detail.Channels, c => c.Channel == "eunomia:b").EntryCount);
    }

    [Fact]
    public async Task GetAsync_UnknownScopeWithNoConnection_ReturnsNull()
    {
        ServerDirectory directory = NewDirectory();

        Assert.Null(await directory.GetAsync("mc.never-seen:25565"));
    }

    private ServerDirectory NewDirectory()
    {
        return new ServerDirectory(_factory, _connectionManager);
    }

    private async Task SeedRecord(long updateCount)
    {
        await using EunomiaDbContext context = _factory.CreateDbContext();
        context.Servers.Add(new ServerRecord
        {
            Scope = Scope,
            Name = "Seed",
            FirstSeen = DateTime.UtcNow,
            LastSeen = DateTime.UtcNow,
            UpdateCount = updateCount,
        });
        await context.SaveChangesAsync();
    }

    private static KeyedEntry NewEntry(string channel, string key)
    {
        return new KeyedEntry
        {
            Scope = Scope,
            Channel = channel,
            Key = key,
            Payload = "{}",
            UpdatedAt = DateTime.UtcNow,
        };
    }
}
