package de.zannagh.eunomia.configuration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerSidePlayerConfigStorageTest {

    private static final Gson GSON = new GsonBuilder().create();

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000a11c");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000b0b0");

    private ServerSidePlayerConfigStorage<TestPlayerConfig> newStore() {
        return new ServerSidePlayerConfigStorage<>(
                TestPlayerConfig.class, id -> new TestPlayerConfig(id, 0), GSON);
    }

    @Test
    void getOnEmptyStoreIsAbsentAndCreatesNothing() {
        var store = newStore();
        assertTrue(store.get(ALICE).isEmpty());
        assertFalse(store.contains(ALICE));
        assertEquals(0, store.size());
    }

    @Test
    void getOrDefaultFallsBackWithoutStoring() {
        var store = newStore();
        TestPlayerConfig fallback = store.getOrDefault(ALICE);
        assertNotNull(fallback);
        assertEquals(ALICE, fallback.getPlayerId(), "the fallback is stamped with the requested id");
        assertEquals(0, store.size(), "getOrDefault must not mutate the store");
    }

    @Test
    void getOrCreateFallsBackAndStores() {
        var store = newStore();
        TestPlayerConfig created = store.getOrCreate(ALICE);
        assertEquals(ALICE, created.getPlayerId());
        assertEquals(1, store.size());
        assertSame(created, store.getOrCreate(ALICE), "a second call returns the same stored instance");
    }

    @Test
    void putReconcilesConfigIdWithKey() {
        var store = newStore();
        // The payload claims to be Bob, but it is stored under Alice's authenticated id.
        TestPlayerConfig lying = new TestPlayerConfig(BOB, 42);
        store.put(ALICE, lying);
        assertEquals(ALICE, store.get(ALICE).orElseThrow().getPlayerId());
        assertFalse(store.contains(BOB));
    }

    @Test
    void removeReturnsAndClears() {
        var store = newStore();
        store.put(ALICE, new TestPlayerConfig(ALICE, 7));
        assertEquals(7, store.remove(ALICE).orElseThrow().level);
        assertFalse(store.contains(ALICE));
        assertTrue(store.remove(ALICE).isEmpty());
    }

    @Test
    void jsonRoundTripPreservesEntries() {
        var store = newStore();
        store.put(ALICE, new TestPlayerConfig(ALICE, 3));
        store.put(BOB, new TestPlayerConfig(BOB, 9));

        String json = store.toJson();

        var reloaded = newStore();
        reloaded.applyJson(json);

        assertEquals(2, reloaded.size());
        assertEquals(3, reloaded.get(ALICE).orElseThrow().level);
        assertEquals(9, reloaded.get(BOB).orElseThrow().level);
        assertEquals(ALICE, reloaded.get(ALICE).orElseThrow().getPlayerId());
    }

    @Test
    void applyJsonHealsMismatchedIdToKey() {
        // A hand-written / corrupted store file where the value's own id disagrees with its map key.
        String json = "{\"" + ALICE + "\":{\"level\":5,\"playerId\":\"" + BOB + "\"}}";
        var store = newStore();
        store.applyJson(json);
        TestPlayerConfig healed = store.get(ALICE).orElseThrow();
        assertEquals(ALICE, healed.getPlayerId(), "the map key wins over the serialized id");
        assertEquals(5, healed.level);
    }

    @Test
    void applyJsonWithBlankInputClearsStore() {
        var store = newStore();
        store.put(ALICE, new TestPlayerConfig(ALICE, 1));
        store.applyJson("   ");
        assertTrue(store.isEmpty());
    }
}
