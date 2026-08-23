package de.zannagh.eunomia.networking.comms;

import de.zannagh.eunomia.networking.handshake.ServerCapabilities;
import de.zannagh.eunomia.networking.packets.PacketType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Client-to-server send gate backing {@link SendOptions#AFTER_SUCCESSFUL_HANDSHAKE} and
 * {@link SendOptions#IF_SERVER_SUPPORTS}. Carried over from the Armor Hider mod's {@code ClientSendGate}
 * and folded into the library so every consumer gets safe gating without hand-rolling it.
 *
 * <p>Eunomia does not blindly emit custom payloads: sending an unknown channel to a vanilla (or
 * otherwise non-Eunomia) server can get the client disconnected. So a gated packet is held until
 * {@link CommunicationManager#serverCapabilities() serverCapabilities()} resolves and, with it, until the
 * client transport selector has settled which of the two possible destinations actually owns this
 * connection.</p>
 *
 * <p><b>The gate has exactly four states</b>, and every one of them is entered by an explicit call, never by
 * timing out or by inference:</p>
 * <ul>
 *   <li><em>undecided</em> - nothing is known yet, sends park;</li>
 *   <li><em>Minecraft</em> ({@link #concludeMinecraftTransport()}, or a "present" resolution while no relay
 *       can outrank it) - flush and keep sending over the game connection;</li>
 *   <li><em>relay</em> ({@link #setExternalTransportActive(boolean) setExternalTransportActive(true)}) - flush
 *       and keep sending over the relay, regardless of the (possibly absent) Minecraft capability;</li>
 *   <li><em>nowhere</em> ({@link #concludeNoRelay()}) - the server is not Eunomia and no relay is usable, so
 *       drop the queue and every later gated send for the rest of the connection.</li>
 * </ul>
 *
 * <p><b>Why a "present" resolution is not automatically a decision.</b> When {@code preferExternalTransport}
 * is effectively on and a relay is configured, the selector routes through the relay <em>even though</em> the
 * joined server runs Eunomia - the relay, not the game server, then owns the durable state. That decision is
 * asynchronous (reachability probe, then a WebSocket handshake), so flushing on the "present" resolution would
 * put the join-time payload on the game connection, which is exactly the destination {@code
 * preferExternalTransport} exists to avoid; and dispatching later sends the moment the relay transport is
 * installed but before its socket is open earns an HTTP 409 from the relay, which nothing retries. Hence
 * {@link #setExternalTransportPreferred(BooleanSupplier)}: a <em>query</em>, not an event, so it holds no
 * matter which order the capability listeners happen to run in. While it answers true, a "present" resolution
 * parks instead of flushing, and only an explicit conclusion from the selector releases the queue.</p>
 *
 * <p>Deactivation (the socket dropped, a reconnect is pending) re-parks the queue rather than dropping it, so a
 * config sent on join survives the gap. {@link #reset()} clears this state so a reconnect always re-tests the
 * new server. All state is guarded by {@link #MONITOR}, and every flush runs while holding it, so a later
 * fast-path send can never overtake packets queued before the decision landed.</p>
 */
final class ClientSendGate {

    private static final Logger LOGGER = LoggerFactory.getLogger("eunomia-net");

    /**
     * Hard ceiling on parked sends. The queue is bounded because parking is open-ended by design: a relay that
     * flaps stays in {@code RECONNECTING} indefinitely, and every gated send in that window is retained. A mod
     * that syncs per tick would otherwise grow this queue without limit for as long as the socket is down.
     * Dropping the oldest is the right end to drop from: the queue is mostly per-key state updates, so the
     * newest entry is the one worth keeping.
     */
    static final int MAX_PENDING = 512;

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
    /** Whether the Minecraft transport has been settled as this connection's destination - deliver over it. */
    private static boolean minecraftConcluded = false;
    /**
     * Whether a relay may still outrank an Eunomia-speaking Minecraft server on this connection. Installed by
     * the client transport selector; {@code false} everywhere the selector does not exist (servers, tests).
     */
    private static volatile BooleanSupplier externalTransportPreferred = () -> false;

    private ClientSendGate() {
    }

    /**
     * Attaches the persistent resolution listener. Called once, eagerly, from {@link CommunicationManager}'s
     * static initializer.
     *
     * <p>Eagerness is the whole point. This used to be lazy - attached on the first gated send - which made the
     * gate's listener the <em>last</em> one on the capability list on every client, behind the transport
     * selector and the diagnostic toasts. Combined with a fan-out that did not isolate its listeners, any throw
     * upstream starved the gate and parked every join-time send for the connection. Installing here makes the
     * gate the first listener registered, which is also the right order on its merits: the queue's fate should
     * be decided before anything cosmetic gets a chance to run.</p>
     */
    static void install(ServerCapabilities capabilities) {
        synchronized (MONITOR) {
            if (installed) {
                return;
            }
            installed = true;
        }
        capabilities.onResolved(ClientSendGate::onCapabilitiesResolved);
    }

    /**
     * Installs the predicate that tells the gate whether the relay may still win this connection even though
     * the Minecraft server speaks Eunomia. Pass {@code null} to restore the default ("it may not").
     */
    static void setExternalTransportPreferred(BooleanSupplier preferred) {
        externalTransportPreferred = preferred == null ? () -> false : preferred;
    }

    /**
     * Sends {@code data} on {@code type} honoring the gate: immediately if a destination is already settled,
     * dropped if the connection concluded there is no eligible destination, or queued (to flush when the
     * decision lands) while it is still pending.
     *
     * @param requireChannelSupport when true, an Eunomia server must also declare a receiver for this exact
     *                              channel (the {@link SendOptions#IF_SERVER_SUPPORTS} contract). Ignored on
     *                              the relay path, which accepts any channel.
     */
    static <T> void send(PacketType<T> type, T data, boolean requireChannelSupport) {
        synchronized (MONITOR) {
            ServerCapabilities caps = CommunicationManager.serverCapabilities();
            if (settled(caps)) {
                // A destination is decided (relay / Eunomia server), or the connection concluded there is
                // none: dispatchOrDrop resolves which. Either way, do not queue.
                dispatchOrDrop(type, data, requireChannelSupport, caps);
                return;
            }
            // Undecided (probe pending, absent while the fallback decision is in flight, or present while the
            // relay may still take the connection off the Minecraft transport): park.
            park(() -> dispatchOrDrop(type, data, requireChannelSupport,
                    CommunicationManager.serverCapabilities()));
        }
    }

    /** Forget the current connection's queued sends and fallback state so a reconnect starts clean. */
    static void reset() {
        synchronized (MONITOR) {
            pending.clear();
            externalActive = false;
            concludedNoRelay = false;
            minecraftConcluded = false;
        }
    }

    /**
     * Marks the external relay transport active (or not) and flushes the parked queue. Called by the client
     * transport selector once the relay's receive socket has actually opened (or dropped). Activating delivers
     * every parked send to the relay; deactivating leaves the queue parked for the pending reconnect.
     */
    static void setExternalTransportActive(boolean active) {
        synchronized (MONITOR) {
            externalActive = active;
            concludedNoRelay = false;
            minecraftConcluded = false;
            if (active) {
                flush();
            }
            // Deactivating deliberately does NOT flush. It means "the relay's socket is down, a reconnect is
            // coming" - flushing there would re-evaluate the queue against an absent Minecraft capability and
            // drop it. The queue stays parked until the socket is back (active again), until the selector
            // hands the connection back with concludeMinecraftTransport(), or until concludeNoRelay() decides
            // there is no destination at all.
        }
    }

    /**
     * Marks the Minecraft transport as this connection's settled destination and flushes the parked queue to
     * it. Two callers: the "present" resolution when no relay can outrank it, and the transport selector when a
     * relay it did try turns out to be unusable while the joined server does speak Eunomia.
     *
     * <p>That second caller is why this exists as its own conclusion rather than being folded into the
     * resolution listener. Under {@code preferExternalTransport} the gate parks a "present" resolution, so
     * something has to release the queue when the relay does not work out - and releasing it as
     * {@link #concludeNoRelay()} would drop packets that have a perfectly good in-game destination.</p>
     */
    static void concludeMinecraftTransport() {
        synchronized (MONITOR) {
            externalActive = false;
            concludedNoRelay = false;
            minecraftConcluded = true;
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
            minecraftConcluded = false;
            concludedNoRelay = true;
            flush();
        }
    }

    /** Reacts to the capability probe concluding. Call from the listener installed by {@link #install}. */
    private static void onCapabilitiesResolved(ServerCapabilities caps) {
        synchronized (MONITOR) {
            if (caps.isPresent() && !relayMayOutrankMinecraft()) {
                // The game server receives Eunomia packets and nothing can take the connection away from it.
                concludeMinecraftTransport();
                return;
            }
            if (externalActive || concludedNoRelay || minecraftConcluded) {
                flush();
            }
            // Otherwise park: either the server is absent and the selector's fallback decision is still in
            // flight, or it is present but the relay may still claim the connection. Both are released by an
            // explicit conclusion from the selector - see the class javadoc for the exhaustive list.
        }
    }

    /** Whether a destination (or the absence of one) is decided. Call under {@link #MONITOR}. */
    private static boolean settled(ServerCapabilities caps) {
        if (externalActive || minecraftConcluded) {
            return true;
        }
        if (concludedNoRelay && caps.isResolved()) {
            return true;
        }
        return caps.isPresent() && !relayMayOutrankMinecraft();
    }

    /** The selector's preference predicate, never allowed to throw into the gate. */
    private static boolean relayMayOutrankMinecraft() {
        try {
            return externalTransportPreferred.getAsBoolean();
        } catch (Exception e) {
            // Failing closed here would park every send forever; failing open only costs the relay preference.
            LOGGER.warn("External-transport preference check failed; treating the Minecraft transport as final", e);
            return false;
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

    /** Queues one send, evicting the oldest once {@link #MAX_PENDING} is reached. Call under {@link #MONITOR}. */
    private static void park(Runnable action) {
        while (pending.size() >= MAX_PENDING) {
            pending.removeFirst();
            LOGGER.warn("Send gate is holding {} packets with no destination decided yet; dropping the oldest. "
                    + "Either the relay never came up or a transport decision was never concluded.", MAX_PENDING);
        }
        pending.add(action);
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
