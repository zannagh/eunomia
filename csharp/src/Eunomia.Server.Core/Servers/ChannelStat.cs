// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

namespace Eunomia.Server.Core.Servers;

/// <summary>
/// Per-channel stored-entry count for a scope, shown in the server detail view.
/// </summary>
public sealed record ChannelStat(string Channel, int EntryCount);
