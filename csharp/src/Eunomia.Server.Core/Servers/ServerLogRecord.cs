// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

namespace Eunomia.Server.Core.Servers;

/// <summary>
/// A single per-server log line as surfaced to the dashboard, projected from the stored log entry.
/// </summary>
public sealed record ServerLogRecord(
    Guid Id,
    string Scope,
    DateTime Timestamp,
    string Level,
    string Message,
    string? Exception);
