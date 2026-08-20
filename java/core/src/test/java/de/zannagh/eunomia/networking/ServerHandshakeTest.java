package de.zannagh.eunomia.networking;

import com.google.gson.Gson;
import de.zannagh.eunomia.networking.comms.ClientTransport;
import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.handshake.HandshakePackets;
import de.zannagh.eunomia.networking.packets.ClientContext;
import de.zannagh.eunomia.networking.packets.PacketType;
import de.zannagh.eunomia.networking.packets.ServerContext;
import de.zannagh.eunomia.networking.serialization.NetworkSerializer;
import de.zannagh.eunomia.networking.serialization.PayloadCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the capability handshake over looped-back wire bytes: the client's HELLO is decoded by
 * the server, whose ACK (carrying its receiver channels) is decoded back on the client and recorded
 * in {@link CommunicationManager#serverCapabilities()}. Also covers the no-Eunomia case, where the
 * probe times out and resolves to "absent".
 */
class ServerHandshakeTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    @BeforeEach
    void setUp() {
        CommunicationManager.resetForTesting();
        NetworkSerializer.setGson(new Gson());
    }

    @AfterEach
    void tearDown() {
        CommunicationManager.resetForTesting();
    }

    private final class LoopClientContext implements ClientContext {
        @Override
        public <T> void reply(PacketType<T> type, T data) {
            CommunicationManager.dispatchServerboundRaw(
                    type.channelKey(), PayloadCodec.encode(data, true), new LoopServerContext());
        }
    }

    private final class LoopServerContext implements ServerContext {
        @Override
        public UUID senderId() {
            return PLAYER;
        }

        @Override
        public String senderName() {
            return "probe";
        }

        @Override
        public <T> void reply(PacketType<T> type, T data) {
            CommunicationManager.dispatchClientboundRaw(
                    type.channelKey(), PayloadCodec.encode(data, false), new LoopClientContext());
        }
    }

    @Test
    void probeResolvesPresentAndListsReceiverChannels() {
        // Server: handshake + one extra receiver, standing in for a consuming mod's packet.
        CommunicationManager.enableServerHandshake();
        record Sync(String v) {
        }
        PacketType<Sync> modPacket = PacketType.serverbound("mymod", "sync", Sync.class);
        CommunicationManager.onServerReceive(modPacket, (s, ctx) -> {
        });

        // Client: record the ACK. The client's outbound HELLO loops into the server dispatch.
        CommunicationManager.enableClientHandshake();
        CommunicationManager.setClientTransport(new ClientTransport() {
            @Override
            public <T> void sendToServer(PacketType<T> type, T data) {
                CommunicationManager.dispatchServerboundRaw(
                        type.channelKey(), PayloadCodec.encode(data, true), new LoopServerContext());
            }
        });

        AtomicReference<Boolean> notified = new AtomicReference<>();
        CommunicationManager.serverCapabilities().onResolved(caps -> notified.set(caps.isPresent()));

        CommunicationManager.beginServerProbe();

        var caps = CommunicationManager.serverCapabilities();
        assertTrue(caps.isResolved(), "probe should have resolved");
        assertTrue(caps.isPresent(), "server runs Eunomia, so it should be present");
        assertTrue(caps.supports(modPacket), "server must report it receives the consuming mod's packet");
        assertTrue(caps.supports(HandshakePackets.HELLO), "server receives HELLO");
        assertFalse(caps.supports("other:nope"), "unknown channels must not be reported as supported");
        assertTrue(notified.get(), "onResolved listener should have fired as present");
    }

    @Test
    void probeTimesOutToAbsentWhenServerHasNoEunomia() {
        // Client only; its HELLO goes nowhere (a vanilla / non-Eunomia server never answers).
        CommunicationManager.enableClientHandshake();
        CommunicationManager.setClientTransport(new ClientTransport() {
            @Override
            public <T> void sendToServer(PacketType<T> type, T data) {
                // dropped
            }
        });

        AtomicReference<Boolean> notified = new AtomicReference<>();
        CommunicationManager.serverCapabilities().onResolved(caps -> notified.set(caps.isPresent()));

        CommunicationManager.beginServerProbe();
        assertFalse(CommunicationManager.serverCapabilities().isResolved(), "no ACK yet, so unresolved");

        // The client's timeout fires.
        CommunicationManager.markServerProbeTimedOut();

        var caps = CommunicationManager.serverCapabilities();
        assertTrue(caps.isResolved());
        assertFalse(caps.isPresent(), "no ACK means the server does not run Eunomia");
        assertFalse(notified.get(), "onResolved listener should have fired as absent");
    }
}
