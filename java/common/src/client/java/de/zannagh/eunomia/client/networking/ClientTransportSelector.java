package de.zannagh.eunomia.client.networking;

import de.zannagh.eunomia.Eunomia;
import de.zannagh.eunomia.clients.ExternalClientTransport;
import de.zannagh.eunomia.clients.ExternalServerClient;
import de.zannagh.eunomia.clients.PingClient;
import de.zannagh.eunomia.clients.RelayConnectionState;
import de.zannagh.eunomia.configuration.EunomiaConfig;
import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.handshake.ServerCapabilities;
import net.minecraft.client.Minecraft;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

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
            // The MC server speaks Eunomia - never use the third-party relay alongside it. Sends parked
            // behind the gate flush to the Minecraft transport on this (present) resolution.
            useMinecraftTransport();
            return;
        }
        EunomiaConfig config = Eunomia.getConfig();
        if (!config.externalFallbackEnabled() || !config.hasExternalServerAddress()) {
            // Server is not Eunomia and the player has not opted into the relay: no destination.
            abandonFallback();
            return;
        }
        String scope = LocalClientIdentity.currentServerScope(client);
        var playerId = LocalClientIdentity.localPlayerId(client);
        if (scope == null) {
            abandonFallback();
            return;
        }
        String rawName = LocalClientIdentity.currentServerName(client);
        String name = (rawName == null || rawName.isBlank()) ? scope : rawName;
        String address = config.externalServerAddress();
        // Reachability probe blocks, so decide off the client thread. Until it lands, gated sends stay
        // parked (an absent resolution does not drop them) so a config sent on join reaches the relay.
        CompletableFuture.runAsync(() -> {
            if (!PingClient.isReachable(address)) {
                Eunomia.LOGGER.info("External relay {} not reachable; staying on the Minecraft transport", address);
                abandonFallback();
                return;
            }
            startExternal(address, scope, name, playerId);
        }).whenComplete((ignored, error) -> {
            // The probe throwing (rather than returning false) would otherwise leave the gate parked for
            // the rest of the connection, silently swallowing the join-time sends it is holding.
            if (error != null) {
                Eunomia.LOGGER.warn("External relay probe for {} failed; staying on the Minecraft transport", address, error);
                abandonFallback();
            }
        });
    }

    private static synchronized void startExternal(String address, String scope, String name, UUID playerId) {
        stopExternal();
        // The relay 409s any REST send without a live WebSocket session for this identity/scope, and nothing
        // retries a 409 - so the gate must not treat the relay as a destination until its socket is open.
        // A previous activation may still be in effect after a reconnect; park until this one reports OPEN.
        CommunicationManager.setExternalTransportActive(false);
        // On a hard block (HTTP 403 / WS 1008) the relay client hands control back here to restore vanilla
        // transport for the rest of the session; abandonFallback is idempotent and thread-safe.
        // The state listener needs to identify its own client, which does not exist yet when it is built.
        AtomicReference<ExternalServerClient> self = new AtomicReference<>();
        ExternalServerClient client = new ExternalServerClient(
                address, scope, name, playerId, Eunomia.LOGGER,
                ClientTransportSelector::abandonFallback,
                state -> onRelayConnectionState(self.get(), state));
        self.set(client);
        external = client;
        // Installed up front so the socket-open callback has somewhere to deliver to; the gate, not the
        // transport, is what decides whether serverbound packets may leave yet.
        CommunicationManager.setClientTransport(new ExternalClientTransport(client));
        client.start();
        Eunomia.LOGGER.info("Routing Eunomia through external relay {} (scope {})", address, scope);
    }

    /**
     * Couples the send gate to the relay's actual socket state instead of to "we asked it to connect".
     * OPEN flushes anything parked on join; RECONNECTING re-parks so sends wait for the socket to return
     * rather than being 409'd away; UNAVAILABLE (the socket never came up at all) releases the parked queue
     * so a relay that answers /health but never opens its WebSocket cannot hold packets forever.
     */
    private static void onRelayConnectionState(ExternalServerClient client, RelayConnectionState state) {
        if (client != external) {
            // A stale client from a previous connection; it no longer owns the transport.
            return;
        }
        switch (state) {
            case OPEN -> CommunicationManager.setExternalTransportActive(true);
            case RECONNECTING -> CommunicationManager.setExternalTransportActive(false);
            case UNAVAILABLE -> {
                Eunomia.LOGGER.info("External relay WebSocket never opened; releasing parked sends until it does");
                CommunicationManager.concludeNoRelay();
            }
        }
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

    /**
     * Restore the Minecraft transport AND tell the gate the fallback concluded with no relay, so sends parked
     * on join are dropped rather than lingering. For the "server is not Eunomia and no usable relay" outcomes:
     * not opted in, unreachable, or hard-blocked by the relay.
     */
    private static synchronized void abandonFallback() {
        useMinecraftTransport();
        CommunicationManager.concludeNoRelay();
    }

    /** Restores the Minecraft transport and closes any relay connection. Call on client disconnect. */
    public static void onDisconnect() {
        useMinecraftTransport();
    }
}
