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
    private static boolean explicitlyConfigured = false;

    private NetworkSerializer() {
    }

    /**
     * Installs the Gson a consumer wants payloads resolved with (its config type adapters included).
     * An explicit install always wins over a library-supplied {@link #installDefaultGson default},
     * whatever the order the two run in.
     */
    public static synchronized void setGson(Gson serializer) {
        if (serializer == null) {
            throw new IllegalArgumentException("Network serializer Gson must not be null");
        }
        gson = serializer;
        explicitlyConfigured = true;
    }

    /**
     * Installs a <em>fallback</em> Gson, used only until (or unless) a consumer installs its own via
     * {@link #setGson}. Eunomia's own init calls this with a bare Gson so the library is usable
     * stand-alone; because it never overrides an explicit {@link #setGson}, a consumer's richer Gson
     * wins regardless of mod-initialization order. Without this the two installs raced and the bare one
     * could clobber the consumer's, leaving payloads to deserialize through Gson's reflective adapter
     * (which rejects the consumer's custom on-wire shapes).
     */
    public static synchronized void installDefaultGson(Gson serializer) {
        if (serializer == null) {
            throw new IllegalArgumentException("Network serializer Gson must not be null");
        }
        if (!explicitlyConfigured) {
            gson = serializer;
        }
    }

    public static Gson gson() {
        return gson;
    }
}
