package de.zannagh.eunomia.networking;

import com.google.gson.Gson;
import de.zannagh.eunomia.networking.examples.ExampleHandlers;
import de.zannagh.eunomia.networking.examples.ExamplePackets;
import de.zannagh.eunomia.networking.examples.PermissionPayload;
import de.zannagh.eunomia.networking.examples.PingPayload;
import de.zannagh.eunomia.networking.serialization.NetworkSerializer;
import de.zannagh.eunomia.networking.serialization.PayloadCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Drives the whole handshake through the real {@link PayloadCodec} wire bytes in one JVM, with the
 * transports looping encoded bytes straight back into the manager's dispatch. This is the FCGT
 * gametest's logic minus the Minecraft mixin layer: client PINGs, the server handler decodes it and
 * replies a PONG, the client handler decodes that; and the server pushes a PERMISSION on "join".
 * Everything crosses a gzip(json) boundary, so a break in routing, direction handling or resolution
 * reddens here without needing a game client.
 */
class LoopbackHandshakeTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private final AtomicReference<String> clientPong = new AtomicReference<>();
    private final AtomicReference<Integer> clientPermission = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        CommunicationManager.resetForTesting();
        NetworkSerializer.setGson(new Gson());
    }

    @AfterEach
    void tearDown() {
        CommunicationManager.resetForTesting();
    }

    /** A client context whose reply loops a serverbound frame back into the server dispatch. */
    private final class LoopClientContext implements ClientContext {
        @Override
        public <T> void reply(PacketType<T> type, T data) {
            byte[] wire = PayloadCodec.encode(data, true);
            CommunicationManager.dispatchServerboundRaw(type.channelKey(), wire, new LoopServerContext());
        }
    }

    /** A server context whose reply loops a clientbound frame back into the client dispatch. */
    private final class LoopServerContext implements ServerContext {
        @Override
        public UUID senderId() {
            return PLAYER;
        }

        @Override
        public String senderName() {
            return "LoopPlayer";
        }

        @Override
        public <T> void reply(PacketType<T> type, T data) {
            byte[] wire = PayloadCodec.encode(data, false);
            CommunicationManager.dispatchClientboundRaw(type.channelKey(), wire, new LoopClientContext());
        }
    }

    @Test
    void fullPingPongAndPermissionRoundTrip() {
        // Server side: the shared PING -> PONG handler, plus a join-style PERMISSION push.
        ExampleHandlers.registerPingPong();

        // Client side: record what comes back.
        CommunicationManager.onClientReceive(ExamplePackets.PONG, (pong, ctx) -> clientPong.set(pong.message));
        CommunicationManager.onClientReceive(ExamplePackets.PERMISSION,
                (perm, ctx) -> clientPermission.set(perm.permissionLevel));

        // "Join": server pushes PERMISSION to the client (clientbound frame over the wire).
        byte[] permWire = PayloadCodec.encode(new PermissionPayload(4), false);
        CommunicationManager.dispatchClientboundRaw(
                ExamplePackets.PERMISSION.channelKey(), permWire, new LoopClientContext());

        // Client PINGs the server (serverbound frame over the wire); the handler replies PONG,
        // which loops back into the client PONG handler.
        byte[] pingWire = PayloadCodec.encode(new PingPayload("handshake", 7L), true);
        CommunicationManager.dispatchServerboundRaw(
                ExamplePackets.PING.channelKey(), pingWire, new LoopServerContext());

        assertEquals(4, clientPermission.get(), "client should have received its permission level");
        assertNotNull(clientPong.get(), "client should have received a PONG");
        assertEquals("handshake", clientPong.get(), "PONG must echo the PING message");
    }

    @Test
    void extraPacketAndHandlerRoundTrip() {
        // Proves the "add another packet + handler with minimal code" claim: define a new channel and
        // handler at runtime, exactly as a downstream mod would, and it routes like any built-in one.
        record Greeting(String who) {
        }
        PacketType<Greeting> greetC2S = PacketType.serverbound("mymod", "greet", Greeting.class);
        PacketType<Greeting> greetS2C = PacketType.clientbound("mymod", "greet_ack", Greeting.class);

        AtomicInteger serverHits = new AtomicInteger();
        AtomicReference<String> clientAck = new AtomicReference<>();

        CommunicationManager.onServerReceive(greetC2S, (greeting, ctx) -> {
            serverHits.incrementAndGet();
            ctx.reply(greetS2C, new Greeting("hello " + greeting.who()));
        });
        CommunicationManager.onClientReceive(greetS2C, (ack, ctx) -> clientAck.set(ack.who()));

        byte[] wire = PayloadCodec.encode(new Greeting("eunomia"), true);
        CommunicationManager.dispatchServerboundRaw(greetC2S.channelKey(), wire, new LoopServerContext());

        assertEquals(1, serverHits.get());
        assertEquals("hello eunomia", clientAck.get());
    }
}
