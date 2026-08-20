// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Text.Json;
using System.Text.Json.Nodes;
using Eunomia.Server.Core.Storage;
using Microsoft.Extensions.Logging.Abstractions;

namespace Eunomia.Server.Tests.Storage;

/// <summary>
/// The store treats every payload as opaque JSON (<see cref="JsonElement.GetRawText"/> in,
/// verbatim string out) - it never touches, reorders, or drops a field. These tests prove that
/// losslessness against real armor-hider fixtures instead of synthetic ones, since armor-hider is
/// the reference "replicated player config" consumer of this relay.
/// </summary>
public class ArmorHiderFormatCompatTests : IDisposable
{
    private const string Scope = "mc.armorhider-smoke.test:25565";
    private const string Channel = "armorhider:config";

    private static readonly string FixturesDir = Path.Combine(AppContext.BaseDirectory, "Fixtures");

    private readonly string _dataDir = Path.Combine(Path.GetTempPath(), "eunomia-armorhider-tests-" + Guid.NewGuid());

    public void Dispose()
    {
        if (Directory.Exists(_dataDir))
        {
            Directory.Delete(_dataDir, recursive: true);
        }
    }

    [Fact]
    public void Put_ClientConfig_RoundTripsLosslessly_InMemory()
    {
        JsonNode original = LoadFixture("client-config.json");
        string playerId = original["playerId"]!.GetValue<string>();
        JsonElement payload = JsonSerializer.Deserialize<JsonElement>(original.ToJsonString());

        KeyedPacketStore store = new(NullLogger<KeyedPacketStore>.Instance, _dataDir);
        store.Put(Scope, Channel, playerId, payload);

        StoreSyncPayload snapshot = Assert.Single(store.SnapshotFor(Scope));
        JsonNode roundTripped = JsonNode.Parse(snapshot.Entries[playerId])!;

        AssertDeepEqual(original, roundTripped);
    }

    [Fact]
    public void Put_ClientConfig_RoundTripsLosslessly_AcrossDiskReload()
    {
        JsonNode original = LoadFixture("client-config.json");
        string playerId = original["playerId"]!.GetValue<string>();
        JsonElement payload = JsonSerializer.Deserialize<JsonElement>(original.ToJsonString());

        KeyedPacketStore store = new(NullLogger<KeyedPacketStore>.Instance, _dataDir);
        store.Put(Scope, Channel, playerId, payload);

        KeyedPacketStore reloaded = new(NullLogger<KeyedPacketStore>.Instance, _dataDir);
        StoreSyncPayload snapshot = Assert.Single(reloaded.SnapshotFor(Scope));
        JsonNode roundTripped = JsonNode.Parse(snapshot.Entries[playerId])!;

        AssertDeepEqual(original, roundTripped);
    }

    [Fact]
    public void Put_EveryServerDictionaryEntry_RebuildsAnIdenticalDictionary()
    {
        JsonNode serverDictionary = LoadFixture("server-dictionary.json");
        JsonObject playerConfigs = serverDictionary["playerConfigs"]!.AsObject();

        KeyedPacketStore store = new(NullLogger<KeyedPacketStore>.Instance, _dataDir);
        foreach (KeyValuePair<string, JsonNode?> entry in playerConfigs)
        {
            JsonElement payload = JsonSerializer.Deserialize<JsonElement>(entry.Value!.ToJsonString());
            store.Put(Scope, Channel, entry.Key, payload);
        }

        // One StoreSyncPayload for the channel, with every uuid present and deep-equal to the
        // original config - this is exactly what gets pushed to a client connecting to this scope.
        StoreSyncPayload snapshot = Assert.Single(store.SnapshotFor(Scope));
        Assert.Equal(playerConfigs.Count, snapshot.Entries.Count);

        foreach (KeyValuePair<string, JsonNode?> entry in playerConfigs)
        {
            JsonNode rebuilt = JsonNode.Parse(snapshot.Entries[entry.Key])!;
            AssertDeepEqual(entry.Value!, rebuilt);
        }
    }

    private static JsonNode LoadFixture(string fileName)
    {
        string json = File.ReadAllText(Path.Combine(FixturesDir, fileName));
        return JsonNode.Parse(json)!;
    }

    private static void AssertDeepEqual(JsonNode expected, JsonNode actual)
    {
        Assert.True(
            JsonNode.DeepEquals(expected, actual),
            $"Expected:\n{expected.ToJsonString()}\n\nActual:\n{actual.ToJsonString()}");
    }
}
