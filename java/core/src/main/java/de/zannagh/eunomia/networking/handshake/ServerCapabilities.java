package de.zannagh.eunomia.networking.handshake;

import de.zannagh.eunomia.networking.packets.PacketType;

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

    private final CopyOnWriteArrayList<Consumer<ServerCapabilities>> listeners = new CopyOnWriteArrayList<>();

    private volatile boolean resolved;
    private volatile boolean present;
    private volatile int protocolVersion;
    private volatile Set<String> receiverChannels = Set.of();

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
            listener.accept(this);
        }
    }

    // ── Internal transitions (driven by CommunicationManager) ───────────────────────────────────

    /** Called when a HELLO_ACK arrives. An ACK always wins over a prior timeout. */
    public synchronized void markPresent(int protocol, Collection<String> channels) {
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
        this.resolved = true;
        fire();
    }

    /** Clears per-connection state (keeps listeners) so the next join probes afresh. */
    public synchronized void reset() {
        this.resolved = false;
        this.present = false;
        this.protocolVersion = 0;
        this.receiverChannels = Set.of();
    }

    private void fire() {
        for (Consumer<ServerCapabilities> listener : listeners) {
            listener.accept(this);
        }
    }
}
