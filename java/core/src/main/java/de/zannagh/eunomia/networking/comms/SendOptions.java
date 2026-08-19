package de.zannagh.eunomia.networking.comms;

import de.zannagh.eunomia.networking.packets.PacketType;

/**
 * Timing policy for a client-to-server send, chosen per call on
 * {@link CommunicationManager#sendToServer(PacketType, Object, SendOptions)}.
 *
 * <p>Emitting a custom-payload channel a server does not understand can get the client disconnected,
 * so by default outgoing packets are held until Eunomia's
 * {@link CommunicationManager#serverCapabilities() capability probe} has resolved whether the joined
 * server runs Eunomia. These options let a caller pick how strict that gating is - from "only once the
 * server is known to receive this exact channel" to "send immediately, no matter what".</p>
 *
 * @see CommunicationManager#serverCapabilities()
 */
public enum SendOptions {

    /**
     * The default. Queue the packet until the capability probe resolves, then send it if the server
     * runs Eunomia ({@link de.zannagh.eunomia.networking.handshake.ServerCapabilities#isPresent()
     * present}) and drop it if it does not (or the probe times out). Packets queued before the probe
     * resolves are flushed in submission order.
     */
    AFTER_SUCCESSFUL_HANDSHAKE,

    /**
     * Like {@link #AFTER_SUCCESSFUL_HANDSHAKE}, but additionally requires the server to actually have a
     * receiver for this packet's channel
     * ({@link de.zannagh.eunomia.networking.handshake.ServerCapabilities#supports(PacketType)}). Use
     * this for optional/feature packets a Eunomia server may or may not handle: it is delivered only
     * when the server both runs Eunomia and declares it receives this channel, and dropped otherwise.
     */
    IF_SERVER_SUPPORTS,

    /**
     * Send immediately through the client transport, regardless of handshake state - the packet is not
     * queued or gated. Use only for traffic that is safe to emit before (or without) a resolved
     * handshake, such as the capability probe itself or a protocol both sides always understand.
     */
    ALWAYS
}
