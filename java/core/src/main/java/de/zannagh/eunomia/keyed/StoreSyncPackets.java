package de.zannagh.eunomia.keyed;

import de.zannagh.eunomia.networking.packets.PacketType;

/**
 * The single built-in channel that carries a {@link StoreSyncPayload} batch from the server to a connecting
 * client. One channel serves every {@link ReplicatedKeyedStore}; the payload's {@link StoreSyncPayload#channel}
 * field tells the client which store to apply it to.
 */
public final class StoreSyncPackets {

    private StoreSyncPackets() {
    }

    /** Server → client: the full contents of one replicated store, sent on (re)connect. */
    public static final PacketType<StoreSyncPayload> STORE_SYNC =
            PacketType.clientbound("eunomia", "store_sync", StoreSyncPayload.class);
}
