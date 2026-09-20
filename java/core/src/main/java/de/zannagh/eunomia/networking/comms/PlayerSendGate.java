package de.zannagh.eunomia.networking.comms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * One connected player's clientbound send state: the capability answer, and the sends parked while that
 * answer is still outstanding. Owned exclusively by {@link ServerSendGate}, which creates exactly one of
 * these per player and drops it on disconnect.
 *
 * <p>Every method is {@code synchronized} on the instance, and a flush runs <em>while holding that
 * monitor</em> - the same discipline {@link ClientSendGate} uses, and for the same reason: a send racing
 * the resolution must not overtake the packets that were parked before it. Holding the monitor across the
 * transport call is safe because a dispatch that loops back into this player's gate re-enters its own
 * monitor rather than blocking on a foreign one.</p>
 */
final class PlayerSendGate {

    private static final Logger LOGGER = LoggerFactory.getLogger("eunomia-net");

    /**
     * Hard ceiling on sends parked for one player. Parking is open-ended by construction - a client that
     * never answers holds its queue for the whole timeout window - and a consumer that syncs per tick would
     * otherwise grow this without limit. The oldest is the right end to drop: these are overwhelmingly
     * per-key state updates, so the newest carries the truth.
     */
    static final int MAX_PENDING = 256;

    private final Deque<Runnable> pending = new ArrayDeque<>();

    private ClientCapability capability = ClientCapability.UNKNOWN;

    /** Whether the timeout task has already been scheduled, so a second park does not schedule another. */
    private boolean timerArmed;

    /** Whether the "this client never answered" line has been logged. Once per player, not once per packet. */
    private boolean timeoutLogged;

    synchronized ClientCapability capability() {
        return capability;
    }

    /**
     * Dispatches, drops or parks one clientbound send, atomically with respect to the capability answer.
     *
     * <p>The decision and the action are deliberately one operation. Asking {@link #capability()} and then
     * acting on the result outside this monitor would let a send that read UNKNOWN park <em>after</em> the
     * flush that was meant to release it, stranding the packet until the next resolution - which never
     * comes, because a player resolves exactly once per connection.</p>
     *
     * @return true if the caller must now schedule the timeout task (this was the first park for the player)
     */
    synchronized boolean submit(Runnable dispatch, String channelKey) {
        if (capability == ClientCapability.PRESENT) {
            dispatch.run();
            return false;
        }
        if (capability == ClientCapability.ABSENT) {
            LOGGER.debug("Withholding clientbound {}: this client does not speak Eunomia.", channelKey);
            return false;
        }
        return park(dispatch, channelKey);
    }

    /** Queues one send, evicting the oldest at the ceiling. Call under the instance monitor. */
    private boolean park(Runnable dispatch, String channelKey) {
        while (pending.size() >= MAX_PENDING) {
            pending.removeFirst();
            LOGGER.warn("Holding {} clientbound packets for a player who has not answered the capability "
                    + "probe; dropping the oldest (newest queued: {}).", MAX_PENDING, channelKey);
        }
        pending.add(dispatch);
        if (timerArmed) {
            return false;
        }
        timerArmed = true;
        return true;
    }

    /** Records the HELLO and flushes everything parked, in submission order. */
    synchronized void markPresent() {
        capability = ClientCapability.PRESENT;
        flush();
    }

    /**
     * Records that the probe window closed with no HELLO: drop the queue and remember the negative so later
     * sends are dropped on arrival instead of being parked all over again.
     *
     * @param playerId only used for the single log line this produces
     */
    synchronized void markAbsent(UUID playerId) {
        if (capability == ClientCapability.PRESENT) {
            // The HELLO beat the timer. Nothing to do - a resolved player is never un-resolved.
            return;
        }
        capability = ClientCapability.ABSENT;
        int dropped = pending.size();
        pending.clear();
        if (!timeoutLogged) {
            timeoutLogged = true;
            LOGGER.info("Player {} did not answer the Eunomia capability probe; withholding clientbound "
                    + "Eunomia packets from them for this connection ({} already queued were dropped). "
                    + "This is expected for vanilla clients and for clients on a pre-Eunomia build.",
                    playerId, dropped);
        }
    }

    /** Drains the parked queue. Call under the instance monitor. */
    private void flush() {
        if (pending.isEmpty()) {
            return;
        }
        // Snapshot then clear before running, so a dispatch that (it should not) parks again cannot
        // corrupt the iteration.
        List<Runnable> batch = new ArrayList<>(pending);
        pending.clear();
        for (Runnable action : batch) {
            action.run();
        }
    }
}
