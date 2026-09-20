package de.zannagh.eunomia.networking.comms;

import de.zannagh.eunomia.networking.handshake.HandshakePackets;
import de.zannagh.eunomia.networking.packets.PacketType;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Server-to-client send gate: the exact mirror of {@link ClientSendGate}, one instance of state per
 * connected player.
 *
 * <p><b>Why this exists.</b> A server running a Eunomia-based mod used to push its clientbound packets to
 * every player unconditionally. A player still on a pre-Eunomia build of that same mod receives Eunomia's
 * framing, their shipped decoder reads the gzip magic {@code 1f 8b 08 00} as a 529-megabyte length, throws,
 * and <em>that client is disconnected</em>. Already-shipped clients cannot be fixed, so the only lever left
 * is not sending to them - which means the server has to know, per player, whether the client speaks
 * Eunomia at all.</p>
 *
 * <p><b>Park, never drop-on-unknown.</b> The answer arrives asynchronously (the client's HELLO), while
 * consumers push their join-time state from a join listener that routinely runs first. A gate that dropped
 * whatever it could not yet vouch for would therefore silently break <em>good</em> clients - the precise
 * failure documented at {@link CommunicationManager#beginClientConnection()}, where a packet was parked and
 * then thrown away microseconds later. So an unresolved player's sends are held, in submission order, and
 * released the moment their HELLO lands. Only the closing of the probe window turns a hold into a drop.</p>
 *
 * <p><b>The bypass set is one channel wide.</b> {@link HandshakePackets#HELLO_ACK} is the packet that
 * <em>resolves</em> the capability; gating it on the capability would deadlock every connection. It is safe
 * to exempt precisely because it is only ever sent as the reply to a HELLO, so a client that cannot decode
 * it is a client that never triggers it. Nothing else is exempt, and there is deliberately no general
 * "send unsafely" entry point: every other clientbound channel carries the framing that does the damage.</p>
 */
final class ServerSendGate {

    /** Default probe window. Generous: a slow client joining a laggy server must not be written off. */
    static final long DEFAULT_TIMEOUT_MILLIS = 15_000L;

    /** Channels exempt from the gate. See the class javadoc - this is not meant to grow. */
    private static final Set<String> BYPASS_CHANNELS = Set.of(HandshakePackets.HELLO_ACK.channelKey());

    private static final ConcurrentHashMap<UUID, PlayerSendGate> GATES = new ConcurrentHashMap<>();

    private static volatile long timeoutMillis = DEFAULT_TIMEOUT_MILLIS;

    private ServerSendGate() {
    }

    /**
     * Routes one clientbound send for {@code playerId}: straight through if the channel is exempt or the
     * client is known to speak Eunomia, dropped if it is known not to, parked otherwise.
     *
     * @param dispatch the transport call itself, so this class stays free of any transport knowledge and a
     *                 parked send re-resolves the current transport when it finally runs
     */
    static void send(UUID playerId, PacketType<?> type, Runnable dispatch) {
        if (BYPASS_CHANNELS.contains(type.channelKey())) {
            dispatch.run();
            return;
        }
        PlayerSendGate gate = GATES.computeIfAbsent(playerId, id -> new PlayerSendGate());
        if (gate.submit(dispatch, type.channelKey())) {
            armTimeout(playerId, gate);
        }
    }

    /** Records that {@code playerId}'s client sent a HELLO, and flushes everything parked for them. */
    static void markCapable(UUID playerId) {
        GATES.computeIfAbsent(playerId, id -> new PlayerSendGate()).markPresent();
    }

    /**
     * Closes the probe window for {@code playerId}: drops what is parked and makes later sends drop on
     * arrival. A no-op if their HELLO already landed - a resolved player is never un-resolved.
     */
    static void markIncapable(UUID playerId) {
        GATES.computeIfAbsent(playerId, id -> new PlayerSendGate()).markAbsent(playerId);
    }

    /** The current answer for {@code playerId}; {@link ClientCapability#UNKNOWN} for anyone untracked. */
    static ClientCapability capability(UUID playerId) {
        PlayerSendGate gate = GATES.get(playerId);
        return gate == null ? ClientCapability.UNKNOWN : gate.capability();
    }

    /**
     * Forgets everything held for {@code playerId}. Call from the platform's disconnect hook.
     *
     * <p>This is not housekeeping, it is the feature: a per-player map the server never prunes is a leak
     * that grows with every join for the lifetime of the process, and this repo has been bitten by exactly
     * that before. Dropping the entry also disarms the player's pending timeout task by construction - the
     * task is identity-checked against the entry it was armed for, so it finds nothing and does nothing.</p>
     */
    static void forget(UUID playerId) {
        GATES.remove(playerId);
    }

    /** Drops all per-player state. Server shutdown, and {@link CommunicationManager#resetForTesting()}. */
    static void reset() {
        GATES.clear();
        timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
    }

    /** Shortens the probe window so a test can exercise the automatic timeout without sleeping for it. */
    static void setTimeoutMillis(long millis) {
        timeoutMillis = millis;
    }

    /**
     * Schedules the close of {@code expected}'s probe window.
     *
     * <p>The task holds the gate instance it was armed for, not just the id, and re-checks that the map
     * still maps the player to that same instance. Without that check a timer armed for a player's first
     * connection would fire into their <em>second</em> one after a quick rejoin and write off a client that
     * had done nothing wrong; and a timer left over from a disconnected player would recreate the very map
     * entry the disconnect hook just removed.</p>
     */
    private static void armTimeout(UUID playerId, PlayerSendGate expected) {
        CompletableFuture.delayedExecutor(timeoutMillis, TimeUnit.MILLISECONDS).execute(() -> {
            if (GATES.get(playerId) == expected) {
                expected.markAbsent(playerId);
            }
        });
    }
}
