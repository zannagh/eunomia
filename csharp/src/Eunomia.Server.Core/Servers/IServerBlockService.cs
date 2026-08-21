// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

namespace Eunomia.Server.Core.Servers;

/// <summary>
/// Administrative blocking of Minecraft servers (scopes). Backed by <c>ServerRecord.IsBlocked</c> but
/// keeps the blocked set in memory so the per-packet and per-handshake checks on the hot path never
/// hit the database. Load the set once at startup with <see cref="InitializeAsync"/>.
/// </summary>
public interface IServerBlockService
{
    /// <summary>
    /// Returns true if the scope is currently blocked. Lock-free read against the in-memory set.
    /// </summary>
    bool IsBlocked(string scope);

    /// <summary>
    /// Loads the blocked-scope set from persistence. Call once on startup.
    /// </summary>
    Task InitializeAsync(CancellationToken cancellationToken = default);

    /// <summary>
    /// Blocks a scope (persists the flag + reason, updates the cache, and proactively closes any live
    /// sockets for that scope). Creating the record if the scope has never been seen.
    /// </summary>
    Task BlockAsync(string scope, string? reason, CancellationToken cancellationToken = default);

    /// <summary>
    /// Unblocks a scope (persists the cleared flag and updates the cache).
    /// </summary>
    Task UnblockAsync(string scope, CancellationToken cancellationToken = default);
}
