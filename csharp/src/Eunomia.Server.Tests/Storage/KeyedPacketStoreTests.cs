// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Text.Json;
using Eunomia.Server.Core.Storage;
using Microsoft.Extensions.Logging.Abstractions;

namespace Eunomia.Server.Tests.Storage;

public class KeyedPacketStoreTests : IDisposable
{
    private readonly string _dataDir = Path.Combine(Path.GetTempPath(), "eunomia-tests-" + Guid.NewGuid());

    public void Dispose()
    {
        if (Directory.Exists(_dataDir))
        {
            Directory.Delete(_dataDir, recursive: true);
        }
    }

    [Fact]
    public void Put_IsolatesEntriesByScope()
    {
        KeyedPacketStore store = new(NullLogger<KeyedPacketStore>.Instance, _dataDir);
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
        KeyedPacketStore store = new(NullLogger<KeyedPacketStore>.Instance, _dataDir);

        IReadOnlyList<StoreSyncPayload> snapshot = store.SnapshotFor("does-not-exist");

        Assert.Empty(snapshot);
    }

    [Fact]
    public void SnapshotFor_ReturnsOnePayloadPerChannel_WithRawJsonEntries()
    {
        KeyedPacketStore store = new(NullLogger<KeyedPacketStore>.Instance, _dataDir);
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
        KeyedPacketStore store = new(NullLogger<KeyedPacketStore>.Instance, _dataDir);

        store.Put("scope", "ns:chan", "key1", ParsePayload("""{"value":1}"""));
        store.Put("scope", "ns:chan", "key1", ParsePayload("""{"value":2}"""));

        StoreSyncPayload payload = Assert.Single(store.SnapshotFor("scope"));
        Assert.Equal("""{"value":2}""", payload.Entries["key1"]);
    }

    [Fact]
    public void PersistedEntries_SurviveAcrossStoreInstances()
    {
        // The scope contains ':' which is sanitized for the file path; loading must recover
        // the original unsanitized scope so a fresh store can still find it.
        const string scope = "mc.hypixel.net:25565";
        JsonElement payload = ParsePayload("""{"restored":true}""");

        KeyedPacketStore original = new(NullLogger<KeyedPacketStore>.Instance, _dataDir);
        original.Put(scope, "eunomia:profile", "player1", payload);

        KeyedPacketStore reloaded = new(NullLogger<KeyedPacketStore>.Instance, _dataDir);
        StoreSyncPayload snapshot = Assert.Single(reloaded.SnapshotFor(scope));

        Assert.Equal("eunomia:profile", snapshot.Channel);
        Assert.Equal("""{"restored":true}""", snapshot.Entries["player1"]);
    }

    private static JsonElement ParsePayload(string json)
    {
        return JsonDocument.Parse(json).RootElement;
    }
}
