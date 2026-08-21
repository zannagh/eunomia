// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Collections.Concurrent;
using System.Diagnostics.CodeAnalysis;

namespace Eunomia.Server.Authentication.Helpers;

/// <summary>
/// Concurrent map whose entries fall out after a fixed lifetime. Entries are consumed (removed) on
/// lookup, and a periodic sweep drops only the entries older than the window - deliberately not a
/// wholesale <c>Clear()</c>, which would discard in-flight OAuth handshakes and fail logins started
/// shortly before each tick.
/// </summary>
/// <typeparam name="TValue">The stashed value type.</typeparam>
public sealed class ExpiringMap<TValue>
{
    private readonly ConcurrentDictionary<string, Entry> entries = new();
    private readonly TimeSpan lifetime;

    public ExpiringMap(TimeSpan lifetime, TimeSpan sweepInterval)
    {
        this.lifetime = lifetime;
        RecurringTask.Create(Sweep, sweepInterval, CancellationToken.None);
    }

    public void Add(string key, TValue value)
    {
        entries.TryAdd(key, new Entry(value, DateTime.UtcNow.Add(lifetime)));
    }

    /// <summary>Removes and returns the entry, unless it is missing or already past its lifetime.</summary>
    public bool TryConsume(string key, [MaybeNullWhen(false)] out TValue value)
    {
        if (entries.TryRemove(key, out Entry entry) && entry.ExpiresAt > DateTime.UtcNow)
        {
            value = entry.Value;
            return true;
        }

        value = default;
        return false;
    }

    private void Sweep()
    {
        DateTime now = DateTime.UtcNow;
        foreach (KeyValuePair<string, Entry> pair in entries)
        {
            if (pair.Value.ExpiresAt <= now)
            {
                entries.TryRemove(pair.Key, out _);
            }
        }
    }

    private readonly record struct Entry(TValue Value, DateTime ExpiresAt);
}
