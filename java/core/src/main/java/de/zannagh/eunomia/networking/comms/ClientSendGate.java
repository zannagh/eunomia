package de.zannagh.eunomia.networking.comms;

import de.zannagh.eunomia.networking.handshake.ServerCapabilities;
import de.zannagh.eunomia.networking.packets.PacketType;
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
 * {@link ServerCapabilities#isPresent() present} the queue is flushed in submission order.</p>
 *
 * <p><b>External relay fallback.</b> When the joined server does <em>not</em> run Eunomia, the client's
 * {@code ClientTransportSelector} may swap the Minecraft transport for the external relay
 * ({@code ExternalClientTransport}). That decision is asynchronous (it needs the capability probe to
 * resolve <em>absent</em> first, then a reachability check), so an absent resolution does NOT drop the
 * queue immediately - the queue is <em>parked</em> until the fallback decision lands:
 * {@link #setExternalTransportActive(boolean) setExternalTransportActive(true)} flushes it to the relay,
 * and {@link #concludeNoRelay()} (no opt-in / unreachable / hard-blocked) drops it. This is what lets a
 * config sent on join actually reach the relay instead of being dropped in the gap between "server is not
 * Eunomia" and "relay is up". Once the relay is active, sends dispatch to it regardless of the (absent)
 * Minecraft capability - the relay is a valid, opted-in destination.</p>
 *
 * <p>{@link #reset()} clears this state so a reconnect always re-tests the new server. All state is
 * guarded by {@link #MONITOR}, and every flush runs while holding it, so a later fast-path send can never
 * overtake packets queued before the decision landed.</p>
 */
final class ClientSendGate {

    private static final Logger LOGGER = LoggerFactory.getLogger("eunomia-net");

    /** Guards all mutable gate state below. */
    private static final Object MONITOR = new Object();
    /** Sends queued while the capability/fallback decision is still pending, flushed in submission order. */
    private static final Deque<Runnable> pending = new ArrayDeque<>();
    /** Whether the persistent {@code onResolved} listener has been attached (attach once, ever). */
    private static boolean installed = false;
    /** Whether the external relay transport is the active send path (deliver regardless of MC capability). */
    private static boolean externalActive = false;
    /** Whether the fallback decision concluded "no relay" (server not Eunomia and no usable relay) - drop. */
    private static boolean concludedNoRelay = false;

    private ClientSendGate() {
    }

    /**
     * Sends {@code data} on {@code type} honoring the gate: immediately if a destination is already known
     * (the server runs Eunomia, or the relay is active), dropped if the connection concluded there is no
     * eligible destination, or queued (to flush when the decision lands) while it is still pending.
     *
     * @param requireChannelSupport when true, an Eunomia server must also declare a receiver for this exact
     *                              channel (the {@link SendOptions#IF_SERVER_SUPPORTS} contract). Ignored on
     *                              the relay path, which accepts any channel.
     */
    static <T> void send(PacketType<T> type, T data, boolean requireChannelSupport) {
        synchronized (MONITOR) {
            ensureInstalled();
            ServerCapabilities caps = CommunicationManager.serverCapabilities();
            if (externalActive || caps.isPresent() || (caps.isResolved() && concludedNoRelay)) {
                // A destination is decided (relay / Eunomia server), or the connection concluded there is
                // none: dispatchOrDrop resolves which. Either way, do not queue.
                dispatchOrDrop(type, data, requireChannelSupport, caps);
                return;
            }
            // Undecided (probe pending, or absent while the fallback decision is still in flight): park.
            pending.add(() -> dispatchOrDrop(type, data, requireChannelSupport,
                    CommunicationManager.serverCapabilities()));
        }
    }

    /** Forget the current connection's queued sends and fallback state so a reconnect starts clean. */
    static void reset() {
        synchronized (MONITOR) {
            pending.clear();
            externalActive = false;
            concludedNoRelay = false;
        }
    }

    /**
     * Marks the external relay transport active (or not) and flushes the parked queue. Called by the client
     * transport selector once it has installed (or torn down) the relay transport. Activating delivers every
     * parked send to the relay; deactivating re-evaluates them (delivered if the server turned out to run
     * Eunomia, dropped otherwise).
     */
    static void setExternalTransportActive(boolean active) {
        synchronized (MONITOR) {
            externalActive = active;
            concludedNoRelay = false;
            flush();
        }
    }

    /**
     * Marks the fallback decision as "no relay" - the server does not run Eunomia and no relay is usable
     * (not opted in, unreachable, or hard-blocked). Drops the parked queue and makes subsequent sends drop
     * immediately for the rest of the connection.
     */
    static void concludeNoRelay() {
        synchronized (MONITOR) {
            externalActive = false;
            concludedNoRelay = true;
            flush();
        }
    }

    /** Sends {@code data} now if a destination is eligible, otherwise drops it. Call under {@link #MONITOR}. */
    private static <T> void dispatchOrDrop(PacketType<T> type, T data, boolean requireChannelSupport,
                                           ServerCapabilities caps) {
        if (externalActive) {
            // The relay is the destination and accepts any channel; the MC capability is irrelevant here.
            CommunicationManager.sendToServerNow(type, data);
            return;
        }
        if (!caps.isPresent()) {
            LOGGER.debug("Suppressing serverbound {}: server does not run Eunomia and no relay is active.",
                    type.channelKey());
            return;
        }
        if (requireChannelSupport && !caps.supports(type)) {
            LOGGER.debug("Suppressing serverbound {}: server has no receiver for this channel.", type.channelKey());
            return;
        }
        CommunicationManager.sendToServerNow(type, data);
    }

    /** Attaches the persistent resolution listener the first time a gated send is made. Call under {@link #MONITOR}. */
    private static void ensureInstalled() {
        if (installed) {
            return;
        }
        installed = true;
        // Deliver on a "present" resolution; on an "absent" resolution, only flush once a destination is
        // decided (relay active) or the connection concluded no relay - otherwise park until the selector's
        // asynchronous fallback decision lands, so a join-time send is not dropped in that window.
        CommunicationManager.serverCapabilities().onResolved(caps -> {
            synchronized (MONITOR) {
                if (caps.isPresent() || externalActive || concludedNoRelay) {
                    flush();
                }
            }
        });
    }

    /** Drains the pending queue: each send re-checks its own eligibility. Call under {@link #MONITOR}. */
    private static void flush() {
        if (pending.isEmpty()) {
            return;
        }
        // Snapshot then clear before running: a queued send that itself enqueues (it should not) cannot
        // corrupt the iteration, and the flush stays strictly ordered under the lock.
        List<Runnable> batch = new ArrayList<>(pending);
        pending.clear();
        for (Runnable action : batch) {
            action.run();
        }
    }
}
