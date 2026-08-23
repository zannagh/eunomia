package de.zannagh.eunomia.networking.handshake;

import com.google.gson.Gson;
import de.zannagh.eunomia.networking.serialization.NetworkSerializer;
import de.zannagh.eunomia.networking.serialization.PayloadCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cross-version handshake decoding. A protocol-2 client will meet protocol-1 servers for as long as anyone runs
 * an older build, so a v1 ACK must decode into a perfectly ordinary "server present, no policy advertised"
 * result - never an exception, and never a set of invented policy values that would outrank the rungs below it
 * in the settings chain.
 */
class ServerHelloPayloadCompatibilityTest {

    /** Exactly what a protocol-1 server put on the wire: no policy keys at all. */
    private static final String V1_ACK =
            "{\"protocolVersion\":1,\"receiverChannels\":[\"eunomia:hello\",\"mymod:sync\"]}";

    @BeforeEach
    void setUp() {
        NetworkSerializer.setGson(new Gson());
    }

    @Test
    void protocolVersionIsTwo() {
        assertEquals(2, HandshakePackets.PROTOCOL_VERSION);
    }

    @Test
    void aVersionOnePayloadDecodesWithoutThrowing() {
        assertDoesNotThrow(() -> new Gson().fromJson(V1_ACK, ServerHelloPayload.class));
    }

    @Test
    void aVersionOnePayloadKeepsItsChannelsAndAdvertisesNoPolicy() {
        ServerHelloPayload payload = new Gson().fromJson(V1_ACK, ServerHelloPayload.class);

        assertEquals(1, payload.protocolVersion);
        assertEquals(List.of("eunomia:hello", "mymod:sync"), payload.receiverChannelsOrEmpty());
        assertEquals(ServerSyncPolicy.UNKNOWN, payload.syncPolicy());
        assertTrue(payload.syncPolicy().isEmpty());
    }

    @Test
    void aVersionOnePayloadDecodesThroughTheRealWireCodecToo() {
        byte[] wire = PayloadCodec.encode(new Gson().fromJson(V1_ACK, ServerHelloPayload.class), false);

        ServerHelloPayload decoded = PayloadCodec.decode(wire, ServerHelloPayload.class);

        assertEquals(ServerSyncPolicy.UNKNOWN, decoded.syncPolicy());
        assertEquals(2, decoded.receiverChannelsOrEmpty().size());
    }

    @Test
    void aTruncatedPayloadWithNoChannelsAtAllStillDecodes() {
        ServerHelloPayload payload = new Gson().fromJson("{\"protocolVersion\":1}", ServerHelloPayload.class);

        assertTrue(payload.receiverChannelsOrEmpty().isEmpty());
        assertEquals(ServerSyncPolicy.UNKNOWN, payload.syncPolicy());
    }

    @Test
    void aVersionTwoPayloadCarriesTheAdvertisedPolicy() {
        ServerHelloPayload sent = new ServerHelloPayload(
                HandshakePackets.PROTOCOL_VERSION, List.of("eunomia:hello"),
                new ServerSyncPolicy(true, "https://relay.example", true));

        ServerHelloPayload decoded = PayloadCodec.decode(PayloadCodec.encode(sent, false), ServerHelloPayload.class);

        assertEquals(2, decoded.protocolVersion);
        assertEquals(new ServerSyncPolicy(true, "https://relay.example", true), decoded.syncPolicy());
    }

    @Test
    void aVersionTwoServerThatAdvertisesNothingIsIndistinguishableFromAVersionOneOne() {
        ServerHelloPayload sent = new ServerHelloPayload(
                HandshakePackets.PROTOCOL_VERSION, List.of("eunomia:hello"), ServerSyncPolicy.UNKNOWN);

        ServerHelloPayload decoded = PayloadCodec.decode(PayloadCodec.encode(sent, false), ServerHelloPayload.class);

        assertEquals(ServerSyncPolicy.UNKNOWN, decoded.syncPolicy());
    }

    @Test
    void aVersionOneAckResolvesTheCapabilityViewWithAnUnknownPolicy() {
        ServerCapabilities capabilities = new ServerCapabilities();
        ServerHelloPayload v1 = new Gson().fromJson(V1_ACK, ServerHelloPayload.class);

        capabilities.markPresent(v1.protocolVersion, v1.receiverChannelsOrEmpty(), v1.syncPolicy());

        assertTrue(capabilities.isPresent());
        assertEquals(1, capabilities.protocolVersion());
        assertEquals(ServerSyncPolicy.UNKNOWN, capabilities.syncPolicy());
    }

    @Test
    void resettingTheCapabilityViewClearsTheAdvertisedPolicy() {
        ServerCapabilities capabilities = new ServerCapabilities();
        capabilities.markPresent(2, List.of(), new ServerSyncPolicy(true, "https://relay.example", true));

        capabilities.reset();

        assertEquals(ServerSyncPolicy.UNKNOWN, capabilities.syncPolicy());
    }
}
