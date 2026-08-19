package de.zannagh.eunomia.keyed;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.packets.KeyedPacket;
import de.zannagh.eunomia.networking.serialization.NetworkSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * A server-side, in-memory dictionary of {@code V} keyed by a composite {@link KeyPath} - the
 * game-agnostic generalization of a mod hand-rolling a {@code HashMap<Id, Value>}. Every key in one
 * store has the same {@link #keyDepth() depth} (its number of primary-key segments), so the store is a
 * tree of uniform height: depth 1 is a flat "keyed by id" map, depth 2+ nests
 * ({@code KeyPath.of(player, "armor", "boots")}).
 * <p>
 * It persists to nested JSON objects keyed by the path segments - {@code { "<player>": { "armor": {
 * "boots": {..} } } }} - through the shared eunomia {@link Gson}, so the same type adapters that encode
 * a payload on the wire also persist it here. The depth makes load unambiguous: the walker descends
 * exactly {@code keyDepth} object levels and binds each leaf to {@code V}.
 * <p>
 * Subclasses can hook {@link #beforeStore} to normalize an entry as it lands (the player store re-stamps
 * a config's id from its key and applies pending migrations).
 *
 * @param <V> the value stored at each leaf
 * @since 0.1.0
 */
public class KeyedStore<V> {

    private static final Logger LOGGER = LoggerFactory.getLogger("eunomia-keyed");

    private final Map<KeyPath, V> entries = new ConcurrentHashMap<>();

    private final int keyDepth;

    private final Class<V> valueClass;

    private final Function<KeyPath, V> defaultFactory;

    private final Gson gson;

    /**
     * @param keyDepth   the number of segments in every key (>= 1); the fixed arity of the store's primary key.
     * @param valueClass the concrete value type, needed to bind leaves back from JSON where {@code V} is erased.
     */
    public KeyedStore(int keyDepth, Class<V> valueClass) {
        this(keyDepth, valueClass, null, null);
    }

    /**
     * @param defaultFactory builds a fresh default for an absent key; required by {@link #getOrCreate} and
     *                       {@link #getOrDefault}, may be null when those are never called.
     */
    public KeyedStore(int keyDepth, Class<V> valueClass, Function<KeyPath, V> defaultFactory) {
        this(keyDepth, valueClass, defaultFactory, null);
    }

    /**
     * @param gson the {@link Gson} used for persistence; when null the shared {@link NetworkSerializer#gson()}
     *             is resolved lazily per (de)serialization, so a store built before the loader installs its
     *             enriched Gson still works.
     */
    public KeyedStore(int keyDepth, Class<V> valueClass, Function<KeyPath, V> defaultFactory, Gson gson) {
        if (keyDepth < 1) {
            throw new IllegalArgumentException("keyDepth must be >= 1, was " + keyDepth);
        }
        this.keyDepth = keyDepth;
        this.valueClass = Objects.requireNonNull(valueClass, "valueClass");
        this.defaultFactory = defaultFactory;
        this.gson = gson;
    }

    /** The fixed number of segments every key in this store carries. */
    public int keyDepth() {
        return keyDepth;
    }

    /** The concrete value type, e.g. for a mirror store to bind JSON entries back to {@code V}. */
    public Class<V> valueClass() {
        return valueClass;
    }

    /**
     * Atomically-ish replaces the whole store with {@code newEntries} (clear then put-each, each passing through
     * {@link #beforeStore} and depth validation). Used to apply a replicated snapshot into a mirror store.
     */
    public void replaceAll(Map<KeyPath, V> newEntries) {
        clear();
        newEntries.forEach(this::put);
    }

    /**
     * Wires a self-keyed serverbound packet into this store: whenever a client sends {@code type}, the payload
     * is stored under its own {@link Keyed#keyPath()}. This is the one-call server-side handling - declare a
     * keyed packet, hand it to the store, done. Returns {@code this} for chaining.
     */
    public KeyedStore<V> handleOn(KeyedPacket<? extends V> type) {
        // KeyedPacket constrains its payload to Keyed, so the cast is always safe; it keeps the wiring valid
        // regardless of how the wildcard capture narrows the payload type at this call site.
        CommunicationManager.onServerReceive(type, (payload, context) -> put(((Keyed) payload).keyPath(), payload));
        return this;
    }

    /** The stored value for {@code key}, if any. Never creates one. */
    public Optional<V> get(KeyPath key) {
        return Optional.ofNullable(entries.get(requireDepth(key)));
    }

    /** Whether a value is stored under {@code key}. */
    public boolean contains(KeyPath key) {
        return entries.containsKey(requireDepth(key));
    }

    /** The value for {@code key}, creating, storing and returning a default when absent. */
    public V getOrCreate(KeyPath key) {
        return entries.computeIfAbsent(requireDepth(key), this::createDefault);
    }

    /** The value for {@code key}, or a fresh (unstored) default when absent - a read-only fallback. */
    public V getOrDefault(KeyPath key) {
        V existing = entries.get(requireDepth(key));
        return existing != null ? existing : createDefault(key);
    }

    /**
     * Stores {@code value} under {@code key}, passing it through {@link #beforeStore} first. Returns the stored
     * instance, which may differ from the argument if the hook produced a new one.
     */
    public V put(KeyPath key, V value) {
        requireDepth(key);
        Objects.requireNonNull(value, "value");
        V stored = beforeStore(key, value);
        entries.put(key, stored);
        return stored;
    }

    /** Removes and returns the value under {@code key}, if one was stored. */
    public Optional<V> remove(KeyPath key) {
        return Optional.ofNullable(entries.remove(requireDepth(key)));
    }

    /** An immutable snapshot of the whole store, keyed by full path. */
    public Map<KeyPath, V> snapshot() {
        return Map.copyOf(entries);
    }

    /** An immutable snapshot of the stored values. */
    public Collection<V> values() {
        return List.copyOf(entries.values());
    }

    /**
     * The entries whose key is under {@code prefix} - a view of one subtree. A depth-3 store queried with a
     * one-segment prefix yields every leaf beneath that branch, keyed by full (absolute) path.
     */
    public Map<KeyPath, V> subtree(KeyPath prefix) {
        Map<KeyPath, V> out = new LinkedHashMap<>();
        for (Map.Entry<KeyPath, V> entry : entries.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                out.put(entry.getKey(), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(out);
    }

    /** The immediate child segments directly under {@code prefix} - the next level of the tree, sorted. */
    public SortedSet<String> childKeys(KeyPath prefix) {
        SortedSet<String> out = new TreeSet<>();
        for (KeyPath key : entries.keySet()) {
            if (key.length() > prefix.length() && key.startsWith(prefix)) {
                out.add(key.segment(prefix.length()));
            }
        }
        return out;
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public void clear() {
        entries.clear();
    }

    /**
     * Hook applied to every value as it is stored (via {@link #put}, {@link #getOrCreate}, {@link #getOrDefault}
     * and JSON load). The default returns the value unchanged; subclasses override to normalize against the key.
     */
    protected V beforeStore(KeyPath key, V value) {
        return value;
    }

    // --- persistence -------------------------------------------------------------------------------------

    /** Serializes the whole store to a single nested JSON object keyed by the path segments. */
    public String toJson() {
        List<KeyPath> keys = new ArrayList<>(entries.keySet());
        Collections.sort(keys);
        Map<String, Object> root = new LinkedHashMap<>();
        for (KeyPath key : keys) {
            branchFor(root, key).put(key.segment(keyDepth - 1), entries.get(key));
        }
        return serializer().toJson(root);
    }

    /**
     * Replaces the store's contents with the entries parsed from {@code json}, healing each through
     * {@link #beforeStore}. A blank/null (or non-object) input clears the store.
     */
    public void applyJson(String json) {
        clear();
        if (json == null || json.isBlank()) {
            return;
        }
        JsonElement parsed = JsonParser.parseString(json);
        if (parsed.isJsonObject()) {
            walk(parsed.getAsJsonObject(), KeyPath.root(), serializer());
        }
    }

    /**
     * Loads the store from {@code file}, or leaves it empty if the file does not exist. IO/parse failures are
     * logged and swallowed so a corrupt file can never take down the server on start.
     */
    public void loadFrom(Path file) {
        try {
            if (Files.exists(file)) {
                applyJson(Files.readString(file, StandardCharsets.UTF_8));
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Failed to load keyed store from {}; starting empty.", file, e);
        }
    }

    /** Writes the store to {@code file}, creating parent directories as needed. */
    public void saveTo(Path file) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, toJson(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("Failed to save keyed store to {}.", file, e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> branchFor(Map<String, Object> root, KeyPath key) {
        Map<String, Object> node = root;
        for (int i = 0; i < keyDepth - 1; i++) {
            node = (Map<String, Object>) node.computeIfAbsent(key.segment(i), k -> new LinkedHashMap<String, Object>());
        }
        return node;
    }

    private void walk(JsonObject node, KeyPath prefix, Gson resolved) {
        for (Map.Entry<String, JsonElement> child : node.entrySet()) {
            KeyPath path = prefix.child(child.getKey());
            if (path.length() == keyDepth) {
                V value = resolved.fromJson(child.getValue(), valueClass);
                if (value != null) {
                    put(path, value);
                }
            } else if (child.getValue().isJsonObject()) {
                walk(child.getValue().getAsJsonObject(), path, resolved);
            }
        }
    }

    private V createDefault(KeyPath key) {
        if (defaultFactory == null) {
            throw new IllegalStateException("No default factory configured for this keyed store");
        }
        V fresh = defaultFactory.apply(key);
        if (fresh == null) {
            throw new IllegalStateException("Default factory returned null for key " + key);
        }
        return beforeStore(key, fresh);
    }

    private KeyPath requireDepth(KeyPath key) {
        Objects.requireNonNull(key, "key");
        if (key.length() != keyDepth) {
            throw new IllegalArgumentException(
                    "Key " + key + " has " + key.length() + " segments but this store keys on " + keyDepth);
        }
        return key;
    }

    protected Gson serializer() {
        Gson resolved = gson != null ? gson : NetworkSerializer.gson();
        if (resolved == null) {
            throw new IllegalStateException(
                    "No Gson available for KeyedStore; pass one to the constructor or install one via NetworkSerializer.setGson(...) first.");
        }
        return resolved;
    }
}
