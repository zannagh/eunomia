package de.zannagh.eunomia;

import com.google.gson.Gson;
import de.zannagh.eunomia.common.PackRepositoryProvider;
import de.zannagh.eunomia.examples.ExampleServerHandlers;
import de.zannagh.eunomia.networking.CommunicationManager;
import de.zannagh.eunomia.networking.loader.LoaderNetwork;
import de.zannagh.eunomia.networking.serialization.NetworkSerializer;
import de.zannagh.eunomia.serialization.JsonSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;

/**
 * Shared entry point for the Eunomia library. Holds nothing loader-specific; the per-loader
 * initializers ({@code EunomiaFabric}, {@code EunomiaNeoForge}) delegate to {@link #init()}.
 */
public final class Eunomia {

    private static final HashSet<PackRepositoryProvider> PACK_REPOSITORY_PROVIDERS = new HashSet<>();

    public static final String MOD_ID = "eunomia";
    public static final Logger LOGGER = LoggerFactory.getLogger("Eunomia");
    public static Gson SERIALIZER;

    private Eunomia() {
    }

    public static void init() {
        SERIALIZER = new JsonSerializer().GSON;
        // The networking core resolves payloads with this Gson (config type adapters included).
        NetworkSerializer.setGson(SERIALIZER);
        // Install the loader networking adapter (registration listener + server transport), then the
        // example server handlers so a fresh install already answers the eunomia:* example packets.
        LoaderNetwork.init();
        ExampleServerHandlers.register();
        // Answer client capability probes so clients can detect this server runs Eunomia.
        CommunicationManager.enableServerHandshake();
        LOGGER.info("Eunomia shared library loaded");
    }

    public static void registerPackRespositoryProvider(PackRepositoryProvider provider) {
        PACK_REPOSITORY_PROVIDERS.add(provider);
    }

    public static HashSet<PackRepositoryProvider> getPackRepositoryProviders() {
        return PACK_REPOSITORY_PROVIDERS;
    }
}
