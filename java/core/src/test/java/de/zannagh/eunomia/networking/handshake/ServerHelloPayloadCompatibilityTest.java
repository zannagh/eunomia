package de.zannagh.eunomia.networking.handshake;

import com.google.gson.Gson;
import de.zannagh.eunomia.configuration.SyncSetting;
import de.zannagh.eunomia.networking.serialization.NetworkSerializer;
import de.zannagh.eunomia.networking.serialization.PayloadCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    /**
     * A full protocol-2 ACK from a server built before enforcement existed: every policy key present, the
     * enforcement key absent. Nothing else in this suite pins that path, and it is the one that decides whether
     * an older server's advertised values stay advisory instead of quietly becoming locks.
     */
    private static final String PRE_ENFORCEMENT_ACK =
            "{\"protocolVersion\":2,\"receiverChannels\":[\"eunomia:hello\"],"
                    + "\"enableExternalFallback\":true,\"externalServerAddress\":\"https://relay.example\","
                    + "\"preferExternalTransport\":true}";

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

    // ── Enforcement, added after protocol 2 shipped ────────────────────────────────────────────

    @Test
    void aPayloadWithoutTheEnforcementFieldAdvertisesValuesButEnforcesNothing() {
        ServerHelloPayload payload = new Gson().fromJson(PRE_ENFORCEMENT_ACK, ServerHelloPayload.class);

        ServerSyncPolicy policy = payload.syncPolicy();

        // The values still arrive; only the enforcement is absent, which must read as "advisory".
        assertEquals(Boolean.TRUE, policy.enableExternalFallback());
        assertEquals("https://relay.example", policy.externalServerAddress());
        assertTrue(policy.enforced().isEmpty());
        for (SyncSetting setting : SyncSetting.values()) {
            assertFalse(policy.enforces(setting));
        }
    }

    @Test
    void aPayloadWithoutTheEnforcementFieldSurvivesTheRealWireCodecToo() {
        byte[] wire = PayloadCodec.encode(
                new Gson().fromJson(PRE_ENFORCEMENT_ACK, ServerHelloPayload.class), false);

        ServerHelloPayload decoded = PayloadCodec.decode(wire, ServerHelloPayload.class);

        assertTrue(decoded.syncPolicy().enforced().isEmpty());
        assertEquals(Boolean.TRUE, decoded.syncPolicy().enableExternalFallback());
    }

    @Test
    void anEnforcedPolicyRoundTripsThroughTheWire() {
        ServerHelloPayload sent = new ServerHelloPayload(
                HandshakePackets.PROTOCOL_VERSION, List.of("eunomia:hello"),
                new ServerSyncPolicy(false, "https://relay.example", null,
                        Set.of(SyncSetting.EXTERNAL_FALLBACK, SyncSetting.EXTERNAL_SERVER_ADDRESS)));

        ServerHelloPayload decoded =
                PayloadCodec.decode(PayloadCodec.encode(sent, false), ServerHelloPayload.class);

        assertEquals(Set.of(SyncSetting.EXTERNAL_FALLBACK, SyncSetting.EXTERNAL_SERVER_ADDRESS),
                decoded.syncPolicy().enforced());
        assertTrue(decoded.syncPolicy().enforces(SyncSetting.EXTERNAL_FALLBACK));
        assertFalse(decoded.syncPolicy().enforces(SyncSetting.PREFER_EXTERNAL_TRANSPORT));
    }

    /**
     * A newer server may enforce a setting this build has never heard of. Dropping it leaves that setting
     * resolving the way it always did; throwing would take the whole handshake down over a setting this client
     * does not even have.
     */
    @Test
    void anUnknownEnforcedSettingNameIsIgnoredRatherThanFatal() {
        String ack = "{\"protocolVersion\":2,\"receiverChannels\":[],\"enableExternalFallback\":true,"
                + "\"enforcedSettings\":[\"EXTERNAL_FALLBACK\",\"SOMETHING_FROM_THE_FUTURE\"]}";

        ServerSyncPolicy policy =
                assertDoesNotThrow(() -> new Gson().fromJson(ack, ServerHelloPayload.class).syncPolicy());

        assertEquals(Set.of(SyncSetting.EXTERNAL_FALLBACK), policy.enforced());
    }

    /** An enforcement set over no values is not an opinion, so it must not make a policy look non-empty. */
    @Test
    void enforcementAloneDoesNotMakeAPolicyLookLikeAnOpinion() {
        ServerSyncPolicy policy =
                new ServerSyncPolicy(null, null, null, Set.of(SyncSetting.EXTERNAL_FALLBACK));

        assertTrue(policy.isEmpty());
    }
}
