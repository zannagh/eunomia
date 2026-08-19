package de.zannagh.eunomia.networking.examples;

import de.zannagh.eunomia.networking.packets.PacketType;

/**
 * Shared packet definitions used by the example handlers on every platform and by the gametests.
 * Declaring them once here - not in the loader and again in Paper - is the whole point: both sides
 * route on the same {@link PacketType#channelKey()} and resolve into the same POJOs.
 */
public final class ExamplePackets {

    private ExamplePackets() {
    }

    /** Client asks the server to echo a message. */
    public static final PacketType<PingPayload> PING =
            PacketType.serverbound("eunomia", "example_ping", PingPayload.class);

    /** Server echoes the message back to the asking client. */
    public static final PacketType<PongPayload> PONG =
            PacketType.clientbound("eunomia", "example_pong", PongPayload.class);

    /** Server tells the client its permission level (sent on join). */
    public static final PacketType<PermissionPayload> PERMISSION =
            PacketType.clientbound("eunomia", "permission", PermissionPayload.class);
}
