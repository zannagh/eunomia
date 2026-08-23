package de.zannagh.eunomia.paper;

import com.google.gson.Gson;
import de.zannagh.eunomia.configuration.EunomiaDefaults;
import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.handshake.ClientHelloPayload;
import de.zannagh.eunomia.networking.handshake.HandshakePackets;
import de.zannagh.eunomia.networking.handshake.ServerHelloPayload;
import de.zannagh.eunomia.networking.handshake.ServerSyncPolicy;
import de.zannagh.eunomia.networking.packets.PacketType;
import de.zannagh.eunomia.networking.packets.ServerContext;
import de.zannagh.eunomia.networking.serialization.NetworkSerializer;
import de.zannagh.eunomia.networking.serialization.PayloadCodec;
import de.zannagh.eunomia.paper.config.PaperServerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic (no Bukkit, no server) proof that a Paper server genuinely advertises its operator's Cloud Sync
 * policy: the plugin's {@link PaperServerConfig} is loaded from a real {@code eunomia-server.json} on disk, and
 * the bytes the handshake handler answers a probe with are decoded back into a {@link ServerSyncPolicy}.
 *
 * <p>The whole point of the slice this covers is that the Paper plugin used to answer {@link
 * ServerSyncPolicy#UNKNOWN} - so the assertions below are deliberately about values arriving on the wire, not
 * about the config object in isolation, which {@code EunomiaServerConfigTest} in {@code :core} already covers.
 */
class PaperSyncPolicyAdvertisementTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000cc");

    @TempDir
    Path dataFolder;

    @BeforeEach
    void setUp() {
        CommunicationManager.resetForTesting();
        NetworkSerializer.setGson(new Gson());
    }

    @AfterEach
    void tearDown() {
        CommunicationManager.resetForTesting();
    }

    @Test
    void theOperatorsPolicyRoundTripsIntoTheHandshakeAnswer() throws IOException {
        writeConfig("{\"enableExternalFallback\":true,"
                + "\"externalServerAddress\":\"https://relay.example\","
                + "\"preferExternalTransport\":true}");

        ServerSyncPolicy advertised = probe();

        assertEquals(Boolean.TRUE, advertised.enableExternalFallback());
        assertEquals("https://relay.example", advertised.externalServerAddress());
        assertEquals(Boolean.TRUE, advertised.preferExternalTransport());
    }

    @Test
    void aServerThatClearedItsAddressAdvertisesNoAddressRatherThanAnEmptyOne() throws IOException {
        // Schema 1.1.0, so the false is read as the operator's decision rather than as an untouched default.
        writeConfig("{\"schemaVersion\":\"1.1.0\",\"enableExternalFallback\":false,"
                + "\"externalServerAddress\":\"  \"}");

        ServerSyncPolicy advertised = probe();

        assertEquals(Boolean.FALSE, advertised.enableExternalFallback());
        assertNull(advertised.externalServerAddress());
    }

    /**
     * The correction to the slice this test class was written for. A Paper server must be <em>able</em> to
     * advertise, which the first test proves - but an operator who never opened {@code eunomia-server.json}
     * has expressed nothing, and advertising the framework defaults on their behalf silently overrode the
     * consuming mod's own defaults on every client that joined. So an absent file materialises, and says
     * nothing.
     */
    @Test
    void anAbsentConfigFileIsMaterialisedButAdvertisesNoOpinionAtAll() throws IOException {
        ServerSyncPolicy advertised = probe();

        assertTrue(Files.exists(dataFolder.resolve(PaperServerConfig.FILE_NAME)),
                "the plugin should materialise the config file so an operator has something to edit");
        assertNull(advertised.enableExternalFallback());
        assertNull(advertised.externalServerAddress());
        assertNull(advertised.preferExternalTransport());
        assertTrue(advertised.isEmpty(), "an untouched Paper server must not shadow the mod's own defaults");
    }

    /**
     * A 1.0.0 file materialised all three keys onto the framework defaults, so it is indistinguishable from
     * an untouched one and migrates to "no opinion" rather than being read as a full set of decisions.
     */
    @Test
    void aLegacyFileThatMerelyRepeatsTheFrameworkDefaultsMigratesToSayingNothing() throws IOException {
        writeConfig("{\"enableExternalFallback\":false,\"externalServerAddress\":\""
                + EunomiaDefaults.DEFAULT_EXTERNAL_SERVER_ADDRESS
                + "\",\"preferExternalTransport\":false}");

        assertTrue(probe().isEmpty());
    }

    /** Loads the config exactly as {@code EunomiaPaperPlugin#onEnable} does, then probes the handshake. */
    private ServerSyncPolicy probe() {
        PaperServerConfig config = PaperServerConfig.loadFrom(
                dataFolder, LoggerFactory.getLogger(PaperSyncPolicyAdvertisementTest.class));
        config.install();
        CommunicationManager.enableServerHandshake();

        AtomicReference<ServerHelloPayload> ack = new AtomicReference<>();
        CommunicationManager.dispatchServerboundRaw(
                HandshakePackets.HELLO.channelKey(),
                PayloadCodec.encode(new ClientHelloPayload(HandshakePackets.PROTOCOL_VERSION), true),
                new CapturingServerContext(ack));

        ServerHelloPayload payload = ack.get();
        assertNotNull(payload, "the server handshake handler did not answer the probe");
        return payload.syncPolicy();
    }

    private void writeConfig(String json) throws IOException {
        Files.createDirectories(dataFolder);
        Files.writeString(dataFolder.resolve(PaperServerConfig.FILE_NAME), json);
    }

    /**
     * Stands in for {@code PaperServerTransport}'s context: it round-trips the reply through the shared codec
     * (rather than keeping the object) so the assertions see what a client would actually decode.
     */
    private static final class CapturingServerContext implements ServerContext {

        private final AtomicReference<ServerHelloPayload> captured;

        private CapturingServerContext(AtomicReference<ServerHelloPayload> captured) {
            this.captured = captured;
        }

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
            if (!HandshakePackets.HELLO_ACK.channelKey().equals(type.channelKey())) {
                return;
            }
            byte[] wire = PayloadCodec.encode(data, false);
            captured.set(PayloadCodec.decode(wire, ServerHelloPayload.class));
        }
    }
}
