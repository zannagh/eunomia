package de.zannagh.eunomia.client.networking;

import de.zannagh.eunomia.Eunomia;
import de.zannagh.eunomia.clients.ExternalClientTransport;
import de.zannagh.eunomia.clients.ExternalServerClient;
import de.zannagh.eunomia.clients.PingClient;
import de.zannagh.eunomia.configuration.EunomiaConfig;
import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.handshake.ServerCapabilities;
import net.minecraft.client.Minecraft;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Chooses the client send/receive path each time the capability probe resolves. The rule mirrors the design
 * exactly: if the joined Minecraft server runs Eunomia, use the Minecraft transport; otherwise, and <em>only</em>
 * if the player opted into the fallback, a relay address is configured and that relay is reachable, swap to the
 * external relay ({@link ExternalServerClient}) scoped to this server. A server-does-run-Eunomia resolution (or a
 * disconnect) always restores the Minecraft transport and tears down any relay connection.
 */
public final class ClientTransportSelector {

    private static final McClientTransport MC = new McClientTransport();

    private static volatile ExternalServerClient external;

    private ClientTransportSelector() {
    }

    /** Installs the Minecraft transport as the default and hooks the capability resolution to re-decide on join. */
    public static void init() {
        CommunicationManager.setClientTransport(MC);
        CommunicationManager.serverCapabilities().onResolved(ClientTransportSelector::onCapabilityResolved);
    }

    private static void onCapabilityResolved(ServerCapabilities capabilities) {
        Minecraft client = Minecraft.getInstance();
        if (capabilities.isPresent()) {
            // The MC server speaks Eunomia - never use the third-party relay alongside it.
            useMinecraftTransport();
            return;
        }
        EunomiaConfig config = Eunomia.getConfig();
        if (!config.externalFallbackEnabled() || !config.hasExternalServerAddress()) {
            useMinecraftTransport();
            return;
        }
        String scope = LocalClientIdentity.currentServerScope(client);
        var playerId = LocalClientIdentity.localPlayerId(client);
        if (scope == null) {
            useMinecraftTransport();
            return;
        }
        String address = config.externalServerAddress();
        // Reachability probe blocks, so decide off the client thread.
        CompletableFuture.runAsync(() -> {
            if (!PingClient.isReachable(address)) {
                Eunomia.LOGGER.info("External relay {} not reachable; staying on the Minecraft transport", address);
                return;
            }
            startExternal(address, scope, playerId);
        });
    }

    private static synchronized void startExternal(String address, String scope, UUID playerId) {
        stopExternal();
        ExternalServerClient client = new ExternalServerClient(address, scope, playerId, Eunomia.LOGGER);
        client.start();
        external = client;
        CommunicationManager.setClientTransport(new ExternalClientTransport(client));
        Eunomia.LOGGER.info("Routing Eunomia through external relay {} (scope {})", address, scope);
    }

    private static synchronized void stopExternal() {
        if (external != null) {
            external.stop();
            external = null;
        }
    }

    private static synchronized void useMinecraftTransport() {
        stopExternal();
        CommunicationManager.setClientTransport(MC);
    }

    /** Restores the Minecraft transport and closes any relay connection. Call on client disconnect. */
    public static void onDisconnect() {
        useMinecraftTransport();
    }
}
