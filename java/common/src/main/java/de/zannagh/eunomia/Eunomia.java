package de.zannagh.eunomia;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import de.zannagh.eunomia.common.PackRepositoryProvider;
import de.zannagh.eunomia.examples.ExampleServerHandlers;
import de.zannagh.eunomia.keyed.ReplicatedStores;
import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.loader.LoaderNetwork;
import de.zannagh.eunomia.networking.serialization.NetworkSerializer;
import de.zannagh.eunomia.serialization.JsonSerializer;
import de.zannagh.eunomia.serialization.SerializationManager;
import de.zannagh.eunomia.server.ServerConnectionEventConsumer;
import de.zannagh.eunomia.server.ServerConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
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
        SerializationManager.init();
        SERIALIZER = SerializationManager.SERIALIZER;

        // Install as a DEFAULT only: a consuming mod resolves payloads with its own Gson (its config type
        // adapters included) via NetworkSerializer.setGson. Using installDefaultGson here means that
        // explicit install always wins no matter which mod's init runs first - the two used to race, and
        // when this bare Gson clobbered the consumer's, its typed payloads deserialized through Gson's
        // reflective adapter and failed on the consumer's custom on-wire shapes.
        NetworkSerializer.installDefaultGson(SERIALIZER);
        // Install the loader networking adapter (registration listener + server transport), then the
        // example server handlers so a fresh install already answers the eunomia:* example packets.
        LoaderNetwork.init();
        ExampleServerHandlers.register();
        // Answer client capability probes so clients can detect this server runs Eunomia.
        CommunicationManager.enableServerHandshake();
        // On join, dump every registered replicated store to the newcomer. A no-op until a mod (or the example
        // wiring) registers a ReplicatedKeyedStore, so it is safe to install unconditionally.
        ServerConnectionEvents.registerJoin(new ServerConnectionEventConsumer() {
            @Override
            public void acceptPlayerJoin(MinecraftServer server, ServerPlayer player) {
                ReplicatedStores.pushAllTo(player.getUUID());
            }
        });
        LOGGER.info("Eunomia shared library loaded");
    }

    public static void registerPackRespositoryProvider(PackRepositoryProvider provider) {
        PACK_REPOSITORY_PROVIDERS.add(provider);
    }

    public static HashSet<PackRepositoryProvider> getPackRepositoryProviders() {
        return PACK_REPOSITORY_PROVIDERS;
    }
}
