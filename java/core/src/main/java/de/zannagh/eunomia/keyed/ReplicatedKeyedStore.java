package de.zannagh.eunomia.keyed;

import com.google.gson.Gson;
import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.packets.KeyedPacket;
import de.zannagh.eunomia.networking.packets.ServerContext;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * A {@link KeyedStore} that replicates: it ingests updates from clients on a bidirectional {@link KeyedPacket}
 * channel, persists them, relays each update to every <em>other</em> connected client, and can dump its whole
 * contents to a single newcomer as a {@link StoreSyncPayload}. This is the server-side half of a {@link Replicated}
 * DTO's lifecycle.
 * <p>
 * Key authority: by default an entry is stored under the payload's own {@link Keyed#keyPath()}. For a store keyed
 * by the sending player (the common per-player-config case), override {@link #keyFor} to return
 * {@code KeyPath.of(context.senderId())} so a client cannot write another player's entry - the server trusts the
 * authenticated sender, not a key the client serialized.
 *
 * @param <V> the replicated value type
 * @since 0.1.0
 */
public class ReplicatedKeyedStore<V extends Replicated> extends KeyedStore<V> {

    private final KeyedPacket<V> channel;

    private final Path persistenceFile;

    /**
     * @param channel         the bidirectional keyed channel this store syncs on (clients send updates serverbound,
     *                        the server relays them clientbound). Use {@link KeyedPacket#keyedBidirectional}.
     * @param persistenceFile where to persist the store on every change, or {@code null} for in-memory only.
     */
    public ReplicatedKeyedStore(int keyDepth, Class<V> valueClass, KeyedPacket<V> channel, Path persistenceFile, Gson gson) {
        this(keyDepth, valueClass, channel, persistenceFile, gson, null);
    }

    public ReplicatedKeyedStore(int keyDepth, Class<V> valueClass, KeyedPacket<V> channel) {
        this(keyDepth, valueClass, channel, null, null, null);
    }

    /**
     * @param defaultFactory builds a fresh default for an absent key, backing {@link #getOrCreate}; may be null.
     */
    protected ReplicatedKeyedStore(int keyDepth, Class<V> valueClass, KeyedPacket<V> channel, Path persistenceFile,
                                   Gson gson, Function<KeyPath, V> defaultFactory) {
        super(keyDepth, valueClass, defaultFactory, gson);
        this.channel = channel;
        this.persistenceFile = persistenceFile;
    }

    /** The wire identity of this store's sync channel; the client mirror matches on it. */
    public String channelKey() {
        return channel.channelKey();
    }

    /**
     * Installs the server-side wiring: loads any persisted state, registers the update handler (store → persist →
     * relay to other clients), and enrolls this store with {@link ReplicatedStores} so newcomers get a snapshot.
     * Returns {@code this} for chaining. Call once during server startup.
     */
    public ReplicatedKeyedStore<V> enableServer() {
        if (persistenceFile != null) {
            loadFrom(persistenceFile);
        }
        CommunicationManager.onServerReceive(channel, (payload, context) -> {
            V stored = put(keyFor(payload, context), payload);
            if (persistenceFile != null) {
                saveTo(persistenceFile);
            }
            CommunicationManager.broadcastExcept(context.senderId(), channel, stored);
        });
        ReplicatedStores.register(this);
        return this;
    }

    /** Pushes the whole store to one player as a single {@link StoreSyncPayload} batch. */
    public void pushSnapshotTo(UUID player) {
        CommunicationManager.sendToPlayer(player, StoreSyncPackets.STORE_SYNC, snapshotPayload());
    }

    /** Builds the batch snapshot: this store's channel plus every entry as {@code pathString -> json(value)}. */
    public StoreSyncPayload snapshotPayload() {
        Gson gson = serializer();
        Map<String, String> entries = new LinkedHashMap<>();
        snapshot().forEach((path, value) -> entries.put(path.toString(), gson.toJson(value)));
        return new StoreSyncPayload(channelKey(), entries);
    }

    /**
     * The key an inbound {@code payload} is stored under. Defaults to the payload's own {@link Keyed#keyPath()};
     * override to derive an authoritative key from the {@link ServerContext} (e.g. the authenticated sender id).
     */
    protected KeyPath keyFor(V payload, ServerContext context) {
        return payload.keyPath();
    }
}
