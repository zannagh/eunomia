package de.zannagh.eunomia.networking;

import de.zannagh.eunomia.networking.handshake.ServerCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Client-to-server send gate backing {@link SendOptions#AFTER_SUCCESSFUL_HANDSHAKE} and
 * {@link SendOptions#IF_SERVER_SUPPORTS}. Carried over from the Armor Hider mod's {@code ClientSendGate}
 * and folded into the library so every consumer gets safe gating without hand-rolling it.
 *
 * <p>Eunomia does not blindly emit custom payloads: sending an unknown channel to a vanilla (or
 * otherwise non-Eunomia) server can get the client disconnected. So a gated packet is held until
 * {@link CommunicationManager#serverCapabilities() serverCapabilities()} resolves. Once it resolves
 * {@link ServerCapabilities#isPresent() present} the queue is flushed in submission order; if it
 * resolves absent - the server does not run Eunomia, or the client's probe timed out
 * ({@link CommunicationManager#markServerProbeTimedOut()}) - the queue is dropped for the rest of the
 * connection. {@link #reset()} clears this state so a reconnect always re-tests the new server.</p>
 *
 * <p>Resolution is entirely event-driven: a single persistent
 * {@link ServerCapabilities#onResolved(java.util.function.Consumer) onResolved} listener flushes or
 * drops the queue. There is no background timer here because Eunomia's own client wiring already
 * concludes the probe (an ACK, or {@code markServerProbeTimedOut} after a timeout), and both outcomes
 * fire that listener. A queue can therefore only linger until the next resolution, {@link #reset()},
 * or the next {@link CommunicationManager#beginServerProbe() probe} - never indefinitely across a
 * connection's lifetime.</p>
 *
 * <p>All state is guarded by {@link #MONITOR}, and the flush runs while holding it, so a later
 * fast-path send can never overtake packets that were queued before the probe resolved.</p>
 */
final class ClientSendGate {

    private static final Logger LOGGER = LoggerFactory.getLogger("eunomia-net");

    /** Guards all mutable gate state below. */
    private static final Object MONITOR = new Object();
    /** Sends queued while the capability decision is still pending, flushed in submission order. */
    private static final Deque<Runnable> pending = new ArrayDeque<>();
    /** Whether the persistent {@code onResolved} listener has been attached (attach once, ever). */
    private static boolean installed = false;

    private ClientSendGate() {
    }

    /**
     * Sends {@code data} on {@code type} honoring the capability gate: immediately if the server is
     * already known to be eligible, never if it is known to be ineligible, or queued (to flush when the
     * probe resolves) while the decision is still pending.
     *
     * @param requireChannelSupport when true, the server must also declare a receiver for this exact
     *                              channel (the {@link SendOptions#IF_SERVER_SUPPORTS} contract), not
     *                              merely run Eunomia.
     */
    static <T> void send(PacketType<T> type, T data, boolean requireChannelSupport) {
        synchronized (MONITOR) {
            ensureInstalled();
            ServerCapabilities caps = CommunicationManager.serverCapabilities();
            if (caps.isResolved()) {
                dispatchOrDrop(type, data, requireChannelSupport, caps);
                return;
            }
            // Undecided: queue, re-checking eligibility when the probe actually resolves.
            pending.add(() -> dispatchOrDrop(type, data, requireChannelSupport,
                    CommunicationManager.serverCapabilities()));
        }
    }

    /** Forget the current connection's queued sends so a reconnect never reuses a prior decision. */
    static void reset() {
        synchronized (MONITOR) {
            pending.clear();
        }
    }

    /** Sends {@code data} now if {@code caps} deems it eligible, otherwise drops it. Call under {@link #MONITOR}. */
    private static <T> void dispatchOrDrop(PacketType<T> type, T data, boolean requireChannelSupport,
                                           ServerCapabilities caps) {
        if (!caps.isPresent()) {
            LOGGER.debug("Suppressing serverbound {}: server does not run Eunomia.", type.channelKey());
            return;
        }
        if (requireChannelSupport && !caps.supports(type)) {
            LOGGER.debug("Suppressing serverbound {}: server has no receiver for this channel.", type.channelKey());
            return;
        }
        CommunicationManager.sendToServerNow(type, data);
    }

    /** Attaches the persistent flush listener the first time a gated send is made. Call under {@link #MONITOR}. */
    private static void ensureInstalled() {
        if (installed) {
            return;
        }
        installed = true;
        // Listeners persist across reconnects and fire again for each new resolution (present or absent),
        // so this single registration both flushes on ACK and drops on timeout, connection after connection.
        CommunicationManager.serverCapabilities().onResolved(caps -> flush());
    }

    /** Drains the pending queue when the probe resolves: each send re-checks its own eligibility. */
    private static void flush() {
        synchronized (MONITOR) {
            if (pending.isEmpty()) {
                return;
            }
            // Snapshot then clear before running: a queued send that itself enqueues (it should not)
            // cannot corrupt the iteration, and the flush stays strictly ordered under the lock.
            List<Runnable> batch = new ArrayList<>(pending);
            pending.clear();
            for (Runnable action : batch) {
                action.run();
            }
        }
    }
}
