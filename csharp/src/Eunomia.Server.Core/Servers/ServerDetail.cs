// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

namespace Eunomia.Server.Core.Servers;

/// <summary>
/// Full server detail: the <see cref="ServerSummary"/> plus per-channel stored-entry counts and the
/// total number of stored keyed entries across all channels.
/// </summary>
public sealed record ServerDetail(
    ServerSummary Summary,
    IReadOnlyList<ChannelStat> Channels,
    long TotalEntries);
