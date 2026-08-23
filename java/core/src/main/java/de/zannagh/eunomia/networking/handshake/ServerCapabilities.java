package de.zannagh.eunomia.networking.handshake;

import de.zannagh.eunomia.networking.packets.PacketType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Client-side view of the server Eunomia handshake. Query it to decide, per connection, whether to
 * talk to the vanilla-hosted server at all or fall back to a custom communications server:
 *
 * <pre>{@code
 * CommunicationManager.serverCapabilities().onResolved(caps -> {
 *     if (!caps.supports(MyPackets.SYNC)) {
 *         // The MC server this client joined has no receiver for our packet - offer the HTTP relay.
 *     }
 * });
 * }</pre>
 *
 * <p>"Resolved" means the probe concluded either way: an ACK arrived ({@link #isPresent()} true, with
 * the server's {@link #receiverChannels()} known), or the probe timed out with no answer
 * ({@link #isPresent()} false - the server does not run Eunomia).</p>
 */
public final class ServerCapabilities {

    private static final Logger LOGGER = LoggerFactory.getLogger("eunomia-net");

    private final CopyOnWriteArrayList<Consumer<ServerCapabilities>> listeners = new CopyOnWriteArrayList<>();

    private volatile boolean resolved;
    private volatile boolean present;
    private volatile int protocolVersion;
    private volatile Set<String> receiverChannels = Set.of();
    private volatile ServerSyncPolicy syncPolicy = ServerSyncPolicy.UNKNOWN;

    /** Whether the probe has concluded (ACK received or timed out). */
    public boolean isResolved() {
        return resolved;
    }

    /** Whether the server runs Eunomia. Only meaningful once {@link #isResolved()}. */
    public boolean isPresent() {
        return present;
    }

    public int protocolVersion() {
        return protocolVersion;
    }

    /**
     * The external-transport policy this server advertised in its ACK - rung two of the effective-settings
     * precedence chain. This is the client-side holder the settings resolver reads: it lives here rather than in
     * a separate singleton because it is per-connection state with exactly the same lifecycle as the rest of the
     * capability view (cleared by {@link #reset()} on every join, replaced by every ACK).
     * <p>
     * Always non-null; {@link ServerSyncPolicy#UNKNOWN} until an ACK carrying a policy arrives, and for the whole
     * connection when the server speaks protocol 1 or advertises nothing.
     */
    public ServerSyncPolicy syncPolicy() {
        return syncPolicy;
    }

    /** The channels the server has a serverbound handler for (empty unless {@link #isPresent()}). */
    public Set<String> receiverChannels() {
        return receiverChannels;
    }

    /** Whether the server can receive the given channel key (i.e. has a handler for it). */
    public boolean supports(String channelKey) {
        return present && receiverChannels.contains(channelKey);
    }

    /** Whether the server can receive this packet type. */
    public boolean supports(PacketType<?> type) {
        return supports(type.channelKey());
    }

    /**
     * Registers a callback fired once the probe resolves. If it has already resolved, the callback
     * runs immediately. Listeners persist across reconnects and fire again for each new resolution.
     */
    public void onResolved(Consumer<ServerCapabilities> listener) {
        listeners.add(listener);
        if (resolved) {
            // Isolated for the same reason fire() is: a late registration that throws must not abort the
            // registrant's remaining client initialization (which may still have the send gate to wire up).
            deliver(listener);
        }
    }

    // ── Internal transitions (driven by CommunicationManager) ───────────────────────────────────

    /** Called when a HELLO_ACK arrives with no advertised policy (e.g. a protocol-1 server). */
    public synchronized void markPresent(int protocol, Collection<String> channels) {
        markPresent(protocol, channels, ServerSyncPolicy.UNKNOWN);
    }

    /** Called when a HELLO_ACK arrives. An ACK always wins over a prior timeout. */
    public synchronized void markPresent(int protocol, Collection<String> channels, ServerSyncPolicy policy) {
        this.syncPolicy = policy == null ? ServerSyncPolicy.UNKNOWN : policy;
        this.protocolVersion = protocol;
        this.receiverChannels = Set.copyOf(channels);
        this.present = true;
        this.resolved = true;
        fire();
    }

    /** Called when the probe times out with no ACK. No-op if already resolved (e.g. a late ACK won). */
    public synchronized void markAbsentIfUnresolved() {
        if (resolved) {
            return;
        }
        this.present = false;
        this.receiverChannels = Set.of();
        this.syncPolicy = ServerSyncPolicy.UNKNOWN;
        this.resolved = true;
        fire();
    }

    /** Clears per-connection state (keeps listeners) so the next join probes afresh. */
    public synchronized void reset() {
        this.resolved = false;
        this.present = false;
        this.protocolVersion = 0;
        this.receiverChannels = Set.of();
        this.syncPolicy = ServerSyncPolicy.UNKNOWN;
    }

    /**
     * Notifies every registered listener of the resolution, each one isolated from the others.
     *
     * <p>The isolation is not defensive politeness, it is a correctness requirement. The listeners on this list
     * are not peers that merely observe: one of them is the send gate, whose whole job is to decide whether
     * packets parked on join may leave. Another - the client transport selector - dereferences the running
     * Minecraft client, and a third raises diagnostic toasts. Without a per-listener guard the first one to
     * throw would abort the loop, and every listener behind it would simply never run: gated join-time sends
     * would park forever, silently, for the entire connection, with nothing on screen or in the log tying the
     * missing packets to the unrelated listener that blew up. Registration order is whatever the client
     * initializers happened to do, so "the important one first" is not a property anything can rely on.</p>
     *
     * <p>Mirrors the same isolation {@code ClientConnectionEvents} and {@link
     * de.zannagh.eunomia.networking.comms.CommunicationManager} already apply to their own handler fan-outs.</p>
     */
    private void fire() {
        for (Consumer<ServerCapabilities> listener : listeners) {
            deliver(listener);
        }
    }

    /** Hands the current view to one listener, absorbing whatever it throws. */
    private void deliver(Consumer<ServerCapabilities> listener) {
        try {
            listener.accept(this);
        } catch (Exception e) {
            LOGGER.error("Server-capability listener failed; continuing with the remaining listeners", e);
        }
    }
}
