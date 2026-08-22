package de.zannagh.eunomia.keyed;

import com.google.gson.Gson;
import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.serialization.NetworkSerializer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Client-side fan-out for the single shared {@code eunomia:store_sync} channel: one clientbound handler routes an
 * inbound {@link StoreSyncPayload} to the {@link ReplicatedClientStore} that owns its {@link StoreSyncPayload#channel}.
 * Registering the handler is idempotent, so any number of mirror stores can enable independently.
 */
final class StoreSyncClient {

    private static final Map<String, BiConsumer<StoreSyncPayload, Gson>> BINDINGS = new ConcurrentHashMap<>();

    private static volatile boolean registered = false;

    static {
        // Runs once, on first use. CommunicationManager.resetForTesting() clears its own handler maps, which
        // silently unbinds the handler ensureRegistered() installed below - but `registered` would stay true, so
        // the next enableClient() would skip re-binding and every inbound store_sync frame would be dropped with
        // no listener and no error. Hooking the reset keeps this flag honest about whether the handler is live.
        CommunicationManager.registerResetHook(StoreSyncClient::resetForTesting);
    }

    private StoreSyncClient() {
    }

    /** Returns this client to its unregistered state so a later {@link #ensureRegistered()} rebinds. */
    private static synchronized void resetForTesting() {
        BINDINGS.clear();
        registered = false;
    }

    static synchronized void ensureRegistered() {
        if (registered) {
            return;
        }
        CommunicationManager.onClientReceive(StoreSyncPackets.STORE_SYNC, (payload, context) -> {
            BiConsumer<StoreSyncPayload, Gson> binding = BINDINGS.get(payload.channel);
            if (binding != null) {
                binding.accept(payload, NetworkSerializer.gson());
            }
        });
        registered = true;
    }

    static void bind(String channel, BiConsumer<StoreSyncPayload, Gson> apply) {
        BINDINGS.put(channel, apply);
    }
}
