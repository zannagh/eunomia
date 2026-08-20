// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Text.Json;

namespace Eunomia.Server.Core.Storage;

/// <summary>
/// Server-side store for replicated keyed packets, partitioned by (scope, channel, key).
/// </summary>
public interface IKeyedPacketStore
{
    /// <summary>
    /// Upserts the payload for (scope, channel, key) and persists it to disk.
    /// </summary>
    void Put(string scope, string channel, string key, JsonElement payload);

    /// <summary>
    /// Returns one <see cref="StoreSyncPayload"/> per channel that has stored entries for
    /// <paramref name="scope"/>, for pushing to a client on (re)connect.
    /// </summary>
    IReadOnlyList<StoreSyncPayload> SnapshotFor(string scope);
}
