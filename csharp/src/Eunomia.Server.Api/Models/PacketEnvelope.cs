// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

using System.Text.Json;

namespace Eunomia.Server.Api.Models;

/// <summary>
/// Wire envelope shared with the Java client, used for both REST puts and websocket pushes.
/// <see cref="Payload"/> is opaque to the server: it is stored/relayed verbatim.
/// </summary>
public sealed record PacketEnvelope
{
    /// <summary>MC-server identity, e.g. "mc.hypixel.net:25565"; partitions all data.</summary>
    public required string Scope { get; init; }

    /// <summary>"namespace:path" channel identifier.</summary>
    public required string Channel { get; init; }

    /// <summary>Slash-joined KeyPath string for keyed packets; null for plain packets.</summary>
    public string? Key { get; init; }

    /// <summary>True: store + push-on-connect + relay. False: relay only.</summary>
    public required bool Replicated { get; init; }

    /// <summary>Player UUID (canonical MC form, dashed lowercase).</summary>
    public required string Sender { get; init; }

    /// <summary>The DTO JSON, opaque to the server.</summary>
    public required JsonElement Payload { get; init; }
}
