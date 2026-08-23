package de.zannagh.eunomia.paper.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.zannagh.eunomia.configuration.ConfigurationProvider;
import de.zannagh.eunomia.configuration.EunomiaServerConfig;
import de.zannagh.eunomia.configuration.FileConfigurationProvider;
import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.handshake.ServerSyncPolicy;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Objects;

/**
 * The Paper plugin's binding to {@link EunomiaServerConfig} - the operator's Cloud Sync policy, and the thing a
 * joining client reads off the capability handshake as rung two of its precedence chain.
 *
 * <p>This exists because a Paper server is a peer of the loaders on the wire, not a lesser participant. Without
 * it the plugin would answer every capability probe with {@link ServerSyncPolicy#UNKNOWN}, and an operator who
 * had deliberately pointed their players at a relay would have no way to say so. The shape read here is the very
 * same {@code EunomiaServerConfig} the loaders persist - it lives in the Minecraft-free {@code :core} precisely
 * so both server implementations can share one file format rather than two that drift.
 *
 * <p>The policy is read <em>per probe</em>, never snapshotted: {@link #install()} hands
 * {@link CommunicationManager} a supplier, so an operator who edits the file and calls {@link #reload()} affects
 * the next client to join without the plugin re-registering anything.
 */
public final class PaperServerConfig {

    /** The config file name, identical to the loaders' so an operator recognises it across server flavours. */
    public static final String FILE_NAME = "eunomia-server.json";

    private final ConfigurationProvider<EunomiaServerConfig> provider;

    private PaperServerConfig(ConfigurationProvider<EunomiaServerConfig> provider) {
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    /**
     * Loads (or creates, with the framework defaults) {@code eunomia-server.json} in the given directory.
     *
     * <p>Pretty-printed on write on purpose: this is a file a human operator edits by hand, unlike the wire
     * payloads elsewhere in the plugin. Loading never throws - {@link FileConfigurationProvider} falls back to
     * the defaults and logs, because a malformed config must degrade the advertisement, not the server start.
     *
     * @param directory the directory holding the file; created if missing.
     * @param logger    the logger load/save failures are reported on.
     * @return the loaded binding.
     */
    public static PaperServerConfig loadFrom(Path directory, Logger logger) {
        Objects.requireNonNull(directory, "directory");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return new PaperServerConfig(new FileConfigurationProvider<>(
                directory.resolve(FILE_NAME),
                EunomiaServerConfig.class,
                EunomiaServerConfig::new,
                gson,
                logger));
    }

    /** The configuration as currently loaded. */
    public EunomiaServerConfig value() {
        return provider.getValue();
    }

    /** The policy this server would advertise right now, derived fresh from the current configuration. */
    public ServerSyncPolicy policy() {
        return value().toSyncPolicy();
    }

    /** Re-reads the file, so an operator edit takes effect for the next joining client. */
    public void reload() {
        provider.load();
    }

    /**
     * Replaces the configuration and writes it to disk.
     *
     * <p>The counterpart of {@link #reload()} for the in-game path: an administrator editing the Cloud Sync
     * settings through eunomia's settings screen lands here, via the server-side settings exchange, which has
     * already decided they are allowed to and already validated the address. This method deliberately does no
     * checking of its own - it is not the security boundary, and a second half-hearted check here would
     * invite the belief that it is.</p>
     *
     * <p>Because {@link #install()} bound {@link #policy()} as a supplier rather than a snapshot, the new
     * values are what the next capability probe is answered with; nothing needs re-registering.</p>
     *
     * @param updated the replacement configuration.
     */
    public void apply(EunomiaServerConfig updated) {
        provider.updateAndSave(Objects.requireNonNull(updated, "updated"));
    }

    /**
     * Installs this config as the source {@link CommunicationManager} reads when answering a capability probe.
     * Call before {@link CommunicationManager#enableServerHandshake()} for clarity, though the supplier is read
     * per probe so the order does not actually matter.
     */
    public void install() {
        CommunicationManager.setServerSyncPolicySource(this::policy);
    }
}
