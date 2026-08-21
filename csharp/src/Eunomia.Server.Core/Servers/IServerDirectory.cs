// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

namespace Eunomia.Server.Core.Servers;

/// <summary>
/// Read/telemetry access over the per-server bookkeeping used by the dashboard, plus presence
/// upserts. <see cref="TouchPresenceAsync"/> records the reported name and last-seen on websocket
/// handshake; it deliberately does NOT touch <c>UpdateCount</c>, which is single-sourced by the keyed
/// packet store on each stored update.
/// </summary>
public interface IServerDirectory
{
    /// <summary>
    /// Upserts a server's <c>Name</c> and <c>LastSeen</c> (and <c>FirstSeen</c> on first sight) without
    /// incrementing <c>UpdateCount</c>. Called when a client hands its scope+name over the socket.
    /// </summary>
    Task TouchPresenceAsync(string scope, string? name, CancellationToken cancellationToken = default);

    /// <summary>
    /// Lists every known server (persisted record union live-connected scopes) with its live user
    /// count folded in, newest activity first.
    /// </summary>
    Task<IReadOnlyList<ServerSummary>> ListAsync(CancellationToken cancellationToken = default);

    /// <summary>
    /// Returns full detail for one scope, or null if it has neither a record nor a live connection.
    /// </summary>
    Task<ServerDetail?> GetAsync(string scope, CancellationToken cancellationToken = default);

    /// <summary>
    /// Returns the most recent per-server log lines for a scope, newest first, optionally filtered by
    /// minimum level name and capped at <paramref name="limit"/>.
    /// </summary>
    Task<IReadOnlyList<ServerLogRecord>> GetLogsAsync(
        string scope,
        string? level = null,
        int limit = 100,
        CancellationToken cancellationToken = default);
}
