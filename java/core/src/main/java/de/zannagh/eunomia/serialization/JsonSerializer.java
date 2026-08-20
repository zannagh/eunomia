package de.zannagh.eunomia.serialization;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.zannagh.eunomia.configuration.ConfigurationItemSerializer;
import de.zannagh.eunomia.configuration.ConfigurationSourceSerializer;
import de.zannagh.eunomia.networking.serialization.LocalOnlyExclusionStrategy;

public class JsonSerializer {

    public JsonSerializer(){
        GsonBuilder builder = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapterFactory(new ConfigurationSourceSerializer())
                .registerTypeAdapterFactory(new ConfigurationItemSerializer());
        GSON = builder.create();
        // GsonBuilder.create() snapshots the builder's current state, so adding the exclusion strategy here
        // and building again leaves GSON above untouched - this second Gson is the same config-aware builder,
        // additionally stripping @LocalOnly fields, i.e. the shape actually sent over the wire.
        NETWORK_GSON = builder.setExclusionStrategies(new LocalOnlyExclusionStrategy()).create();
    }

    public Gson GSON;
    public Gson NETWORK_GSON;
}
