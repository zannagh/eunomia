package de.zannagh.eunomia.client.networking;

import de.zannagh.eunomia.Eunomia;
import de.zannagh.eunomia.client.toast.diagnostics.SyncDiagnosticToasts;
import de.zannagh.eunomia.clients.ExternalClientTransport;
import de.zannagh.eunomia.clients.ExternalServerClient;
import de.zannagh.eunomia.clients.PingClient;
import de.zannagh.eunomia.clients.RelayConnectionState;
import de.zannagh.eunomia.configuration.EunomiaSyncSettings;
import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.handshake.ServerCapabilities;
import net.minecraft.client.Minecraft;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Chooses the client send/receive path each time the capability probe resolves. Every setting it consults is the
 * <em>effective</em> one from {@link EunomiaSyncSettings} (player override, then the server's advertised policy,
 * then the consuming mod's default, then the framework default) - never a raw config field.
 *
 * <p>The rule:</p>
 * <ul>
 *   <li>the joined Minecraft server does not run Eunomia: swap to the external relay
 *       ({@link ExternalServerClient}) scoped to this server, but <em>only</em> if the fallback is enabled, an
 *       address is configured and that relay is reachable;</li>
 *   <li>the server does run Eunomia: use the Minecraft transport - unless {@code preferExternalTransport} is
 *       effectively on and a reachable relay exists, in which case the relay wins even here (the relay, not the
 *       game server, then owns the durable state).</li>
 * </ul>
 *
 * <p>A failed reachability probe never leaves the client without a transport: it always lands back on the
 * Minecraft transport. When the server runs Eunomia that is a working destination, so the send gate must not be
 * told "no relay, drop everything" - it already flushed to the Minecraft transport on the present resolution.
 * Only the server-is-not-Eunomia paths conclude the fallback with {@link CommunicationManager#concludeNoRelay()}.
 * A disconnect always restores the Minecraft transport and tears down any relay connection.</p>
 */
public final class ClientTransportSelector {

    private static final McClientTransport MC = new McClientTransport();

    private static volatile ExternalServerClient external;

    /**
     * Whether the connection the current relay was started for also has an Eunomia-speaking MC server (the
     * {@code preferExternalTransport} case). Decides whether losing the relay is a dead end or merely a demotion
     * back to a perfectly usable in-game transport.
     */
    private static volatile boolean mcServerSpeaksEunomia;

    private ClientTransportSelector() {
    }

    /** Installs the Minecraft transport as the default and hooks the capability resolution to re-decide on join. */
    public static void init() {
        CommunicationManager.setClientTransport(MC);
        // Tell the send gate that a relay can outrank an Eunomia-speaking server here, so a "present"
        // resolution parks instead of flushing to the game connection this preference exists to bypass.
        // A predicate rather than a notification: the gate and this selector are both capability listeners,
        // and neither may depend on running before the other.
        CommunicationManager.setExternalTransportPreferred(ClientTransportSelector::relayMayOutrankMinecraft);
        CommunicationManager.serverCapabilities().onResolved(ClientTransportSelector::onCapabilityResolved);
    }

    /**
     * Whether this connection may end up on the relay even though the joined server runs Eunomia. Exactly the
     * condition {@link #onCapabilityResolved} uses to skip its "the game server wins" early return, kept as one
     * expression so the gate can never be parking for a decision the selector is not actually going to make.
     */
    private static boolean relayMayOutrankMinecraft() {
        return EunomiaSyncSettings.externalRelayUsable() && EunomiaSyncSettings.preferExternalTransport();
    }

    private static void onCapabilityResolved(ServerCapabilities capabilities) {
        Minecraft client = Minecraft.getInstance();
        boolean serverSpeaksEunomia = capabilities.isPresent();
        if (!relayMayOutrankMinecraft() && serverSpeaksEunomia) {
            // The MC server speaks Eunomia and nobody asked for the relay to win over it. Sends parked behind
            // the gate flush to the Minecraft transport on this (present) resolution.
            settleOnMinecraft();
            return;
        }
        if (!EunomiaSyncSettings.externalRelayUsable()) {
            // Server is not Eunomia and the relay is not enabled/addressed: no destination.
            abandonFallback();
            return;
        }
        if (client == null) {
            // No running client (a headless harness, or a very early resolution). Nothing to scope a relay to,
            // and dereferencing it here would throw straight into the capability fan-out - which is precisely
            // how the send gate used to end up starved for the whole connection.
            fallBackToMinecraft(serverSpeaksEunomia);
            return;
        }
        String scope = LocalClientIdentity.currentServerScope(client);
        var playerId = LocalClientIdentity.localPlayerId(client);
        if (scope == null) {
            fallBackToMinecraft(serverSpeaksEunomia);
            return;
        }
        String rawName = LocalClientIdentity.currentServerName(client);
        String name = (rawName == null || rawName.isBlank()) ? scope : rawName;
        String address = EunomiaSyncSettings.externalServerAddress();
        // Reachability probe blocks, so decide off the client thread. Until it lands, gated sends stay
        // parked (an absent resolution does not drop them) so a config sent on join reaches the relay.
        CompletableFuture.runAsync(() -> {
            if (!PingClient.isReachable(address)) {
                Eunomia.LOGGER.info("External relay {} not reachable; staying on the Minecraft transport", address);
                fallBackToMinecraft(serverSpeaksEunomia);
                // Strictly after the landing above: the gate has already been concluded by the time the
                // diagnostic runs, so nothing it does (or fails at) can leave parked sends hanging.
                SyncDiagnosticToasts.relayUnreachable(address, serverSpeaksEunomia);
                return;
            }
            startExternal(address, scope, name, playerId, serverSpeaksEunomia);
        }).whenComplete((ignored, error) -> {
            // The probe throwing (rather than returning false) would otherwise leave the gate parked for
            // the rest of the connection, silently swallowing the join-time sends it is holding.
            if (error != null) {
                Eunomia.LOGGER.warn("External relay probe for {} failed; staying on the Minecraft transport", address, error);
                fallBackToMinecraft(serverSpeaksEunomia);
                // Same ordering rule as the probe-returned-false path: conclude the gate, then diagnose.
                SyncDiagnosticToasts.relayUnreachable(address, serverSpeaksEunomia);
            }
        });
    }

    /**
     * The "relay did not work out" landing. With an Eunomia-speaking MC server the Minecraft transport is a real
     * destination, so the gate is told to settle on it and flush; without one, there is genuinely nowhere to
     * send and the parked queue has to be dropped. Concluding "no relay" in the first case would throw away
     * packets that had somewhere to go all along.
     */
    private static void fallBackToMinecraft(boolean serverSpeaksEunomia) {
        if (serverSpeaksEunomia) {
            settleOnMinecraft();
            return;
        }
        abandonFallback();
    }

    /**
     * Restore the Minecraft transport AND tell the gate that is where this connection's traffic goes. The
     * explicit conclusion matters under {@code preferExternalTransport}: there the gate parks the "present"
     * resolution waiting for the relay decision, so nothing else would ever release the parked queue.
     */
    private static synchronized void settleOnMinecraft() {
        useMinecraftTransport();
        CommunicationManager.concludeMinecraftTransport();
    }

    /**
     * The relay handed control back for good (hard block, or an API version it will not serve). Which landing
     * that is depends on whether the joined server is itself an Eunomia server, so it is read before
     * {@link #useMinecraftTransport()} clears it.
     */
    private static synchronized void onRelayGaveUp() {
        fallBackToMinecraft(mcServerSpeaksEunomia);
    }

    private static synchronized void startExternal(String address, String scope, String name, UUID playerId,
                                                   boolean serverSpeaksEunomia) {
        stopExternal();
        mcServerSpeaksEunomia = serverSpeaksEunomia;
        // The relay 409s any REST send without a live WebSocket session for this identity/scope, and nothing
        // retries a 409 - so the gate must not treat the relay as a destination until its socket is open.
        // A previous activation may still be in effect after a reconnect; park until this one reports OPEN.
        CommunicationManager.setExternalTransportActive(false);
        // On a hard block (HTTP 403 / WS 1008), an unsupported API version, or a reconnect budget that runs
        // out, the relay client hands control back here to restore the vanilla transport for the rest of the
        // session; onRelayGaveUp is idempotent and thread-safe.
        // The state listener needs to identify its own client, which does not exist yet when it is built.
        AtomicReference<ExternalServerClient> self = new AtomicReference<>();
        ExternalServerClient client = new ExternalServerClient(
                address, scope, name, playerId, Eunomia.LOGGER,
                ClientTransportSelector::onRelayGaveUp,
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
                if (mcServerSpeaksEunomia) {
                    // We only took the relay because it was preferred; the MC server is still there, so the
                    // parked queue has a real destination - settle on it rather than concluding "nowhere".
                    Eunomia.LOGGER.info("External relay WebSocket never opened; returning to the Minecraft transport");
                    settleOnMinecraft();
                    return;
                }
                Eunomia.LOGGER.info("External relay WebSocket never opened; releasing parked sends until it does");
                CommunicationManager.concludeNoRelay();
                // A relay that answers /health but never opens its socket is unreachable as far as the player
                // is concerned, and this branch is the one where no in-game transport is left to fall back on.
                // Raised only after the gate has been concluded on the line above.
                SyncDiagnosticToasts.relayUnreachable(EunomiaSyncSettings.externalServerAddress(), false);
            }
        }
    }

    private static synchronized void stopExternal() {
        mcServerSpeaksEunomia = false;
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
