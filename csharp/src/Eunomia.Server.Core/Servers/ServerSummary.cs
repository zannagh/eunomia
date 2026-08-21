// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

namespace Eunomia.Server.Core.Servers;

/// <summary>
/// Dashboard-facing snapshot of one Minecraft server (scope): its bookkeeping row joined with the
/// live websocket presence tracked by the connection manager. A server with a persisted record but
/// no live connection still lists, marked <see cref="Online"/> = false.
/// </summary>
public sealed record ServerSummary(
    string Scope,
    string? Name,
    int LiveUserCount,
    long UpdateCount,
    DateTime FirstSeen,
    DateTime LastSeen,
    bool IsBlocked,
    string? BlockReason,
    bool Online);
