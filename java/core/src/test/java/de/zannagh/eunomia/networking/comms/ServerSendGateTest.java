package de.zannagh.eunomia.networking.comms;

import com.google.gson.Gson;
import de.zannagh.eunomia.networking.handshake.ClientHelloPayload;
import de.zannagh.eunomia.networking.handshake.HandshakePackets;
import de.zannagh.eunomia.networking.handshake.ServerHelloPayload;
import de.zannagh.eunomia.networking.packets.PacketType;
import de.zannagh.eunomia.networking.packets.ServerContext;
import de.zannagh.eunomia.networking.serialization.NetworkSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the server-to-client capability gate: a clientbound send parks until the player's HELLO lands,
 * flushes in submission order when it does, is dropped when the probe window closes unanswered, and is
 * dropped on arrival afterwards. Also pins the two things that are easy to get silently wrong - the
 * handshake answer bypassing the gate, and per-player state being released on disconnect.
 */
class ServerSendGateTest {

    private record Payload(String value) {
    }

    private static final PacketType<Payload> CONFIG = PacketType.clientbound("eunomia", "gate_config", Payload.class);
    private static final PacketType<Payload> PERMISSION =
            PacketType.clientbound("eunomia", "gate_permission", Payload.class);

    private final RecordingServerTransport transport = new RecordingServerTransport();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    private static final class RecordingServerTransport implements ServerTransport {
        final List<String> sent = new ArrayList<>();
        final List<UUID> online = new ArrayList<>();

        @Override
        public <T> void sendToPlayer(UUID playerId, PacketType<T> type, T data) {
            sent.add(playerId + " " + type.channelKey());
        }

        @Override
        public <T> void broadcast(PacketType<T> type, T data) {
            sent.add("broadcast " + type.channelKey());
        }

        @Override
        public <T> void broadcastExcept(UUID excludedPlayerId, PacketType<T> type, T data) {
            sent.add("broadcast-except " + type.channelKey());
        }

        @Override
        public Collection<UUID> connectedPlayerIds() {
            return online;
        }
    }

    @BeforeEach
    void setUp() {
        CommunicationManager.resetForTesting();
        NetworkSerializer.setGson(new Gson());
        CommunicationManager.setServerTransport(transport);
        CommunicationManager.register(CONFIG);
        CommunicationManager.register(PERMISSION);
        transport.online.add(alice);
        transport.online.add(bob);
    }

    @AfterEach
    void tearDown() {
        CommunicationManager.resetForTesting();
    }

    @Test
    void parksUntilHelloThenFlushesInSubmissionOrder() {
        CommunicationManager.sendToPlayer(alice, CONFIG, new Payload("1"));
        CommunicationManager.sendToPlayer(alice, PERMISSION, new Payload("2"));
        assertEquals(List.of(), transport.sent,
                "a clientbound send must not leave before the player's capability is known");
        assertEquals(ClientCapability.UNKNOWN, CommunicationManager.playerCapability(alice));

        CommunicationManager.markPlayerCapable(alice);
        assertEquals(List.of(alice + " " + CONFIG.channelKey(), alice + " " + PERMISSION.channelKey()),
                transport.sent, "the parked queue must flush in submission order");
        assertEquals(ClientCapability.PRESENT, CommunicationManager.playerCapability(alice));
    }

    @Test
    void sendsImmediatelyOnceThePlayerIsKnownCapable() {
        CommunicationManager.markPlayerCapable(alice);
        CommunicationManager.sendToPlayer(alice, CONFIG, new Payload("now"));
        assertEquals(List.of(alice + " " + CONFIG.channelKey()), transport.sent,
                "a resolved player must not be queued behind anything");
    }

    @Test
    void dropsTheQueueWhenTheProbeWindowClosesUnanswered() {
        CommunicationManager.sendToPlayer(alice, CONFIG, new Payload("1"));
        CommunicationManager.markPlayerIncapable(alice);
        assertEquals(List.of(), transport.sent,
                "a client that never answered must never be handed Eunomia framing");
        assertEquals(ClientCapability.ABSENT, CommunicationManager.playerCapability(alice));
    }

    @Test
    void dropsImmediatelyAfterAKnownNegativeInsteadOfReQueuing() {
        CommunicationManager.markPlayerIncapable(alice);
        CommunicationManager.sendToPlayer(alice, CONFIG, new Payload("late"));
        assertEquals(List.of(), transport.sent, "a known-incapable client must be dropped on arrival");
        // Re-queuing would show up as a flush here; a real drop cannot be resurrected.
        CommunicationManager.markPlayerCapable(alice);
        assertEquals(List.of(), transport.sent, "a dropped send must not be resurrected by a later HELLO");
    }

    @Test
    void aLateHelloStillWinsOverTheTimeout() {
        CommunicationManager.sendToPlayer(alice, CONFIG, new Payload("1"));
        CommunicationManager.markPlayerCapable(alice);
        // The timer for this player is still pending at this point; closing the window must not un-resolve
        // a player whose HELLO already landed.
        CommunicationManager.markPlayerIncapable(alice);
        assertEquals(ClientCapability.PRESENT, CommunicationManager.playerCapability(alice),
                "a resolved-present player must never be downgraded to absent");
    }

    @Test
    void perPlayerStateIsReleasedOnDisconnect() {
        CommunicationManager.markPlayerIncapable(alice);
        assertEquals(ClientCapability.ABSENT, CommunicationManager.playerCapability(alice));

        CommunicationManager.onPlayerDisconnect(alice);
        assertEquals(ClientCapability.UNKNOWN, CommunicationManager.playerCapability(alice),
                "disconnect must drop the entry, not just its queue - a stale entry is the leak");

        // And a rejoining player starts from scratch rather than inheriting the previous verdict.
        CommunicationManager.sendToPlayer(alice, CONFIG, new Payload("rejoined"));
        CommunicationManager.markPlayerCapable(alice);
        assertEquals(List.of(alice + " " + CONFIG.channelKey()), transport.sent);
    }

    @Test
    void theHandshakeAnswerBypassesTheGate() {
        CommunicationManager.register(HandshakePackets.HELLO_ACK);
        // Alice is entirely unresolved: gating HELLO_ACK on her capability would deadlock her connection,
        // because HELLO_ACK is what resolves it.
        CommunicationManager.sendToPlayer(alice, HandshakePackets.HELLO_ACK,
                new ServerHelloPayload(HandshakePackets.PROTOCOL_VERSION, List.of()));
        assertEquals(List.of(alice + " " + HandshakePackets.HELLO_ACK.channelKey()), transport.sent,
                "the handshake answer must go out regardless of the capability it exists to establish");
    }

    @Test
    void everyOtherChannelIsGatedIncludingBroadcasts() {
        CommunicationManager.markPlayerCapable(alice);
        CommunicationManager.broadcast(CONFIG, new Payload("x"));
        assertEquals(List.of(alice + " " + CONFIG.channelKey()), transport.sent,
                "a broadcast must reach the resolved player and wait for the unresolved one");

        CommunicationManager.markPlayerCapable(bob);
        assertEquals(List.of(alice + " " + CONFIG.channelKey(), bob + " " + CONFIG.channelKey()),
                transport.sent, "bob's copy must be delivered once his HELLO lands, not lost");
    }

    @Test
    void broadcastExceptSkipsTheExcludedPlayerAndGatesTheRest() {
        CommunicationManager.markPlayerCapable(alice);
        CommunicationManager.markPlayerCapable(bob);
        CommunicationManager.broadcastExcept(bob, CONFIG, new Payload("x"));
        assertEquals(List.of(alice + " " + CONFIG.channelKey()), transport.sent);
    }

    @Test
    void theHelloHandlerItselfResolvesThePlayerAndReleasesTheirQueue() {
        // End to end through the real handshake wiring rather than the markPlayerCapable shortcut: the
        // registered HELLO handler is what a joining client actually reaches, and if it stopped recording
        // the sender every gated send on this server would silently die of timeout.
        CommunicationManager.enableServerHandshake();
        CommunicationManager.sendToPlayer(alice, CONFIG, new Payload("join"));
        assertEquals(List.of(), transport.sent);

        CommunicationManager.dispatchServerbound(HandshakePackets.HELLO.channelKey(),
                new ClientHelloPayload(HandshakePackets.PROTOCOL_VERSION), new GateServerContext(alice));

        assertEquals(ClientCapability.PRESENT, CommunicationManager.playerCapability(alice));
        assertTrue(transport.sent.contains(alice + " " + CONFIG.channelKey()),
                "the packet parked at join must go out as soon as the player's HELLO is handled");
    }

    /** Minimal server context: the gate only ever needs the authenticated sender id. */
    private record GateServerContext(UUID senderId) implements ServerContext {
        @Override
        public String senderName() {
            return "gate-test";
        }

        @Override
        public <T> void reply(PacketType<T> type, T data) {
            CommunicationManager.sendToPlayer(senderId, type, data);
        }
    }

    @Test
    void theParkedQueueIsBoundedAndKeepsTheNewest() {
        int overflow = PlayerSendGate.MAX_PENDING + 5;
        for (int i = 0; i < overflow; i++) {
            CommunicationManager.sendToPlayer(alice, CONFIG, new Payload(Integer.toString(i)));
        }
        CommunicationManager.markPlayerCapable(alice);
        assertEquals(PlayerSendGate.MAX_PENDING, transport.sent.size(),
                "an unresolved player's queue must be bounded, or a per-tick consumer leaks memory");
    }

    @Test
    void theAutomaticTimeoutClosesTheWindowWithoutAnyoneAskingIt() throws Exception {
        ServerSendGate.setTimeoutMillis(50L);
        CommunicationManager.sendToPlayer(alice, CONFIG, new Payload("1"));
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (CommunicationManager.playerCapability(alice) == ClientCapability.UNKNOWN
                && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        assertEquals(ClientCapability.ABSENT, CommunicationManager.playerCapability(alice),
                "the gate must arm its own timeout - nothing else on the server will");
        assertEquals(List.of(), transport.sent);
    }

    @Test
    void aStaleTimeoutDoesNotResurrectAnEntryForADisconnectedPlayer() throws Exception {
        ServerSendGate.setTimeoutMillis(50L);
        CommunicationManager.sendToPlayer(alice, CONFIG, new Payload("1"));
        CommunicationManager.onPlayerDisconnect(alice);
        Thread.sleep(250L);
        assertEquals(ClientCapability.UNKNOWN, CommunicationManager.playerCapability(alice),
                "the timer is identity-checked, so a disconnected player's entry must stay gone");
        assertTrue(transport.sent.isEmpty());
    }
}
