package de.zannagh.eunomia.networking.comms;

import com.google.gson.Gson;
import de.zannagh.eunomia.networking.packets.PacketType;
import de.zannagh.eunomia.networking.serialization.NetworkSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the external-relay fallback behaviour of the send gate: an {@code AFTER_SUCCESSFUL_HANDSHAKE} send is no
 * longer dropped the instant the probe resolves "absent" (the server is not Eunomia) - it is parked until the
 * client transport selector's asynchronous fallback decision lands, so a config sent on join reaches the relay
 * instead of being lost in the gap. {@link CommunicationManager#setExternalTransportActive(boolean)} flushes the
 * parked queue to the relay; {@link CommunicationManager#concludeNoRelay()} drops it when no relay is usable.
 */
class ClientSendGateFallbackTest {

    private static final PacketType<Msg> C2S = PacketType.serverbound("test", "gate_fallback", Msg.class);

    private final List<String> delivered = new ArrayList<>();

    @BeforeEach
    void setUp() {
        CommunicationManager.resetForTesting();
        NetworkSerializer.setGson(new Gson());
        CommunicationManager.register(C2S);
        CommunicationManager.setClientTransport(new RecordingTransport());
    }

    @AfterEach
    void tearDown() {
        CommunicationManager.resetForTesting();
    }

    @Test
    void absentResolutionParksTheSendUntilTheRelayActivatesThenDeliversIt() {
        CommunicationManager.beginServerProbe();
        CommunicationManager.sendToServer(C2S, new Msg("join"));
        assertTrue(delivered.isEmpty(), "queued while the probe is pending");

        CommunicationManager.markServerProbeTimedOut();
        assertTrue(delivered.isEmpty(), "absent no longer drops immediately - parked for the fallback decision");

        CommunicationManager.setExternalTransportActive(true);
        assertEquals(List.of("join"), delivered, "the parked send flushes to the relay once it is active");
    }

    @Test
    void concludingNoRelayDropsTheParkedSend() {
        CommunicationManager.beginServerProbe();
        CommunicationManager.sendToServer(C2S, new Msg("join"));
        CommunicationManager.markServerProbeTimedOut();

        CommunicationManager.concludeNoRelay();
        assertTrue(delivered.isEmpty(), "no usable relay: the parked send is dropped");
    }

    @Test
    void sendsAfterTheRelayIsActiveGoStraightToIt() {
        CommunicationManager.beginServerProbe();
        CommunicationManager.markServerProbeTimedOut();
        CommunicationManager.setExternalTransportActive(true);

        CommunicationManager.sendToServer(C2S, new Msg("change"));
        assertEquals(List.of("change"), delivered, "delivered immediately while the relay is active");
    }

    /** Records only the test channel's sends; the handshake HELLO the probe emits is on another channel. */
    private final class RecordingTransport implements ClientTransport {
        @Override
        public <T> void sendToServer(PacketType<T> type, T data) {
            if (type == C2S) {
                delivered.add(((Msg) data).value);
            }
        }
    }

    public static final class Msg {
        public String value;

        public Msg() {
        }

        public Msg(String value) {
            this.value = value;
        }
    }
}
