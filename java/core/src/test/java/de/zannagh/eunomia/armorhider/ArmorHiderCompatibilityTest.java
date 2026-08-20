package de.zannagh.eunomia.armorhider;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.zannagh.eunomia.configuration.ReplicatedPlayerConfigStore;
import de.zannagh.eunomia.keyed.StoreSyncPayload;
import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.comms.ServerTransport;
import de.zannagh.eunomia.networking.packets.KeyedPacket;
import de.zannagh.eunomia.networking.packets.PacketType;
import de.zannagh.eunomia.networking.packets.ServerContext;
import de.zannagh.eunomia.networking.serialization.NetworkSerializer;
import de.zannagh.eunomia.serialization.JsonSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves eunomia's storage and networking stack understands Armor Hider's real config formats losslessly,
 * against fixture files copied verbatim from a running instance ({@code src/test/resources/armorhider/}):
 * the per-player client config and the server-side per-player dictionary. {@link ArmorHiderConfig} models
 * the flat top-level fields and keeps the deeply-nested blocks as raw {@code JsonObject}s so nothing has to
 * be enumerated key-by-key to prove nothing is dropped.
 */
class ArmorHiderCompatibilityTest {

    private static final Gson GSON = new JsonSerializer().GSON;

    private static final KeyedPacket<ArmorHiderConfig> CHANNEL =
            KeyedPacket.keyedBidirectional("armorhider", "config", ArmorHiderConfig.class);

    private record Sent(UUID target, String channel, Object data) {
    }

    private static final class RecordingServerTransport implements ServerTransport {
        final List<Sent> sent = new ArrayList<>();

        @Override
        public <T> void sendToPlayer(UUID playerId, PacketType<T> type, T data) {
            sent.add(new Sent(playerId, type.channelKey(), data));
        }

        @Override
        public <T> void broadcast(PacketType<T> type, T data) {
            sent.add(new Sent(null, type.channelKey(), data));
        }

        @Override
        public <T> void broadcastExcept(UUID excludedPlayerId, PacketType<T> type, T data) {
            sent.add(new Sent(excludedPlayerId, "except:" + type.channelKey(), data));
        }
    }

    private record TestServerContext(UUID senderId, String senderName) implements ServerContext {
        @Override
        public <T> void reply(PacketType<T> type, T data) {
        }
    }

    private RecordingServerTransport transport;

    @BeforeEach
    void setUp() {
        CommunicationManager.resetForTesting();
        NetworkSerializer.setGson(GSON);
        transport = new RecordingServerTransport();
        CommunicationManager.setServerTransport(transport);
    }

    @AfterEach
    void tearDown() {
        CommunicationManager.resetForTesting();
    }

    private static String resource(String name) {
        try (InputStream in = ArmorHiderCompatibilityTest.class.getResourceAsStream("/armorhider/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private ReplicatedPlayerConfigStore<ArmorHiderConfig> store() {
        return new ReplicatedPlayerConfigStore<>(ArmorHiderConfig.class, id -> new ArmorHiderConfig(), CHANNEL)
                .enableServer();
    }

    @Test
    void clientConfigRoundTripsLosslessly() {
        String original = resource("client-config.json");

        ArmorHiderConfig config = GSON.fromJson(original, ArmorHiderConfig.class);

        assertEquals(0.35, config.helmetOpacity);
        assertEquals("ArmorHiderSmoke", config.playerName);
        assertEquals(UUID.fromString("3e75fff1-9ea7-3b96-a8a5-1ab6ba5848e0"), config.getPlayerId());
        assertEquals(15, config.configVersion);
        assertNotNull(config.exclusionItems);
        assertTrue(config.exclusionItems.has("items"));
        assertNotNull(config.globalPlayerOverride);

        String reserialized = GSON.toJson(config);

        JsonObject originalJson = JsonParser.parseString(original).getAsJsonObject();
        JsonObject reserializedJson = JsonParser.parseString(reserialized).getAsJsonObject();
        assertEquals(originalJson, reserializedJson, "no field was dropped or altered by the round trip");
    }

    @Test
    void serverDictionaryRoundTripsLosslesslyThroughTheStore() {
        JsonObject dictionary = JsonParser.parseString(resource("server-dictionary.json")).getAsJsonObject();
        JsonObject playerConfigs = dictionary.getAsJsonObject("playerConfigs");

        ReplicatedPlayerConfigStore<ArmorHiderConfig> store = store();

        for (Map.Entry<String, JsonElement> entry : playerConfigs.entrySet()) {
            UUID playerId = UUID.fromString(entry.getKey());
            ArmorHiderConfig config = GSON.fromJson(entry.getValue(), ArmorHiderConfig.class);
            store.put(playerId, config);

            ArmorHiderConfig stored = store.get(playerId).orElseThrow();
            assertEquals(entry.getValue(), GSON.toJsonTree(stored), "stored entry matches the source dictionary entry");
            assertTrue(store.byPlayer().containsKey(playerId));
        }

        JsonObject roundTripped = JsonParser.parseString(store.toJson()).getAsJsonObject();
        assertEquals(playerConfigs, roundTripped, "toJson() reproduces the original playerConfigs object exactly");
    }

    @Test
    void clientSendIsIncorporatedUnderTheAuthenticatedSenderAndPublished() {
        UUID sender = UUID.fromString("3e75fff1-9ea7-3b96-a8a5-1ab6ba5848e0");
        UUID otherPlayer = UUID.fromString("00000000-0000-0000-0000-00000000b0b0");
        ArmorHiderConfig config = GSON.fromJson(resource("client-config.json"), ArmorHiderConfig.class);

        ReplicatedPlayerConfigStore<ArmorHiderConfig> store = store();

        boolean handled = CommunicationManager.dispatchServerbound(
                CHANNEL.channelKey(), config, new TestServerContext(sender, "ArmorHiderSmoke"));
        assertTrue(handled);

        ArmorHiderConfig incorporated = store.get(sender).orElseThrow();
        assertEquals(GSON.toJsonTree(config), GSON.toJsonTree(incorporated), "incorporated entry is intact");

        // Published to everyone else on incoming update.
        Sent relay = transport.sent.get(0);
        assertEquals(sender, relay.target());
        assertEquals("except:" + CHANNEL.channelKey(), relay.channel());

        // Published to a newcomer via a full snapshot.
        transport.sent.clear();
        store.pushSnapshotTo(otherPlayer);

        Sent push = transport.sent.get(0);
        StoreSyncPayload sync = assertInstanceOf(StoreSyncPayload.class, push.data());
        assertTrue(sync.entries.containsKey(sender.toString()));
        JsonObject pushedConfig = JsonParser.parseString(sync.entries.get(sender.toString())).getAsJsonObject();
        assertEquals(GSON.toJsonTree(config), pushedConfig, "snapshot entry carries the config intact");
    }
}
