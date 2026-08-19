package de.zannagh.eunomia.configuration;

import com.google.gson.Gson;
import de.zannagh.eunomia.common.SemanticVersion;
import de.zannagh.eunomia.keyed.KeyPath;
import de.zannagh.eunomia.keyed.StoreSyncPackets;
import de.zannagh.eunomia.keyed.StoreSyncPayload;
import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.comms.ServerTransport;
import de.zannagh.eunomia.networking.packets.KeyedPacket;
import de.zannagh.eunomia.networking.packets.PacketType;
import de.zannagh.eunomia.networking.packets.ServerContext;
import de.zannagh.eunomia.networking.serialization.NetworkSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplicatedPlayerConfigStoreTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000a11c");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000b0b0");
    private static final UUID CAROL = UUID.fromString("00000000-0000-0000-0000-00000000ca01");

    private static final KeyedPacket<HideConfig> CHANNEL =
            KeyedPacket.keyedBidirectional("test", "hide", HideConfig.class);

    /** A concrete replicated per-player config: one field, keyed by player UUID via the default keyPath(). */
    public static final class HideConfig extends PlayerLinkedConfigurationItemBase<HideConfig>
            implements ReplicatedPlayerConfig<HideConfig> {

        private static final SemanticVersion VERSION = new SemanticVersion(1, 0, 0, null);

        public int level;

        public HideConfig() {
        }

        public HideConfig(UUID playerId, int level) {
            super(playerId);
            this.level = level;
        }

        @Override
        public HideConfig getValue() {
            return this;
        }

        @Override
        public void setValue(HideConfig newValue) {
            this.level = newValue.level;
            setPlayerId(newValue.getPlayerId());
        }

        @Override
        public HideConfig getDefaultValue() {
            return new HideConfig();
        }

        @Override
        public SemanticVersion getSchemaVersion() {
            return VERSION;
        }

        @Override
        public SemanticVersion getCurrentSchemaVersion() {
            return VERSION;
        }

        @Override
        public HideConfig migrateFrom(HideConfig old) {
            return old;
        }
    }

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
        NetworkSerializer.setGson(new Gson());
        transport = new RecordingServerTransport();
        CommunicationManager.setServerTransport(transport);
    }

    @AfterEach
    void tearDown() {
        CommunicationManager.resetForTesting();
    }

    private ReplicatedPlayerConfigStore<HideConfig> store() {
        return new ReplicatedPlayerConfigStore<>(HideConfig.class, id -> new HideConfig(id, 0), CHANNEL).enableServer();
    }

    @Test
    void storesUnderAuthenticatedSenderNotClientClaimAndRelays() {
        ReplicatedPlayerConfigStore<HideConfig> store = store();

        // The payload lies that it is Bob's, but Alice is the authenticated sender.
        boolean handled = CommunicationManager.dispatchServerbound(
                CHANNEL.channelKey(), new HideConfig(BOB, 42), new TestServerContext(ALICE, "alice"));

        assertTrue(handled);
        assertEquals(42, store.get(ALICE).orElseThrow().level);
        assertEquals(ALICE, store.get(ALICE).orElseThrow().getPlayerId(), "id healed to the authenticated sender");
        assertFalse(store.contains(BOB), "the client-claimed id is ignored");

        Sent relay = transport.sent.get(0);
        assertEquals(ALICE, relay.target());
        assertEquals("except:" + CHANNEL.channelKey(), relay.channel());
        assertEquals(ALICE, ((HideConfig) relay.data()).getPlayerId());
    }

    @Test
    void pushSnapshotIncludesEveryPlayer() {
        ReplicatedPlayerConfigStore<HideConfig> store = store();
        store.put(ALICE, new HideConfig(ALICE, 3));
        store.put(BOB, new HideConfig(BOB, 9));
        transport.sent.clear();

        store.pushSnapshotTo(CAROL);

        Sent push = transport.sent.get(0);
        assertEquals(CAROL, push.target());
        assertEquals(StoreSyncPackets.STORE_SYNC.channelKey(), push.channel());
        StoreSyncPayload sync = assertInstanceOf(StoreSyncPayload.class, push.data());
        assertEquals(2, sync.entries.size());
        assertTrue(sync.entries.containsKey(ALICE.toString()));
        assertTrue(sync.entries.containsKey(BOB.toString()));
    }

    @Test
    void getOrCreateAndByPlayerUseUuidKeys() {
        ReplicatedPlayerConfigStore<HideConfig> store = store();
        HideConfig created = store.getOrCreate(ALICE);
        assertEquals(ALICE, created.getPlayerId());
        assertEquals(1, store.byPlayer().size());
        assertEquals(ALICE, store.byPlayer().keySet().iterator().next());
    }

    @Test
    void replicatedPlayerConfigKeyPathDerivesFromPlayerId() {
        assertEquals(KeyPath.of(BOB), new HideConfig(BOB, 1).keyPath());
    }
}
