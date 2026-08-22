package de.zannagh.eunomia.keyed;

import com.google.gson.Gson;
import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.comms.ServerTransport;
import de.zannagh.eunomia.networking.packets.ClientContext;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplicatedKeyedStoreTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000a11c");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000b0b0");
    private static final UUID CAROL = UUID.fromString("00000000-0000-0000-0000-00000000ca01");

    private static final KeyedPacket<TestEntry> CHANNEL =
            KeyedPacket.keyedBidirectional("test", "entry", TestEntry.class);

    /** A replicated leaf keyed by its player id (depth 1). */
    private static final class TestEntry implements Replicated {
        UUID player;
        int value;

        TestEntry() {
        }

        TestEntry(UUID player, int value) {
            this.player = player;
            this.value = value;
        }

        @Override
        public KeyPath keyPath() {
            return KeyPath.of(player);
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

    private record TestClientContext() implements ClientContext {
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

    private ReplicatedKeyedStore<TestEntry> serverStore() {
        return new ReplicatedKeyedStore<>(1, TestEntry.class, CHANNEL).enableServer();
    }

    @Test
    void inboundUpdateIsStoredAndRelayedToOthers() {
        ReplicatedKeyedStore<TestEntry> store = serverStore();

        boolean handled = CommunicationManager.dispatchServerbound(
                CHANNEL.channelKey(), new TestEntry(ALICE, 5), new TestServerContext(ALICE, "alice"));

        assertTrue(handled);
        assertEquals(5, store.get(KeyPath.of(ALICE)).orElseThrow().value);
        // Relayed to everyone except the sender, carrying the stored value.
        assertEquals(1, transport.sent.size());
        Sent relay = transport.sent.get(0);
        assertEquals(ALICE, relay.target());
        assertEquals("except:" + CHANNEL.channelKey(), relay.channel());
        assertEquals(5, ((TestEntry) relay.data()).value);
    }

    @Test
    void pushSnapshotSendsBatchToNewcomer() {
        ReplicatedKeyedStore<TestEntry> store = serverStore();
        store.put(KeyPath.of(ALICE), new TestEntry(ALICE, 3));
        store.put(KeyPath.of(BOB), new TestEntry(BOB, 9));
        transport.sent.clear();

        store.pushSnapshotTo(CAROL);

        assertEquals(1, transport.sent.size());
        Sent push = transport.sent.get(0);
        assertEquals(CAROL, push.target());
        assertEquals(StoreSyncPackets.STORE_SYNC.channelKey(), push.channel());
        StoreSyncPayload sync = assertInstanceOf(StoreSyncPayload.class, push.data());
        assertEquals(CHANNEL.channelKey(), sync.channel);
        assertEquals(2, sync.entries.size());
        assertTrue(sync.entries.get(ALICE.toString()).contains("\"value\":3"));
        assertTrue(sync.entries.get(BOB.toString()).contains("\"value\":9"));
    }

    @Test
    void clientMirrorAppliesSnapshotThenSingleUpdate() {
        ReplicatedClientStore<TestEntry> mirror =
                new ReplicatedClientStore<>(1, TestEntry.class, CHANNEL).enableClient();

        StoreSyncPayload sync = new ReplicatedKeyedStore<>(1, TestEntry.class, CHANNEL) {{
            put(KeyPath.of(ALICE), new TestEntry(ALICE, 7));
        }}.snapshotPayload();

        CommunicationManager.dispatchClientbound(
                StoreSyncPackets.STORE_SYNC.channelKey(), sync, new TestClientContext());
        assertEquals(7, mirror.store().get(KeyPath.of(ALICE)).orElseThrow().value);

        CommunicationManager.dispatchClientbound(
                CHANNEL.channelKey(), new TestEntry(ALICE, 9), new TestClientContext());
        assertEquals(9, mirror.store().get(KeyPath.of(ALICE)).orElseThrow().value);
    }

    /**
     * Regression: {@code resetForTesting()} must also unregister the shared {@code store_sync} handler.
     *
     * <p>{@link StoreSyncClient} binds that handler once, guarded by a static flag. Before the reset hook existed,
     * clearing the manager's handler maps left the flag set, so the next {@code enableClient()} skipped rebinding
     * and every inbound snapshot was dropped with no listener and no error. The damage was invisible and
     * order-dependent: whichever test enabled a mirror first passed, and the next one to rely on a snapshot failed
     * on an assertion that pointed at the store rather than at the reset.</p>
     */
    @Test
    void aMirrorEnabledAfterAResetStillReceivesSnapshots() {
        new ReplicatedClientStore<>(1, TestEntry.class, CHANNEL).enableClient();

        // Exactly what a test harness does between cases - and what used to strand the store_sync handler.
        CommunicationManager.resetForTesting();
        NetworkSerializer.setGson(new Gson());

        ReplicatedClientStore<TestEntry> rebound =
                new ReplicatedClientStore<>(1, TestEntry.class, CHANNEL).enableClient();
        StoreSyncPayload sync = new ReplicatedKeyedStore<>(1, TestEntry.class, CHANNEL) {{
            put(KeyPath.of(BOB), new TestEntry(BOB, 11));
        }}.snapshotPayload();

        assertTrue(CommunicationManager.dispatchClientbound(
                        StoreSyncPackets.STORE_SYNC.channelKey(), sync, new TestClientContext()),
                "the store_sync handler must be rebound after a reset, not left on the discarded manager");
        assertEquals(11, rebound.store().get(KeyPath.of(BOB)).orElseThrow().value,
                "a mirror enabled after a reset must still receive snapshots");
    }
}
