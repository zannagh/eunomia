// Copyright (c) 2026, zannagh. All rights reserved.
// See License in the project root for license information.

namespace Eunomia.Server.Core.Communication;

/// <summary>
/// Thin tagged wrapper for every server-&gt;client push, so the Java client can distinguish a
/// per-update <c>PacketEnvelope</c> from a batch <c>StoreSyncPayload</c> without a second frame
/// type: <c>{ "type": "envelope", "data": &lt;PacketEnvelope&gt; }</c> or
/// <c>{ "type": "store_sync", "data": &lt;StoreSyncPayload&gt; }</c>.
/// </summary>
/// <typeparam name="T">The payload type carried in <c>data</c>.</typeparam>
public sealed record WsFrame<T>(string Type, T Data)
{
    public static WsFrame<T> Envelope(T data) => new("envelope", data);

    public static WsFrame<T> StoreSync(T data) => new("store_sync", data);
}
