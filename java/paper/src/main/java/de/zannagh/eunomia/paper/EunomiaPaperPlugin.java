package de.zannagh.eunomia.paper;

import com.google.gson.Gson;
import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.packets.PacketType;
import de.zannagh.eunomia.networking.examples.ExampleHandlers;
import de.zannagh.eunomia.networking.examples.ExamplePackets;
import de.zannagh.eunomia.networking.examples.ExampleReplication;
import de.zannagh.eunomia.networking.serialization.NetworkSerializer;
import de.zannagh.eunomia.paper.admin.PaperServerSettings;
import de.zannagh.eunomia.paper.net.ChannelSubscriber;
import de.zannagh.eunomia.paper.net.PaperMessageListener;
import de.zannagh.eunomia.paper.config.PaperServerConfig;
import de.zannagh.eunomia.paper.net.PaperServerTransport;
import de.zannagh.eunomia.paper.perm.PermissionResolver;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side Eunomia networking, as a Bukkit plugin. It reuses {@code :core} for everything that
 * matters - the {@link ExamplePackets} definitions, the {@link ExampleHandlers#registerPingPong()}
 * handler, and the JSON resolution - so the Paper side is a peer of the loaders on the same wire
 * protocol, not a separate hand-written schema. One jar covers every game version.
 */
public final class EunomiaPaperPlugin extends JavaPlugin {

    private PaperServerTransport transport;
    private PermissionResolver permissions;
    private PaperServerConfig serverConfig;

    @Override
    public void onEnable() {
        // Resolve payloads with a plain Gson (the shared example POJOs need no custom adapters). Installed
        // as a default so a plugin embedding Eunomia can still install its own richer Gson via setGson.
        NetworkSerializer.installDefaultGson(new Gson());

        transport = new PaperServerTransport(this);
        CommunicationManager.setServerTransport(transport);

        // Exactly the definitions and handler the loaders register - the whole point of depending on
        // :core rather than re-describing the packets here.
        CommunicationManager.register(ExamplePackets.PING);
        CommunicationManager.register(ExamplePackets.PONG);
        CommunicationManager.register(ExamplePackets.PERMISSION);
        ExampleHandlers.registerPingPong();
        // Register the replicated example store BEFORE channel registration so its sync channels
        // (the bidirectional data channel + eunomia:store_sync) are picked up and force-subscribed below.
        ExampleReplication.enableServer();
        // The operator's Cloud Sync policy, read from plugins/<this plugin>/eunomia-server.json and installed
        // as the source the handshake answer derives its advertisement from. Done BEFORE the handshake is
        // enabled so the very first probe already sees a real policy rather than "no opinion".
        serverConfig = PaperServerConfig.loadFrom(getDataFolder().toPath(), getSLF4JLogger());
        serverConfig.install();

        // Built here, at enable, so the LuckPerms lookup happens once - every plugin is loaded by
        // now, and the per-join path then only reads the cached flag. Built before the admin settings
        // channel below because that channel's permission check is this very resolver.
        permissions = new PermissionResolver(getLogger());
        // The administrative Cloud Sync settings channel, registered BEFORE registerBukkitChannels() so its
        // three channels are among the ones given a Bukkit plugin-messaging registration below.
        PaperServerSettings.register(serverConfig, permissions);

        // Answer capability probes so a client can detect this Paper server speaks Eunomia.
        CommunicationManager.enableServerHandshake();

        List<String> clientboundChannels = registerBukkitChannels();
        ChannelSubscriber subscriber = new ChannelSubscriber(getLogger(), clientboundChannels);
        getServer().getPluginManager().registerEvents(new PaperJoinListener(subscriber, transport, permissions), this);

        getLogger().info("Eunomia Paper networking enabled ("
                + CommunicationManager.serverboundTypes().size() + " C2S, "
                + CommunicationManager.clientboundTypes().size() + " S2C channels).");
    }

    /**
     * The permission resolver this plugin enabled with (op -> superperms -> LuckPerms). Exposed so
     * server-side handlers can ask "is this player an eunomia admin?" without re-detecting LuckPerms.
     *
     * @return the resolver, or {@code null} before {@link #onEnable()} ran
     */
    public PermissionResolver permissions() {
        return permissions;
    }

    /**
     * The operator's Cloud Sync policy binding this plugin enabled with. Exposed so a plugin embedding Eunomia
     * can read the effective policy (or {@link PaperServerConfig#reload()} it from its own reload command)
     * instead of parsing the file a second time.
     *
     * @return the binding, or {@code null} before {@link #onEnable()} ran
     */
    public PaperServerConfig serverConfig() {
        return serverConfig;
    }

    @Override
    public void onDisable() {
        Messenger messenger = getServer().getMessenger();
        messenger.unregisterIncomingPluginChannel(this);
        messenger.unregisterOutgoingPluginChannel(this);
    }

    /**
     * Registers each serverbound channel as incoming (with the dispatch listener) and each
     * clientbound channel as outgoing, returning the clientbound channel names to force-subscribe.
     */
    private List<String> registerBukkitChannels() {
        Messenger messenger = getServer().getMessenger();
        PaperMessageListener listener = new PaperMessageListener(getLogger(), transport);
        for (PacketType<?> type : CommunicationManager.serverboundTypes()) {
            messenger.registerIncomingPluginChannel(this, type.channelKey(), listener);
        }
        List<String> clientbound = new ArrayList<>();
        for (PacketType<?> type : CommunicationManager.clientboundTypes()) {
            messenger.registerOutgoingPluginChannel(this, type.channelKey());
            clientbound.add(type.channelKey());
        }
        return clientbound;
    }
}
