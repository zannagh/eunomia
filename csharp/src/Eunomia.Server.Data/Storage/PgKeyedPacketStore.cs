// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Collections.Concurrent;
using System.Text.Json;
using Eunomia.Server.Core.Storage;
using Eunomia.Server.Data.Entities;
using Microsoft.EntityFrameworkCore;

namespace Eunomia.Server.Data.Storage;

/// <summary>
/// Postgres-backed <see cref="IKeyedPacketStore"/>. Payloads are stored verbatim as raw JSON text
/// (<see cref="JsonElement.GetRawText"/> in, string out) so they round-trip losslessly, exactly like
/// the previous file store. The store is a singleton, so it uses short-lived contexts from an
/// <see cref="IDbContextFactory{TContext}"/> and serializes writes per (scope, channel) - matching the
/// old per-channel file lock so last-writer-wins holds under concurrency on any provider.
/// </summary>
public sealed class PgKeyedPacketStore : IKeyedPacketStore
{
    private readonly IDbContextFactory<EunomiaDbContext> _contextFactory;
    // Keyed by scope, NOT by (scope, channel). TouchServer does a read-then-insert on Servers, which is
    // keyed by scope alone: two puts to the same scope on different channels would take different locks,
    // both observe no server row, and both insert - a UNIQUE violation on Servers.Scope (23505 on
    // Postgres). Serializing per scope costs channel parallelism within one scope and buys correctness.
    private readonly ConcurrentDictionary<string, object> _scopeLocks = new();

    public PgKeyedPacketStore(IDbContextFactory<EunomiaDbContext> contextFactory)
    {
        _contextFactory = contextFactory;
    }

    public void Put(string scope, string channel, string key, JsonElement payload)
    {
        string raw = payload.GetRawText();
        object scopeLock = _scopeLocks.GetOrAdd(scope, _ => new object());
        lock (scopeLock)
        {
            using EunomiaDbContext context = _contextFactory.CreateDbContext();
            UpsertEntry(context, scope, channel, key, raw);
            TouchServer(context, scope);
            context.SaveChanges();
        }
    }

    public IReadOnlyList<StoreSyncPayload> SnapshotFor(string scope)
    {
        using EunomiaDbContext context = _contextFactory.CreateDbContext();
        List<KeyedEntry> entries = context.KeyedEntries
            .AsNoTracking()
            .Where(e => e.Scope == scope)
            .ToList();

        return entries
            .GroupBy(e => e.Channel)
            .Select(group => new StoreSyncPayload(
                group.Key,
                group.ToDictionary(e => e.Key, e => e.Payload)))
            .ToList();
    }

    private static void UpsertEntry(EunomiaDbContext context, string scope, string channel, string key, string raw)
    {
        DateTime now = DateTime.UtcNow;
        KeyedEntry? existing = context.KeyedEntries.Find(scope, channel, key);
        if (existing is null)
        {
            context.KeyedEntries.Add(new KeyedEntry
            {
                Scope = scope,
                Channel = channel,
                Key = key,
                Payload = raw,
                UpdatedAt = now,
            });
        }
        else
        {
            existing.Payload = raw;
            existing.UpdatedAt = now;
        }
    }

    private static void TouchServer(EunomiaDbContext context, string scope)
    {
        DateTime now = DateTime.UtcNow;
        ServerRecord? server = context.Servers.Find(scope);
        if (server is null)
        {
            context.Servers.Add(new ServerRecord
            {
                Scope = scope,
                FirstSeen = now,
                LastSeen = now,
                UpdateCount = 1,
            });
        }
        else
        {
            server.LastSeen = now;
            server.UpdateCount++;
        }
    }
}
