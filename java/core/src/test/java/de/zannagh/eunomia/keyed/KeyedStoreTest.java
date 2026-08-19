package de.zannagh.eunomia.keyed;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyedStoreTest {

    private static final Gson GSON = new GsonBuilder().create();

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000a11c");

    /** A minimal keyed leaf value: a label whose own key is a player/slot path. */
    private static final class Slot implements Keyed {
        String label;
        transient KeyPath key;

        Slot(KeyPath key, String label) {
            this.key = key;
            this.label = label;
        }

        @Override
        public KeyPath keyPath() {
            return key;
        }
    }

    private KeyedStore<Slot> depthTwoStore() {
        return new KeyedStore<>(2, Slot.class, key -> new Slot(key, "default"), GSON);
    }

    @Test
    void rejectsKeysOfTheWrongDepth() {
        var store = depthTwoStore();
        assertThrows(IllegalArgumentException.class, () -> store.put(KeyPath.of(ALICE), new Slot(KeyPath.of(ALICE), "x")));
        assertThrows(IllegalArgumentException.class, () -> store.get(KeyPath.of(ALICE, "a", "b")));
    }

    @Test
    void putGetRemoveByCompositeKey() {
        var store = depthTwoStore();
        KeyPath boots = KeyPath.of(ALICE, "boots");
        store.put(boots, new Slot(boots, "hidden"));
        assertEquals("hidden", store.get(boots).orElseThrow().label);
        assertTrue(store.contains(boots));
        assertEquals("hidden", store.remove(boots).orElseThrow().label);
        assertFalse(store.contains(boots));
    }

    @Test
    void getOrCreateStoresAndIsStable() {
        var store = depthTwoStore();
        KeyPath key = KeyPath.of(ALICE, "helmet");
        Slot created = store.getOrCreate(key);
        assertEquals("default", created.label);
        assertSame(created, store.getOrCreate(key));
        assertEquals(1, store.size());
    }

    @Test
    void nestedJsonRoundTripPreservesTree() {
        var store = depthTwoStore();
        store.put(KeyPath.of(ALICE, "boots"), new Slot(KeyPath.of(ALICE, "boots"), "b"));
        store.put(KeyPath.of(ALICE, "helmet"), new Slot(KeyPath.of(ALICE, "helmet"), "h"));

        String json = store.toJson();
        assertTrue(json.contains("\"" + ALICE + "\""), "top level is keyed by the first segment");
        assertTrue(json.contains("\"boots\""), "second level is keyed by the second segment");

        var reloaded = depthTwoStore();
        reloaded.applyJson(json);
        assertEquals(2, reloaded.size());
        assertEquals("b", reloaded.get(KeyPath.of(ALICE, "boots")).orElseThrow().label);
        assertEquals("h", reloaded.get(KeyPath.of(ALICE, "helmet")).orElseThrow().label);
    }

    @Test
    void subtreeAndChildKeysNavigateTheTree() {
        var store = depthTwoStore();
        UUID bob = UUID.fromString("00000000-0000-0000-0000-00000000b0b0");
        store.put(KeyPath.of(ALICE, "boots"), new Slot(KeyPath.of(ALICE, "boots"), "b"));
        store.put(KeyPath.of(ALICE, "helmet"), new Slot(KeyPath.of(ALICE, "helmet"), "h"));
        store.put(KeyPath.of(bob, "boots"), new Slot(KeyPath.of(bob, "boots"), "bb"));

        assertEquals(2, store.subtree(KeyPath.of(ALICE)).size());
        assertEquals(java.util.Set.of("boots", "helmet"), store.childKeys(KeyPath.of(ALICE)));
    }

    @Test
    void blankJsonClearsTheStore() {
        var store = depthTwoStore();
        store.put(KeyPath.of(ALICE, "boots"), new Slot(KeyPath.of(ALICE, "boots"), "b"));
        store.applyJson("   ");
        assertTrue(store.isEmpty());
    }
}
