package de.zannagh.eunomia.serialization;

import com.google.gson.Gson;

public final class SerializationManager {
    public static Gson SERIALIZER;

    public static void init() {
        SERIALIZER = new JsonSerializer().GSON;
    }
}
