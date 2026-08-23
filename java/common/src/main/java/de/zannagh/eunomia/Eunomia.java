package de.zannagh.eunomia;

import com.google.gson.Gson;
import de.zannagh.eunomia.admin.ServerSettingsServerHandlers;
import de.zannagh.eunomia.common.PackRepositoryProvider;
import de.zannagh.eunomia.configuration.ConfigurationProvider;
import de.zannagh.eunomia.configuration.EunomiaConfig;
import de.zannagh.eunomia.configuration.EunomiaConfiguration;
import de.zannagh.eunomia.configuration.EunomiaServerConfig;
import de.zannagh.eunomia.configuration.EunomiaSyncSettings;
import de.zannagh.eunomia.configuration.FileConfigurationProvider;
import de.zannagh.eunomia.examples.ExampleServerHandlers;
import de.zannagh.eunomia.keyed.ReplicatedStores;
import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.loader.LoaderNetwork;
import de.zannagh.eunomia.networking.serialization.NetworkSerializer;
import de.zannagh.eunomia.serialization.SerializationManager;
import de.zannagh.eunomia.server.ServerConnectionEventConsumer;
import de.zannagh.eunomia.server.ServerConnectionEvents;
import de.zannagh.eunomia.utils.ServerUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
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

    /**
     * Provides centralized access to the configuration system for the Eunomia mod, specifically
     * managing instances of {@link EunomiaConfig}. This static variable acts as the global configuration
     * provider for Eunomia, allowing loading, updating, saving, and retrieving the mod's configuration.
     *
     * <ul>
     *   <li>Supports defining and persisting both default and customized configurations.</li>
     *   <li>Utilizes the {@link ConfigurationProvider} interface to abstract various storage and
     *       retrieval mechanisms for the configuration.</li>
     *   <li>Ensures configuration changes are modular and encapsulated within the EunomiaConfig
     *       implementation.</li>
     * </ul>
     *
     * The {@link EunomiaConfig} managed by this provider includes key options related to the external
     * fallback behavior, such as toggling external relay support and specifying relay addresses.
     */
    public static ConfigurationProvider<EunomiaConfig> CONFIG_PROVIDER;

    /**
     * The server-side counterpart of {@link #CONFIG_PROVIDER}, backed by {@code config/eunomia-server.json}.
     * Holds the operator's external-transport policy, which is advertised to every joining client through the
     * capability handshake.
     */
    public static ConfigurationProvider<EunomiaServerConfig> SERVER_CONFIG_PROVIDER;

    /** The directory both config files live in. Relative to the game directory, as every loader expects. */
    private static final Path DEFAULT_CONFIG_DIRECTORY = Path.of("config");

    private Eunomia() {
    }

    /**
     * Builds both configuration providers (loading, migrating and writing their files as needed) and wires the
     * settings resolver to them.
     *
     * <p>Idempotent on purpose: {@link #init()} runs once per loader entry point, and on a combined client the
     * client and server initializers can both reach it. Re-running would otherwise throw away in-memory edits by
     * re-reading from disk. Split out from {@code init()} so it can run - and be tested - without any of the
     * Minecraft-side wiring.</p>
     *
     * @param configDirectory the directory holding {@code eunomia-client.json} / {@code eunomia-server.json}
     */
    public static synchronized void initConfiguration(Path configDirectory) {
        if (CONFIG_PROVIDER != null && SERVER_CONFIG_PROVIDER != null) {
            return;
        }
        // SerializationManager.init() may not have run yet (a consumer may bootstrap config first); a plain Gson
        // round-trips both config records fine, they are flat POJOs with no custom adapters.
        Gson localGson = SerializationManager.SERIALIZER != null ? SerializationManager.SERIALIZER : new Gson();
        if (CONFIG_PROVIDER == null) {
            CONFIG_PROVIDER = new FileConfigurationProvider<>(
                    configDirectory.resolve("eunomia-client.json"),
                    EunomiaConfig.class, EunomiaConfig::withCurrentSchema, localGson, Eunomia.LOGGER);
        }
        if (SERVER_CONFIG_PROVIDER == null) {
            SERVER_CONFIG_PROVIDER = new FileConfigurationProvider<>(
                    configDirectory.resolve("eunomia-server.json"),
                    EunomiaServerConfig.class, EunomiaServerConfig::new, localGson, Eunomia.LOGGER);
        }
        // Rung one of the precedence chain, and what this server advertises as rung two to its clients.
        EunomiaSyncSettings.bindClientConfigSource(Eunomia::getConfig);
        CommunicationManager.setServerSyncPolicySource(() -> getServerConfig().toSyncPolicy());
    }

    /** Bootstraps the configuration in the default {@code config/} directory. */
    public static void initConfiguration() {
        initConfiguration(DEFAULT_CONFIG_DIRECTORY);
    }

    /** Drops both providers so the next {@link #initConfiguration(Path)} rebuilds them. Tests only. */
    public static synchronized void resetConfigurationForTesting() {
        CONFIG_PROVIDER = null;
        SERVER_CONFIG_PROVIDER = null;
        EunomiaSyncSettings.resetForTesting();
    }

    /**
     * The client-side configuration - the player's <em>overrides</em>, not the effective settings. Read
     * {@code EunomiaSyncSettings} for the values that actually govern the transport.
     */
    public static EunomiaConfig getConfig() {
        initConfiguration();
        return CONFIG_PROVIDER.getValue();
    }

    /** The server-side configuration, i.e. the operator's external-transport policy. */
    public static EunomiaServerConfig getServerConfig() {
        initConfiguration();
        return SERVER_CONFIG_PROVIDER.getValue();
    }

    /**
     * Opens a fluent, side-agnostic configuration chain for a consuming mod. Chain the settings you care
     * about and finish with {@code apply()}; nothing is written before that:
     *
     * <pre>{@code
     * Eunomia.configure()
     *         .externalFallback(true)
     *         .externalServerAddress("https://sync.mymod.example")
     *         .toasts(false)
     *         .apply();
     * }</pre>
     *
     * <p>Order-independent with respect to {@link #init()}: call it before or after, from your mod
     * initializer either way. Every value lands in a holder that is read at the point of use rather than
     * snapshotted during init.</p>
     *
     * <p>Client-only settings that need a {@code net.minecraft.client} type - moving eunomia's settings
     * button to your own screen, or relabelling it - are on {@code EunomiaClient.configure()} instead.
     * This class is loaded on dedicated servers and therefore names no client type anywhere.</p>
     *
     * @return a fresh, single-use configuration builder.
     */
    public static EunomiaConfiguration configure() {
        return new EunomiaConfiguration();
    }

    public static void init() {
        initConfiguration();
        // Permission compat, once, here. Detection is the class-load-free resource probe, so registering it
        // this early is safe; ServerUtil used to self-bootstrap it on the first permission query instead.
        ServerUtil.initPermissionCompat();
        SerializationManager.init();
        SERIALIZER = SerializationManager.SERIALIZER;

        // Install as a DEFAULT only: a consuming mod resolves payloads with its own Gson (its config type
        // adapters included) via NetworkSerializer.setGson. Using installDefaultGson here means that
        // explicit install always wins no matter which mod's init runs first - the two used to race, and
        // when this bare Gson clobbered the consumer's, its typed payloads deserialized through Gson's
        // reflective adapter and failed on the consumer's custom on-wire shapes.
        // NOTE: SerializationManager.init() above already installs its own network Gson (SERIALIZER's fields
        // minus @LocalOnly) as the default; SERIALIZER itself is the full/local one, so re-installing it here
        // would silently undo that stripping. Install the network Gson explicitly instead.
        NetworkSerializer.installDefaultGson(SerializationManager.NETWORK);
        // Install the loader networking adapter (registration listener + server transport), then the
        // example server handlers so a fresh install already answers the eunomia:* example packets.
        LoaderNetwork.init();
        ExampleServerHandlers.register();
        // The administrative Cloud Sync settings channel. Registered before the handshake is enabled so its
        // clientbound answer channel is among the receiver channels the very first probe is told about, and
        // so an admin that joins immediately already has a server willing to answer.
        ServerSettingsServerHandlers.register();
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
