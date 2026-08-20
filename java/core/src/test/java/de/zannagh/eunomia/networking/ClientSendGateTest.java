package de.zannagh.eunomia.networking;

import com.google.gson.Gson;
import de.zannagh.eunomia.networking.comms.ClientTransport;
import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.comms.SendOptions;
import de.zannagh.eunomia.networking.handshake.HandshakePackets;
import de.zannagh.eunomia.networking.handshake.ServerCapabilities;
import de.zannagh.eunomia.networking.packets.PacketType;
import de.zannagh.eunomia.networking.serialization.NetworkSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises {@link CommunicationManager#sendToServer(PacketType, Object, SendOptions)} and the
 * capability gate behind it: {@link SendOptions#ALWAYS} bypasses the handshake, while
 * {@link SendOptions#AFTER_SUCCESSFUL_HANDSHAKE} and {@link SendOptions#IF_SERVER_SUPPORTS} queue until
 * the probe resolves and then flush-or-drop. The gate is driven directly through the
 * {@link ServerCapabilities} transitions rather than a full wire handshake.
 */
class ClientSendGateTest {

    private record Payload(String value) {
    }

    private static final PacketType<Payload> GATED = PacketType.serverbound("eunomia", "gated_test", Payload.class);
    private static final PacketType<Payload> OTHER = PacketType.serverbound("eunomia", "other_test", Payload.class);

    private final RecordingClientTransport transport = new RecordingClientTransport();

    private static final class RecordingClientTransport implements ClientTransport {
        final List<String> sent = new ArrayList<>();

        @Override
        public <T> void sendToServer(PacketType<T> type, T data) {
            sent.add(type.channelKey());
        }
    }

    @BeforeEach
    void setUp() {
        CommunicationManager.resetForTesting();
        NetworkSerializer.setGson(new Gson());
        CommunicationManager.setClientTransport(transport);
    }

    @AfterEach
    void tearDown() {
        CommunicationManager.resetForTesting();
    }

    private ServerCapabilities caps() {
        return CommunicationManager.serverCapabilities();
    }

    @Test
    void alwaysSendsImmediatelyEvenBeforeHandshakeResolves() {
        // No probe has resolved, yet ALWAYS must still put the packet on the wire.
        CommunicationManager.sendToServer(GATED, new Payload("x"), SendOptions.ALWAYS);
        assertEquals(List.of(GATED.channelKey()), transport.sent);
    }

    @Test
    void afterHandshakeQueuesUntilPresentThenFlushesInOrder() {
        // Two gated sends while the probe is unresolved: nothing goes out yet.
        CommunicationManager.sendToServer(GATED, new Payload("1"));
        CommunicationManager.sendToServer(OTHER, new Payload("2"));
        assertEquals(List.of(), transport.sent, "gated sends must not leave before the probe resolves");

        // Server turns out to run Eunomia: the queue flushes in submission order.
        caps().markPresent(1, List.of(GATED.channelKey(), OTHER.channelKey()));
        assertEquals(List.of(GATED.channelKey(), OTHER.channelKey()), transport.sent);
    }

    @Test
    void afterHandshakeSendsImmediatelyOnceAlreadyPresent() {
        caps().markPresent(1, List.of(GATED.channelKey()));
        CommunicationManager.sendToServer(GATED, new Payload("now"));
        assertEquals(List.of(GATED.channelKey()), transport.sent, "an already-present server sends without queuing");
    }

    @Test
    void afterHandshakeDropsQueueWhenServerIsAbsent() {
        CommunicationManager.sendToServer(GATED, new Payload("drop me"));
        // The probe times out with no ACK: the server does not run Eunomia, so the queue is dropped.
        CommunicationManager.markServerProbeTimedOut();
        assertEquals(List.of(), transport.sent, "queued sends must be dropped for a non-Eunomia server");
    }

    @Test
    void ifServerSupportsRequiresAReceiverForThatChannel() {
        // Server runs Eunomia but only receives OTHER, not GATED.
        CommunicationManager.sendToServer(GATED, new Payload("no receiver"), SendOptions.IF_SERVER_SUPPORTS);
        CommunicationManager.sendToServer(OTHER, new Payload("has receiver"), SendOptions.IF_SERVER_SUPPORTS);
        caps().markPresent(1, List.of(OTHER.channelKey()));
        assertEquals(List.of(OTHER.channelKey()), transport.sent,
                "IF_SERVER_SUPPORTS must drop channels the server has no receiver for");
    }

    @Test
    void beginServerProbeDropsSendsQueuedForThePriorConnection() {
        CommunicationManager.setClientTransport(transport);
        CommunicationManager.sendToServer(GATED, new Payload("stale"));

        // Reconnect: the new probe clears the previous connection's queue (and sends HELLO).
        CommunicationManager.beginServerProbe();
        // Resolving the NEW connection as present must not flush the stale packet.
        caps().markPresent(1, List.of(GATED.channelKey()));
        assertEquals(List.of(HandshakePackets.HELLO.channelKey()), transport.sent,
                "only the fresh probe's HELLO should be on the wire; the stale queued send is gone");
    }

    @Test
    void directionGuardStillThrowsForClientboundEvenWithOptions() {
        PacketType<Payload> clientbound = PacketType.clientbound("eunomia", "cb_test", Payload.class);
        assertThrows(IllegalArgumentException.class,
                () -> CommunicationManager.sendToServer(clientbound, new Payload("nope"), SendOptions.ALWAYS));
    }
}
