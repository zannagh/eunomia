// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.ComponentModel.DataAnnotations;

namespace Eunomia.Server.Data.Entities;

/// <summary>
/// One row per Minecraft server (identified by its <see cref="Scope"/>, a host:port string) that has
/// relayed data. Tracks first/last activity and administrative blocking state.
/// </summary>
public class ServerRecord
{
    /// <summary>MC-server identity, e.g. "mc.hypixel.net:25565". Primary key.</summary>
    [Key]
    [MaxLength(512)]
    public required string Scope { get; set; }

    /// <summary>Human-readable name reported by the Java client (ServerData.name); null if unknown.</summary>
    [MaxLength(512)]
    public string? Name { get; set; }

    public DateTime FirstSeen { get; set; }

    public DateTime LastSeen { get; set; }

    public long UpdateCount { get; set; }

    public bool IsBlocked { get; set; }

    [MaxLength(1024)]
    public string? BlockReason { get; set; }
}
