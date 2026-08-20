package de.zannagh.eunomia.networking.packets;

import de.zannagh.eunomia.networking.comms.Side;

/**
 * Context for a clientbound packet as seen on the receiving client. Implemented per platform (the
 * loader wraps the {@code ClientPacketListener}/{@code Minecraft}). A handler that just wants to
 * apply the payload needs nothing from here; one that wants to answer the server uses {@link #reply}.
 */
public interface ClientContext extends PacketContext {

    @Override
    default Side side() {
        return Side.CLIENT;
    }

    /** Sends a packet back up to the server on the given channel. */
    <T> void reply(PacketType<T> type, T data);
}
