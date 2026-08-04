package de.zannagh.eunomia.networking;

import com.google.gson.Gson;
import de.zannagh.eunomia.networking.examples.ExamplePackets;
import de.zannagh.eunomia.networking.examples.PingPayload;
import de.zannagh.eunomia.networking.examples.PongPayload;
import de.zannagh.eunomia.networking.serialization.NetworkSerializer;
import de.zannagh.eunomia.networking.serialization.PayloadCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommunicationManagerTest {

    private static final UUID SENDER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        CommunicationManager.resetForTesting();
        NetworkSerializer.setGson(new Gson());
    }

    @AfterEach
    void tearDown() {
        CommunicationManager.resetForTesting();
    }

    /** A minimal server context so handlers can reply through a recording transport. */
    private record TestServerContext(UUID senderId, String senderName) implements ServerContext {
        @Override
        public <T> void reply(PacketType<T> type, T data) {
            CommunicationManager.sendToPlayer(senderId, type, data);
        }
    }

    private static final class RecordingServerTransport implements ServerTransport {
        final List<String> sent = new ArrayList<>();

        @Override
        public <T> void sendToPlayer(UUID playerId, PacketType<T> type, T data) {
            sent.add(playerId + " " + type.channelKey());
        }

        @Override
        public <T> void broadcast(PacketType<T> type, T data) {
            sent.add("all " + type.channelKey());
        }

        @Override
        public <T> void broadcastExcept(UUID excludedPlayerId, PacketType<T> type, T data) {
            sent.add("all-but " + excludedPlayerId + " " + type.channelKey());
        }
    }

    @Test
    void registrationListenerReplaysEarlyRegistrations() {
        List<String> seen = new ArrayList<>();
        CommunicationManager.register(ExamplePackets.PING);
        // Listener installed AFTER a registration must still see it.
        CommunicationManager.setRegistrationListener(type -> seen.add(type.channelKey()));
        assertTrue(seen.contains(ExamplePackets.PING.channelKey()));

        // And a later registration is delivered live.
        CommunicationManager.register(ExamplePackets.PONG);
        assertTrue(seen.contains(ExamplePackets.PONG.channelKey()));
    }

    @Test
    void serverHandlerReceivesDecodedPayloadAndCanReply() {
        RecordingServerTransport transport = new RecordingServerTransport();
        CommunicationManager.setServerTransport(transport);

        AtomicReference<String> received = new AtomicReference<>();
        CommunicationManager.onServerReceive(ExamplePackets.PING, (payload, ctx) -> {
            received.set(payload.message);
            ctx.reply(ExamplePackets.PONG, new PongPayload(payload.message, payload.sentAtMillis, 123L));
        });

        boolean handled = CommunicationManager.dispatchServerbound(
                ExamplePackets.PING.channelKey(),
                new PingPayload("hi", 1L),
                new TestServerContext(SENDER, "tester"));

        assertTrue(handled);
        assertEquals("hi", received.get());
        assertEquals(List.of(SENDER + " " + ExamplePackets.PONG.channelKey()), transport.sent);
    }

    @Test
    void rawDispatchDecodesThroughPayloadCodec() {
        AtomicReference<PingPayload> received = new AtomicReference<>();
        CommunicationManager.onServerReceive(ExamplePackets.PING, (payload, ctx) -> received.set(payload));

        // Simulate the Paper/HTTP inbound path: raw gzip(json) bytes, decoded by the shared codec.
        byte[] raw = PayloadCodec.encode(new PingPayload("wire", 42L), true);
        boolean handled = CommunicationManager.dispatchServerboundRaw(
                ExamplePackets.PING.channelKey(), raw, new TestServerContext(SENDER, "tester"));

        assertTrue(handled);
        assertNotNull(received.get());
        assertEquals("wire", received.get().message);
        assertEquals(42L, received.get().sentAtMillis);
    }

    @Test
    void unhandledChannelReturnsFalse() {
        assertFalse(CommunicationManager.dispatchServerbound(
                "eunomia:never_registered", new PingPayload(), new TestServerContext(SENDER, "x")));
    }

    @Test
    void directionGuardsRejectWrongWaySends() {
        CommunicationManager.setServerTransport(new RecordingServerTransport());
        // PING is serverbound: sending it to a player is a programming error.
        assertThrows(IllegalArgumentException.class,
                () -> CommunicationManager.sendToPlayer(SENDER, ExamplePackets.PING, new PingPayload()));
        // PONG is clientbound: sending it to the server is a programming error.
        assertThrows(IllegalArgumentException.class,
                () -> CommunicationManager.sendToServer(ExamplePackets.PONG, new PongPayload()));
    }

    @Test
    void directionFiltersPartitionTypes() {
        CommunicationManager.register(ExamplePackets.PING);
        CommunicationManager.register(ExamplePackets.PONG);
        assertTrue(CommunicationManager.serverboundTypes().contains(ExamplePackets.PING));
        assertFalse(CommunicationManager.serverboundTypes().contains(ExamplePackets.PONG));
        assertTrue(CommunicationManager.clientboundTypes().contains(ExamplePackets.PONG));
        assertFalse(CommunicationManager.clientboundTypes().contains(ExamplePackets.PING));
    }
}
