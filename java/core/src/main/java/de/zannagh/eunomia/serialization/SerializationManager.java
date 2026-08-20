package de.zannagh.eunomia.serialization;

import com.google.gson.Gson;
import de.zannagh.eunomia.networking.serialization.NetworkSerializer;

public final class SerializationManager {
    /** The full/local Gson - everything, including {@code @LocalOnly} fields. Used for on-disk persistence. */
    public static Gson SERIALIZER;

    /** The wire Gson - the same config-aware builder as {@link #SERIALIZER}, minus {@code @LocalOnly} fields. */
    public static Gson NETWORK;

    public static void init() {
        JsonSerializer serializer = new JsonSerializer();
        SERIALIZER = serializer.GSON;
        NETWORK = serializer.NETWORK_GSON;
        // A default only: an explicit NetworkSerializer.setGson(...) from a consumer always wins.
        NetworkSerializer.installDefaultGson(NETWORK);
    }
}
