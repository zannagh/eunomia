package de.zannagh.eunomia.networking.serialization;

import com.google.gson.Gson;

/**
 * Holds the {@link Gson} the payload codec resolves packets with. Each platform installs its own
 * during init: the loader supplies a Gson enriched with its config type adapters, the Bukkit plugin
 * supplies one that knows the shared payload POJOs, a test harness can install a bare one. Defaults
 * to a plain {@code Gson} so the codec is usable before any explicit configuration.
 */
public final class NetworkSerializer {

    private static volatile Gson gson = new Gson();

    private NetworkSerializer() {
    }

    /** Installs the Gson used for all subsequent payload (de)serialization. */
    public static void setGson(Gson serializer) {
        if (serializer == null) {
            throw new IllegalArgumentException("Network serializer Gson must not be null");
        }
        gson = serializer;
    }

    public static Gson gson() {
        return gson;
    }
}
