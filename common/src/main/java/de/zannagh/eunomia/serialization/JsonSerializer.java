package de.zannagh.eunomia.serialization;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.zannagh.eunomia.configuration.ConfigurationItemSerializer;
import de.zannagh.eunomia.configuration.ConfigurationSourceSerializer;
import net.minecraft.util.GsonHelper;

public class JsonSerializer {

    public JsonSerializer(){
        GSON = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapterFactory(new ConfigurationSourceSerializer())
                .registerTypeAdapterFactory(new ConfigurationItemSerializer())
                .create();
    }

    public Gson GSON;
}
