package de.zannagh.eunomia.networking.handshake;

import de.zannagh.eunomia.networking.packets.PacketType;

/** The built-in capability-handshake channels. Registered automatically by both sides. */
public final class HandshakePackets {

    private HandshakePackets() {
    }

    /** Bumped when the handshake payload shape changes; carried both ways for forward diagnostics. */
    public static final int PROTOCOL_VERSION = 1;

    /** Client → server: "do you speak Eunomia?". */
    public static final PacketType<ClientHelloPayload> HELLO =
            PacketType.serverbound("eunomia", "hello", ClientHelloPayload.class);

    /** Server → client: "yes, and here are the channels I receive". */
    public static final PacketType<ServerHelloPayload> HELLO_ACK =
            PacketType.clientbound("eunomia", "hello_ack", ServerHelloPayload.class);
}
