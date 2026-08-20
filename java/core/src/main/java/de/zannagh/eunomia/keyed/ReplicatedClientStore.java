package de.zannagh.eunomia.keyed;

import com.google.gson.Gson;
import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.packets.KeyedPacket;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The client-side mirror of a {@link ReplicatedKeyedStore}. It holds a local {@link KeyedStore} that is kept in
 * sync from the server two ways: a {@link StoreSyncPayload} batch replaces the whole store on (re)connect, and
 * each relayed update on the store's channel applies as a single entry. A mod reads the mirror to render every
 * player's shared state.
 *
 * @param <V> the replicated value type
 * @since 0.1.0
 */
public final class ReplicatedClientStore<V extends Replicated> {

    private final KeyedStore<V> store;

    private final KeyedPacket<V> channel;

    public ReplicatedClientStore(int keyDepth, Class<V> valueClass, KeyedPacket<V> channel) {
        this.store = new KeyedStore<>(keyDepth, valueClass);
        this.channel = channel;
    }

    /** The local mirror store - read it to see the current shared state. */
    public KeyedStore<V> store() {
        return store;
    }

    /**
     * Installs the client-side handlers: the shared {@code store_sync} batch apply (routed by channel) and the
     * per-entry update handler on this store's channel. Idempotent for the shared handler. Returns {@code this}.
     */
    public ReplicatedClientStore<V> enableClient() {
        StoreSyncClient.ensureRegistered();
        StoreSyncClient.bind(channel.channelKey(), this::applySnapshot);
        CommunicationManager.onClientReceive(channel, (payload, context) -> store.put(payload.keyPath(), payload));
        return this;
    }

    private void applySnapshot(StoreSyncPayload sync, Gson gson) {
        Map<KeyPath, V> parsed = new LinkedHashMap<>();
        sync.entries.forEach((pathString, json) -> {
            V value = gson.fromJson(json, store.valueClass());
            if (value != null) {
                parsed.put(KeyPath.ofSegments(List.of(pathString.split("/"))), value);
            }
        });
        store.replaceAll(parsed);
    }
}
