package de.zannagh.eunomia;

import com.google.gson.Gson;
import de.zannagh.eunomia.common.PackRepositoryProvider;
import de.zannagh.eunomia.networking.EunomiaServer;import de.zannagh.eunomia.serialization.JsonSerializer;
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
    public static EunomiaServer SERVER;

    private Eunomia() {
    }

    public static void init() {
        SERIALIZER = new JsonSerializer().GSON;
        SERVER = new EunomiaServer();
        LOGGER.info("Eunomia shared library loaded");
    }

    public static void registerPackRespositoryProvider(PackRepositoryProvider provider) {
        PACK_REPOSITORY_PROVIDERS.add(provider);
    }

    public static HashSet<PackRepositoryProvider> getPackRepositoryProviders() {
        return PACK_REPOSITORY_PROVIDERS;
    }
}
