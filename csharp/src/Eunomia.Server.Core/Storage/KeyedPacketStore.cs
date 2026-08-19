// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Collections.Concurrent;
using System.Text.Json;
using Eunomia.Server.Core.Serialization;
using Microsoft.Extensions.Logging;

namespace Eunomia.Server.Core.Storage;

/// <summary>
/// In-memory, file-persisted implementation of <see cref="IKeyedPacketStore"/>. Entries are kept
/// in memory for fast reads and mirrored to <c>./data/&lt;scope&gt;/&lt;channel&gt;.json</c> so
/// they survive a restart. Every entry's payload is stored as its raw JSON string.
/// </summary>
public sealed class KeyedPacketStore : IKeyedPacketStore
{
    private readonly ConcurrentDictionary<(string Scope, string Channel), ConcurrentDictionary<string, string>> _store = new();
    private readonly ILogger<KeyedPacketStore> _logger;
    private readonly string _dataDir;

    public KeyedPacketStore(ILogger<KeyedPacketStore> logger, string dataDir = "data")
    {
        _logger = logger;
        _dataDir = dataDir;
        LoadFromDisk();
    }

    public void Put(string scope, string channel, string key, JsonElement payload)
    {
        ConcurrentDictionary<string, string> channelEntries =
            _store.GetOrAdd((scope, channel), _ => new ConcurrentDictionary<string, string>());
        channelEntries[key] = payload.GetRawText();

        PersistChannel(scope, channel, channelEntries);
    }

    public IReadOnlyList<StoreSyncPayload> SnapshotFor(string scope)
    {
        List<StoreSyncPayload> result = new();
        foreach (KeyValuePair<(string Scope, string Channel), ConcurrentDictionary<string, string>> entry in _store)
        {
            if (entry.Key.Scope != scope || entry.Value.IsEmpty)
            {
                continue;
            }

            result.Add(new StoreSyncPayload(entry.Key.Channel, new Dictionary<string, string>(entry.Value)));
        }

        return result;
    }

    private void LoadFromDisk()
    {
        if (!Directory.Exists(_dataDir))
        {
            return;
        }

        try
        {
            foreach (string scopeDir in Directory.GetDirectories(_dataDir))
            {
                foreach (string channelFile in Directory.GetFiles(scopeDir, "*.json"))
                {
                    LoadChannelFile(channelFile);
                }
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to load keyed packet store from {DataDir}", _dataDir);
        }
    }

    private void LoadChannelFile(string channelFile)
    {
        try
        {
            string json = File.ReadAllText(channelFile);
            PersistedChannel? persisted = JsonSerializer.Deserialize<PersistedChannel>(json, EunomiaJsonOptions.Wire);
            if (persisted is null)
            {
                return;
            }

            ConcurrentDictionary<string, string> channelEntries = _store.GetOrAdd(
                (persisted.Scope, persisted.Channel), _ => new ConcurrentDictionary<string, string>());
            foreach (KeyValuePair<string, string> pair in persisted.Entries)
            {
                channelEntries[pair.Key] = pair.Value;
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to load keyed packet store file {File}", channelFile);
        }
    }

    private void PersistChannel(string scope, string channel, ConcurrentDictionary<string, string> entries)
    {
        try
        {
            string scopeDir = Path.Combine(_dataDir, Sanitize(scope));
            Directory.CreateDirectory(scopeDir);
            string file = Path.Combine(scopeDir, Sanitize(channel) + ".json");
            PersistedChannel persisted = new(scope, channel, new Dictionary<string, string>(entries));
            string json = JsonSerializer.Serialize(persisted, EunomiaJsonOptions.Wire);
            File.WriteAllText(file, json);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to persist keyed packet store for {Scope}/{Channel}", scope, channel);
        }
    }

    private static string Sanitize(string value)
    {
        char[] invalid = Path.GetInvalidFileNameChars();
        char[] result = value.ToCharArray();
        for (int i = 0; i < result.Length; i++)
        {
            if (Array.IndexOf(invalid, result[i]) >= 0 || result[i] is ':' or '/')
            {
                result[i] = '_';
            }
        }

        return new string(result);
    }
}
