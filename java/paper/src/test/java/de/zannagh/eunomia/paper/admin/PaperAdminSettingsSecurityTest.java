package de.zannagh.eunomia.paper.admin;

import com.google.gson.Gson;
import de.zannagh.eunomia.networking.admin.AdminPackets;
import de.zannagh.eunomia.networking.admin.ServerSettingsPayload;
import de.zannagh.eunomia.networking.admin.ServerSettingsRequestPayload;
import de.zannagh.eunomia.networking.admin.ServerSettingsStatus;
import de.zannagh.eunomia.networking.admin.ServerSettingsWritePayload;
import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.serialization.NetworkSerializer;
import de.zannagh.eunomia.networking.serialization.PayloadCodec;
import de.zannagh.eunomia.paper.config.PaperServerConfig;
import de.zannagh.eunomia.paper.net.PaperServerContext;
import de.zannagh.eunomia.paper.net.PaperServerTransport;
import de.zannagh.eunomia.paper.perm.PermissionResolver;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The Paper half of the administrative settings channel, proven to enforce the same rules as the loaders -
 * because it is the same {@code ServerSettingsExchange}, and the only thing that could go wrong here is the
 * plugin binding it to the wrong permission answer or the wrong config.
 *
 * <p>Hermetic: no running server, a mocked Bukkit {@code Player} and {@code Plugin}, a real
 * {@link PaperServerConfig} on a temp directory, and real wire bytes both in (through
 * {@code dispatchServerboundRaw}) and out (captured off {@code sendPluginMessage}). The LuckPerms API is
 * absent from the test classpath, so this also re-proves the soft dependency along the way.</p>
 */
class PaperAdminSettingsSecurityTest {

    private static final Logger LOGGER = Logger.getLogger(PaperAdminSettingsSecurityTest.class.getName());
    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d4");

    @TempDir
    Path dataFolder;

    private PaperServerConfig config;
    private Plugin plugin;
    private PaperServerTransport transport;

    @BeforeEach
    void setUp() {
        CommunicationManager.resetForTesting();
        NetworkSerializer.setGson(new Gson());
        plugin = mock(Plugin.class);
        lenient().when(plugin.getLogger()).thenReturn(LOGGER);
        transport = new PaperServerTransport(plugin);
        CommunicationManager.setServerTransport(transport);
        config = PaperServerConfig.loadFrom(dataFolder, LoggerFactory.getLogger(getClass()));
        config.install();
        CommunicationManager.enableServerHandshake();
    }

    @AfterEach
    void tearDown() {
        CommunicationManager.resetForTesting();
    }

    @Test
    void aPlayerWithoutTheAdminNodeCannotWriteTheServersCloudSyncSettings() throws Exception {
        register(false);

        ServerSettingsPayload answer = write(player(false, false), new ServerSettingsWritePayload(
                1L, true, "https://evil.example", true));

        assertEquals(ServerSettingsStatus.DENIED, answer.status);
        assertFalse(answer.editable);
        assertFalse(config.value().externalFallbackEnabled());
        assertFalse(Files.readString(dataFolder.resolve(PaperServerConfig.FILE_NAME))
                .contains("evil.example"));
    }

    @Test
    void aForgedAdminFlagInTheSubmittedJsonChangesNothing() {
        register(false);
        Map<String, Object> forged = new HashMap<>();
        forged.put("correlationId", 2L);
        forged.put("enableExternalFallback", true);
        forged.put("externalServerAddress", "https://evil.example");
        forged.put("editable", true);
        forged.put("admin", true);

        ServerSettingsPayload answer = dispatchWrite(player(false, false),
                PayloadCodec.encode(forged, true));

        assertEquals(ServerSettingsStatus.DENIED, answer.status);
        assertFalse(config.value().externalFallbackEnabled());
    }

    @Test
    void anOperatorsWriteIsAppliedPersistedAndAdvertisedFromThereOn() throws Exception {
        register(false);

        ServerSettingsPayload answer = write(player(true, false), new ServerSettingsWritePayload(
                3L, true, "relay.example/", true));

        assertEquals(ServerSettingsStatus.APPLIED, answer.status);
        assertTrue(answer.editable);
        // Normalised exactly as the transport would dial it, then persisted...
        assertEquals("http://relay.example", answer.externalServerAddress);
        assertTrue(Files.readString(dataFolder.resolve(PaperServerConfig.FILE_NAME))
                .contains("http://relay.example"));
        // ...and picked up by the policy supplier the handshake reads per probe, with nothing re-registered.
        assertEquals("http://relay.example", config.policy().externalServerAddress());
        assertEquals(Boolean.TRUE, config.policy().enableExternalFallback());
    }

    @Test
    void aNonAdministratorStillGetsAReadOnlySnapshotOnRead() {
        register(false);

        ServerSettingsPayload answer = request(player(false, false), 4L);

        assertEquals(ServerSettingsStatus.OK, answer.status);
        assertFalse(answer.editable);
    }

    @Test
    void theSuperpermsNodeIsEnoughToEdit() {
        register(false);

        assertTrue(request(player(false, true), 5L).editable);
        assertEquals(ServerSettingsStatus.APPLIED, write(player(false, true),
                new ServerSettingsWritePayload(6L, false, "https://relay.example", false)).status);
    }

    // ── Harness ─────────────────────────────────────────────────────────────────────────────────

    /** Registers the exchange exactly as {@code EunomiaPaperPlugin#onEnable} does. */
    private void register(boolean luckPermsPresent) {
        PaperServerSettings.register(config, new PermissionResolver(LOGGER, () -> luckPermsPresent));
    }

    private Player player(boolean op, boolean hasNode) {
        Player player = mock(Player.class);
        lenient().when(player.isOp()).thenReturn(op);
        lenient().when(player.hasPermission(PermissionResolver.ADMIN_PERMISSION)).thenReturn(hasNode);
        lenient().when(player.getUniqueId()).thenReturn(PLAYER_ID);
        lenient().when(player.getName()).thenReturn("tester");
        lenient().when(player.getListeningPluginChannels())
                .thenReturn(Set.of(AdminPackets.SERVER_SETTINGS.channelKey()));
        return player;
    }

    private ServerSettingsPayload request(Player player, long correlationId) {
        CommunicationManager.dispatchServerboundRaw(
                AdminPackets.SERVER_SETTINGS_REQUEST.channelKey(),
                PayloadCodec.encode(new ServerSettingsRequestPayload(correlationId), true),
                new PaperServerContext(player, transport));
        return captureAnswer(player);
    }

    private ServerSettingsPayload write(Player player, ServerSettingsWritePayload payload) {
        return dispatchWrite(player, PayloadCodec.encode(payload, true));
    }

    private ServerSettingsPayload dispatchWrite(Player player, byte[] wire) {
        CommunicationManager.dispatchServerboundRaw(
                AdminPackets.SERVER_SETTINGS_WRITE.channelKey(), wire, new PaperServerContext(player, transport));
        return captureAnswer(player);
    }

    /** Decodes the last plugin message the server actually put on the wire for this player. */
    private ServerSettingsPayload captureAnswer(Player player) {
        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(player, atLeastOnce()).sendPluginMessage(
                any(), eq(AdminPackets.SERVER_SETTINGS.channelKey()), bytes.capture());
        byte[] wire = bytes.getValue();
        assertNotNull(wire, "the server sent no answer");
        return PayloadCodec.decode(wire, ServerSettingsPayload.class);
    }
}
