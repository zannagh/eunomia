// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.ComponentModel.DataAnnotations;

namespace Eunomia.Server.Data.Entities;

/// <summary>
/// One relayed, replicated keyed packet, partitioned by (Scope, Channel, Key). <see cref="Payload"/>
/// is the original JSON stored verbatim as text (never jsonb) so it round-trips losslessly - the
/// server treats it as opaque and never reorders or drops fields.
/// </summary>
public class KeyedEntry
{
    /// <summary>MC-server identity, e.g. "mc.hypixel.net:25565".</summary>
    [MaxLength(512)]
    public required string Scope { get; set; }

    /// <summary>"namespace:path" channel identifier.</summary>
    [MaxLength(512)]
    public required string Channel { get; set; }

    /// <summary>Slash-joined key path (e.g. a player UUID).</summary>
    [MaxLength(512)]
    public required string Key { get; set; }

    /// <summary>Raw JSON payload, stored verbatim (lossless).</summary>
    [Required]
    public required string Payload { get; set; }

    public DateTime UpdatedAt { get; set; }
}
