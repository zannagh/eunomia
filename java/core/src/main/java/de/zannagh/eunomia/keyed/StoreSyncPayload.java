package de.zannagh.eunomia.keyed;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The batch snapshot of one replicated store, pushed to a client after it connects (over the Minecraft transport
 * as the built-in {@code eunomia:store_sync} packet, or over the external relay's WebSocket). It carries the
 * owning store's {@link #channel} identity plus every entry as {@code keyPath -> json(value)}.
 * <p>
 * Entry values are pre-serialized JSON <em>strings</em> rather than typed objects so the generic sync packet stays
 * type-agnostic: the receiving {@link ReplicatedClientStore} deserializes each string with its own concrete value
 * type. Keys are {@link KeyPath#toString() slash-joined} paths.
 *
 * @since 0.1.0
 */
public class StoreSyncPayload {

    public String channel;

    public Map<String, String> entries;

    public StoreSyncPayload() {
        this.entries = new LinkedHashMap<>();
    }

    public StoreSyncPayload(String channel, Map<String, String> entries) {
        this.channel = channel;
        this.entries = entries;
    }
}
