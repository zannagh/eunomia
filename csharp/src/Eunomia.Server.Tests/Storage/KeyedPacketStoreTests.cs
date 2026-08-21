// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Text.Json;
using Eunomia.Server.Core.Storage;
using Eunomia.Server.Data.Storage;
using Eunomia.Server.Tests.TestSupport;

namespace Eunomia.Server.Tests.Storage;

public class PgKeyedPacketStoreTests : IDisposable
{
    private readonly SqliteDbContextFactory _factory = new();

    public void Dispose()
    {
        _factory.Dispose();
    }

    [Fact]
    public void Put_IsolatesEntriesByScope()
    {
        PgKeyedPacketStore store = new(_factory);
        JsonElement payload = ParsePayload("""{"value":1}""");

        store.Put("scope-a", "ns:channel", "key1", payload);
        store.Put("scope-b", "ns:channel", "key1", payload);

        IReadOnlyList<StoreSyncPayload> scopeA = store.SnapshotFor("scope-a");
        IReadOnlyList<StoreSyncPayload> scopeB = store.SnapshotFor("scope-b");

        Assert.Single(scopeA);
        Assert.Single(scopeB);
        Assert.True(scopeA[0].Entries.ContainsKey("key1"));
        Assert.True(scopeB[0].Entries.ContainsKey("key1"));
    }

    [Fact]
    public void SnapshotFor_UnknownScope_ReturnsEmpty()
    {
        PgKeyedPacketStore store = new(_factory);

        IReadOnlyList<StoreSyncPayload> snapshot = store.SnapshotFor("does-not-exist");

        Assert.Empty(snapshot);
    }

    [Fact]
    public void SnapshotFor_ReturnsOnePayloadPerChannel_WithRawJsonEntries()
    {
        PgKeyedPacketStore store = new(_factory);
        JsonElement payload = ParsePayload("""{"value":42}""");

        store.Put("mc.hypixel.net:25565", "eunomia:one", "keyA", payload);
        store.Put("mc.hypixel.net:25565", "eunomia:two", "keyB", payload);

        IReadOnlyList<StoreSyncPayload> snapshot = store.SnapshotFor("mc.hypixel.net:25565");

        Assert.Equal(2, snapshot.Count);
        StoreSyncPayload channelOne = Assert.Single(snapshot, s => s.Channel == "eunomia:one");
        Assert.Equal("""{"value":42}""", channelOne.Entries["keyA"]);
    }

    [Fact]
    public void Put_SameScopeChannelKey_OverwritesPreviousValue()
    {
        PgKeyedPacketStore store = new(_factory);

        store.Put("scope", "ns:chan", "key1", ParsePayload("""{"value":1}"""));
        store.Put("scope", "ns:chan", "key1", ParsePayload("""{"value":2}"""));

        StoreSyncPayload payload = Assert.Single(store.SnapshotFor("scope"));
        Assert.Equal("""{"value":2}""", payload.Entries["key1"]);
    }

    [Fact]
    public void PersistedEntries_SurviveAcrossStoreInstances()
    {
        // A fresh store over the same database must see rows written by an earlier instance - the
        // direct analog of the old "reload from disk" check, now proving DB persistence.
        const string scope = "mc.hypixel.net:25565";
        JsonElement payload = ParsePayload("""{"restored":true}""");

        PgKeyedPacketStore original = new(_factory);
        original.Put(scope, "eunomia:profile", "player1", payload);

        PgKeyedPacketStore reloaded = new(_factory);
        StoreSyncPayload snapshot = Assert.Single(reloaded.SnapshotFor(scope));

        Assert.Equal("eunomia:profile", snapshot.Channel);
        Assert.Equal("""{"restored":true}""", snapshot.Entries["player1"]);
    }

    [Fact]
    public async Task Put_ConcurrentWritesAcrossManyKeys_PersistsAllWithoutCorruption()
    {
        const string scope = "stress-scope";
        const string channel = "eunomia:stress";
        const int writerCount = 64;

        PgKeyedPacketStore store = new(_factory);

        await Task.WhenAll(Enumerable.Range(0, writerCount).Select(i => Task.Run(() =>
        {
            store.Put(scope, channel, $"key-{i}", ParsePayload($$"""{"index":{{i}}}"""));
        })));

        // A fresh instance reading the database back proves every write committed intact.
        PgKeyedPacketStore reloaded = new(_factory);
        StoreSyncPayload snapshot = Assert.Single(reloaded.SnapshotFor(scope));

        Assert.Equal(writerCount, snapshot.Entries.Count);
        for (int i = 0; i < writerCount; i++)
        {
            Assert.Equal($$"""{"index":{{i}}}""", snapshot.Entries[$"key-{i}"]);
        }
    }

    [Fact]
    public async Task Put_ConcurrentWritesToSameKey_ReloadsWithoutCorruption()
    {
        const string scope = "race-scope";
        const string channel = "eunomia:race";
        const string key = "shared-key";
        const int writerCount = 64;

        PgKeyedPacketStore store = new(_factory);

        await Task.WhenAll(Enumerable.Range(0, writerCount).Select(i => Task.Run(() =>
        {
            store.Put(scope, channel, key, ParsePayload($$"""{"writer":{{i}}}"""));
        })));

        PgKeyedPacketStore reloaded = new(_factory);
        StoreSyncPayload snapshot = Assert.Single(reloaded.SnapshotFor(scope));

        // Exactly one entry survives (last writer wins); it must be intact, valid JSON from one of
        // the writers - never a truncated or interleaved fragment from a torn concurrent write.
        string persisted = Assert.Single(snapshot.Entries).Value;
        using JsonDocument document = JsonDocument.Parse(persisted); // throws on corruption
        int writerIndex = document.RootElement.GetProperty("writer").GetInt32();
        Assert.InRange(writerIndex, 0, writerCount - 1);
    }

    [Fact]
    public async Task Put_ConcurrentWritesAcrossScopesAndChannels_AllSurviveReload()
    {
        string[] scopes = ["scope-alpha", "scope-beta"];
        string[] channels = ["eunomia:chan-a", "eunomia:chan-b", "eunomia:chan-c"];
        const int taskCount = 8;
        const int putsPerTask = 200;

        PgKeyedPacketStore store = new(_factory);

        // Every (task, i) pair gets a globally unique key, spread pseudo-randomly across every
        // (scope, channel) combination, so we know exactly how many entries each combo must end up
        // with once every task has finished writing.
        var expected = new Dictionary<(string Scope, string Channel), HashSet<string>>();
        foreach (string scope in scopes)
        {
            foreach (string channel in channels)
            {
                expected[(scope, channel)] = [];
            }
        }

        foreach (int t in Enumerable.Range(0, taskCount))
        {
            foreach (int i in Enumerable.Range(0, putsPerTask))
            {
                string scope = scopes[(t + i) % scopes.Length];
                string channel = channels[(t + i) % channels.Length];
                expected[(scope, channel)].Add($"t{t}-i{i}");
            }
        }

        await Task.WhenAll(Enumerable.Range(0, taskCount).Select(t => Task.Run(() =>
        {
            for (int i = 0; i < putsPerTask; i++)
            {
                string scope = scopes[(t + i) % scopes.Length];
                string channel = channels[(t + i) % channels.Length];
                string key = $"t{t}-i{i}";
                store.Put(scope, channel, key, ParsePayload($$"""{"task":{{t}},"i":{{i}}}"""));
            }
        })));

        AssertMatchesExpected(store, scopes, expected);

        // Fresh instance over the same database proves every row committed and is readable.
        PgKeyedPacketStore reloaded = new(_factory);
        AssertMatchesExpected(reloaded, scopes, expected);
    }

    private static void AssertMatchesExpected(
        PgKeyedPacketStore store, string[] scopes, Dictionary<(string Scope, string Channel), HashSet<string>> expected)
    {
        foreach (string scope in scopes)
        {
            IReadOnlyList<StoreSyncPayload> snapshot = store.SnapshotFor(scope);
            foreach (StoreSyncPayload payload in snapshot)
            {
                HashSet<string> expectedKeys = expected[(scope, payload.Channel)];
                Assert.Equal(expectedKeys.Count, payload.Entries.Count);
                foreach (string key in expectedKeys)
                {
                    Assert.True(payload.Entries.ContainsKey(key), $"missing {scope}/{payload.Channel}/{key}");
                }
            }
        }
    }

    private static JsonElement ParsePayload(string json)
    {
        return JsonDocument.Parse(json).RootElement;
    }
}
