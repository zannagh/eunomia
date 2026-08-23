package de.zannagh.eunomia.networking.admin;

import com.google.gson.Gson;
import de.zannagh.eunomia.configuration.ConfigurationProvider;
import de.zannagh.eunomia.configuration.EunomiaServerConfig;
import de.zannagh.eunomia.configuration.FileConfigurationProvider;
import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.handshake.ClientHelloPayload;
import de.zannagh.eunomia.networking.handshake.HandshakePackets;
import de.zannagh.eunomia.networking.handshake.ServerHelloPayload;
import de.zannagh.eunomia.networking.handshake.ServerSyncPolicy;
import de.zannagh.eunomia.networking.packets.PacketType;
import de.zannagh.eunomia.networking.packets.ServerContext;
import de.zannagh.eunomia.networking.serialization.NetworkSerializer;
import de.zannagh.eunomia.networking.serialization.PayloadCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The security properties of the administrative Cloud Sync settings channel, exercised through real wire
 * bytes rather than through direct handler calls - because the thing under test is what an arbitrary,
 * possibly modified client can make the server do, and that is defined by what it can put on the wire.
 *
 * <p>Deliberately independent of the capability handshake resolving: every request here is injected with
 * {@link CommunicationManager#dispatchServerboundRaw}, which is exactly what a platform's inbound path calls
 * once bytes have arrived. Nothing waits on a probe.</p>
 *
 * <p>Config storage is a real {@link FileConfigurationProvider} over a temp directory, so "persisted" means
 * the bytes actually reached disk and not merely that a field was reassigned.</p>
 */
class ServerSettingsExchangeSecurityTest {

    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID INTRUDER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    @TempDir
    Path configDirectory;

    private ConfigurationProvider<EunomiaServerConfig> provider;
    private final AtomicBoolean callerIsAdmin = new AtomicBoolean(false);

    @BeforeEach
    void setUp() {
        CommunicationManager.resetForTesting();
        NetworkSerializer.setGson(new Gson());
        provider = new FileConfigurationProvider<>(
                configDirectory.resolve("eunomia-server.json"),
                EunomiaServerConfig.class,
                EunomiaServerConfig::new,
                new Gson(),
                LoggerFactory.getLogger(ServerSettingsExchangeSecurityTest.class));
        // The exact wiring the loaders use: the exchange and the handshake read the same provider, the
        // latter through a supplier evaluated per probe.
        new ServerSettingsExchange(new ProviderAccess(), context -> callerIsAdmin.get()).register();
        CommunicationManager.setServerSyncPolicySource(() -> provider.getValue().toSyncPolicy());
        CommunicationManager.enableServerHandshake();
    }

    @AfterEach
    void tearDown() {
        CommunicationManager.resetForTesting();
    }

    @Test
    void aNonAdministratorsWriteIsRefusedAndChangesNothing() {
        callerIsAdmin.set(false);

        ServerSettingsPayload answer = write(INTRUDER_ID, new ServerSettingsWritePayload(
                7L, true, "https://evil.example", true));

        assertThat(answer.status).isEqualTo(ServerSettingsStatus.DENIED);
        assertThat(answer.correlationId).isEqualTo(7L);
        assertThat(answer.editable).isFalse();
        assertThat(provider.getValue().externalServerAddress()).isNotEqualTo("https://evil.example");
        assertThat(provider.getValue().externalFallbackEnabled()).isFalse();
    }

    @Test
    void aClientSuppliedAdminFlagOnTheWireIsIgnored() {
        callerIsAdmin.set(false);
        // A hand-rolled write payload carrying every flag a hopeful attacker might imagine the server
        // reads back off the packet. Gson drops unknown fields on decode, so these never even reach a
        // decision - the permission answer comes from the connection, and only from there.
        Map<String, Object> forged = new HashMap<>();
        forged.put("correlationId", 11L);
        forged.put("enableExternalFallback", true);
        forged.put("externalServerAddress", "https://evil.example");
        forged.put("preferExternalTransport", true);
        forged.put("editable", true);
        forged.put("admin", true);
        forged.put("permissionLevel", 4);

        ServerSettingsPayload answer = dispatchWrite(INTRUDER_ID, PayloadCodec.encode(forged, true));

        assertThat(answer.status).isEqualTo(ServerSettingsStatus.DENIED);
        assertThat(answer.editable).isFalse();
        assertThat(provider.getValue().externalServerAddress()).isNotEqualTo("https://evil.example");
    }

    @Test
    void aThrowingPermissionBackendFailsClosed() {
        CommunicationManager.resetForTesting();
        NetworkSerializer.setGson(new Gson());
        new ServerSettingsExchange(new ProviderAccess(), context -> {
            throw new IllegalStateException("permission backend is down");
        }).register();

        ServerSettingsPayload answer = write(ADMIN_ID, new ServerSettingsWritePayload(
                12L, true, "https://relay.example", false));

        assertThat(answer.status).isEqualTo(ServerSettingsStatus.DENIED);
        assertThat(provider.getValue().externalServerAddress()).isNotEqualTo("https://relay.example");
    }

    @Test
    void aNonAdministratorReadGetsAReadOnlySnapshotRatherThanARefusal() {
        callerIsAdmin.set(false);

        ServerSettingsPayload answer = request(INTRUDER_ID, 1L);

        // Not refused on purpose: the same three values are advertised unsolicited to every joining
        // client in the capability handshake, so withholding them here would buy nothing but a worse UI.
        assertThat(answer.status).isEqualTo(ServerSettingsStatus.OK);
        assertThat(answer.editable).isFalse();
        assertThat(answer.externalServerAddress).isEqualTo(provider.getValue().externalServerAddress());
    }

    @Test
    void anAdministratorsReadIsMarkedEditable() {
        callerIsAdmin.set(true);

        assertThat(request(ADMIN_ID, 2L).editable).isTrue();
    }

    @Test
    void anAdministratorsMalformedAddressIsRefused() {
        callerIsAdmin.set(true);

        ServerSettingsPayload answer = write(ADMIN_ID, new ServerSettingsWritePayload(
                3L, true, "not a url at all", false));

        assertThat(answer.status).isEqualTo(ServerSettingsStatus.INVALID_ADDRESS);
        // Still editable: this player IS an admin, they just submitted something unusable.
        assertThat(answer.editable).isTrue();
        assertThat(provider.getValue().externalFallbackEnabled()).isFalse();
    }

    @Test
    void aNonHttpSchemeIsRefused() {
        callerIsAdmin.set(true);

        assertThat(write(ADMIN_ID, new ServerSettingsWritePayload(4L, true, "ftp://relay.example", false))
                .status).isEqualTo(ServerSettingsStatus.INVALID_ADDRESS);
        assertThat(write(ADMIN_ID, new ServerSettingsWritePayload(5L, true, "javascript:alert(1)", false))
                .status).isEqualTo(ServerSettingsStatus.INVALID_ADDRESS);
    }

    @Test
    void aBareHostIsStoredTheWayTheTransportWouldDialIt() {
        callerIsAdmin.set(true);

        ServerSettingsPayload answer = write(ADMIN_ID, new ServerSettingsWritePayload(
                6L, true, "relay.example:8080/", false));

        assertThat(answer.status).isEqualTo(ServerSettingsStatus.APPLIED);
        // RelayEndpoints.base() prefixes a bare host with http:// and strips trailing slashes; storing
        // the normalised form keeps config, advertisement and dialled URI the same string.
        assertThat(answer.externalServerAddress).isEqualTo("http://relay.example:8080");
    }

    @Test
    void clearingTheAddressIsAnAcceptedWriteNotAMalformedOne() {
        callerIsAdmin.set(true);

        ServerSettingsPayload answer = write(ADMIN_ID, new ServerSettingsWritePayload(
                8L, false, "   ", false));

        assertThat(answer.status).isEqualTo(ServerSettingsStatus.APPLIED);
        assertThat(answer.externalServerAddress).isEmpty();
        assertThat(probe().externalServerAddress()).isNull();
    }

    @Test
    void anAcceptedWriteIsPersistedAndReAdvertisedToTheNextHandshake() throws Exception {
        assertThat(probe().externalServerAddress()).isNotEqualTo("https://relay.example");
        callerIsAdmin.set(true);

        ServerSettingsPayload answer = write(ADMIN_ID, new ServerSettingsWritePayload(
                9L, true, "https://relay.example/", true));

        assertThat(answer.status).isEqualTo(ServerSettingsStatus.APPLIED);
        assertThat(answer.editable).isTrue();
        // On disk...
        String json = Files.readString(configDirectory.resolve("eunomia-server.json"));
        assertThat(json).contains("https://relay.example");
        // ...and advertised to a client that probes afterwards, with nothing re-registered in between:
        // the policy source is a supplier read per probe.
        ServerSyncPolicy advertised = probe();
        assertThat(advertised.enableExternalFallback()).isEqualTo(Boolean.TRUE);
        assertThat(advertised.externalServerAddress()).isEqualTo("https://relay.example");
        assertThat(advertised.preferExternalTransport()).isEqualTo(Boolean.TRUE);
    }

    private ServerSettingsPayload request(UUID sender, long correlationId) {
        Captor captor = new Captor(sender);
        CommunicationManager.dispatchServerboundRaw(
                AdminPackets.SERVER_SETTINGS_REQUEST.channelKey(),
                PayloadCodec.encode(new ServerSettingsRequestPayload(correlationId), true),
                captor);
        return captor.decode(AdminPackets.SERVER_SETTINGS.channelKey(), ServerSettingsPayload.class);
    }

    private ServerSettingsPayload write(UUID sender, ServerSettingsWritePayload payload) {
        return dispatchWrite(sender, PayloadCodec.encode(payload, true));
    }

    private ServerSettingsPayload dispatchWrite(UUID sender, byte[] wire) {
        Captor captor = new Captor(sender);
        CommunicationManager.dispatchServerboundRaw(
                AdminPackets.SERVER_SETTINGS_WRITE.channelKey(), wire, captor);
        return captor.decode(AdminPackets.SERVER_SETTINGS.channelKey(), ServerSettingsPayload.class);
    }

    /** What a client joining right now would be told about this server's policy. */
    private ServerSyncPolicy probe() {
        Captor captor = new Captor(ADMIN_ID);
        CommunicationManager.dispatchServerboundRaw(
                HandshakePackets.HELLO.channelKey(),
                PayloadCodec.encode(new ClientHelloPayload(HandshakePackets.PROTOCOL_VERSION), true),
                captor);
        return captor.decode(HandshakePackets.HELLO_ACK.channelKey(), ServerHelloPayload.class).syncPolicy();
    }

    /** Storage that is the very value the handshake's policy supplier reads, exactly as on the loaders. */
    private final class ProviderAccess implements ServerSettingsAccess {

        @Override
        public EunomiaServerConfig current() {
            return provider.getValue();
        }

        @Override
        public void persist(EunomiaServerConfig updated) {
            provider.updateAndSave(updated);
        }
    }

    /**
     * A server context that keeps replies as wire bytes rather than as objects, so every assertion above
     * is about what a client would actually decode - the codec is part of what is under test.
     */
    private static final class Captor implements ServerContext {

        private final UUID sender;
        private final Map<String, byte[]> replies = new HashMap<>();

        private Captor(UUID sender) {
            this.sender = sender;
        }

        @Override
        public UUID senderId() {
            return sender;
        }

        @Override
        public String senderName() {
            return "player-" + sender.toString().substring(0, 8);
        }

        @Override
        public <T> void reply(PacketType<T> type, T data) {
            replies.put(type.channelKey(), PayloadCodec.encode(data, false));
        }

        private <T> T decode(String channelKey, Class<T> type) {
            byte[] wire = replies.get(channelKey);
            assertThat(wire).as("no reply was sent on %s", channelKey).isNotNull();
            return PayloadCodec.decode(wire, type);
        }
    }
}
