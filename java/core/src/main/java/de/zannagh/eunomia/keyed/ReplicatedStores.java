package de.zannagh.eunomia.keyed;

import de.zannagh.eunomia.networking.comms.CommunicationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The server-side registry of every {@link ReplicatedKeyedStore}. It exists so the platform join hooks (loader
 * {@code ServerConnectionEvents.registerJoin}, Paper {@code PaperJoinListener}) have one call to make -
 * {@link #pushAllTo(UUID)} - to dump all replicated stores to a newcomer, without knowing which stores exist.
 * <p>
 * Registration also lazily declares the shared {@link StoreSyncPackets#STORE_SYNC} channel exactly once, so the
 * platform codec/channel for the batch packet is built regardless of registration order.
 */
public final class ReplicatedStores {

    private static final CopyOnWriteArrayList<ReplicatedKeyedStore<?>> STORES = new CopyOnWriteArrayList<>();

    private static volatile boolean packetRegistered = false;

    private ReplicatedStores() {
    }

    static void register(ReplicatedKeyedStore<?> store) {
        ensureSyncPacketRegistered();
        STORES.add(store);
    }

    private static synchronized void ensureSyncPacketRegistered() {
        if (!packetRegistered) {
            CommunicationManager.register(StoreSyncPackets.STORE_SYNC);
            packetRegistered = true;
        }
    }

    /** Pushes every registered replicated store's full contents to {@code player} (one batch packet per store). */
    public static void pushAllTo(UUID player) {
        for (ReplicatedKeyedStore<?> store : STORES) {
            store.pushSnapshotTo(player);
        }
    }

    /**
     * The clientbound channel keys a platform must subscribe a joining player to before {@link #pushAllTo} can
     * reach them - the shared {@code store_sync} channel plus each store's own relay channel. Paper needs this
     * because its transport drops sends to unsubscribed channels.
     */
    public static List<String> clientboundChannels() {
        List<String> out = new ArrayList<>();
        out.add(StoreSyncPackets.STORE_SYNC.channelKey());
        for (ReplicatedKeyedStore<?> store : STORES) {
            out.add(store.channelKey());
        }
        return out;
    }
}
