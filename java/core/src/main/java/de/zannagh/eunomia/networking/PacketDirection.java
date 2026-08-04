package de.zannagh.eunomia.networking;

/**
 * The direction a {@link PacketType} is allowed to travel. Kept separate from {@link Side} because
 * a packet's legal travel is a property of the packet definition, whereas the side is a property of
 * the running process.
 */
public enum PacketDirection {
    /** Client to server (C2S). Held to the tighter serverbound size ceiling when encoded. */
    SERVERBOUND,
    /** Server to client (S2C). */
    CLIENTBOUND,
    /** Both directions are permitted. Encoded with the serverbound ceiling to stay safe on the C2S leg. */
    BIDIRECTIONAL;

    public boolean allowsServerbound() {
        return this == SERVERBOUND || this == BIDIRECTIONAL;
    }

    public boolean allowsClientbound() {
        return this == CLIENTBOUND || this == BIDIRECTIONAL;
    }
}
