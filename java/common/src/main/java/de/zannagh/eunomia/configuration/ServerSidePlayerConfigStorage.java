package de.zannagh.eunomia.configuration;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import de.zannagh.eunomia.Eunomia;
import de.zannagh.eunomia.networking.CommunicationManager;
import de.zannagh.eunomia.networking.PacketType;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * A server-side, in-memory store of {@link PlayerLinkedConfigurationItem}s keyed by player UUID - the eunomia
 * replacement for a mod hand-rolling its own {@code HashMap<UUID, PlayerConfig>} plus the ad-hoc name-based
 * mirror Armor Hider carried. Lookups are UUID-only; there is deliberately no name index.
 * <p>
 * The store never hands back {@code null} for a known-good query: {@link #getOrCreate} and {@link #getOrDefault}
 * fall back to the configured default factory, and both {@link #put} and healing on load reconcile every entry's
 * own {@link PlayerLinkedConfigurationItem#getPlayerId() player id} with the map key it lives under (the key is
 * authoritative, since server-side the key is the authenticated sender). JSON round-trips through the eunomia
 * {@link Gson} so the exact same config type adapters that serialize a payload on the wire also persist it here.
 *
 * @param <C> the player-linked config type stored per player
 * @since 0.1.0
 */
public final class ServerSidePlayerConfigStorage<C extends PlayerLinkedConfigurationItem<C>> {

    private final Map<UUID, C> configs = new ConcurrentHashMap<>();

    private final Class<C> configClass;

    private final Function<UUID, C> defaultFactory;

    private final Gson gson;

    /**
     * @param configClass    the concrete config type; needed to reconstruct the {@code Map<UUID, C>} from JSON,
     *                       where {@code C} would otherwise be erased.
     * @param defaultFactory builds a fresh default config for a player id; must return a non-null instance whose
     *                       {@code getPlayerId()} is the supplied id (the store re-stamps it defensively anyway).
     */
    public ServerSidePlayerConfigStorage(Class<C> configClass, Function<UUID, C> defaultFactory) {
        this(configClass, defaultFactory, null);
    }

    /**
     * @param gson the {@link Gson} used for persistence; when {@code null}, {@link Eunomia#SERIALIZER} is
     *             resolved lazily at each (de)serialization so a store built before {@link Eunomia#init()} still
     *             works.
     */
    public ServerSidePlayerConfigStorage(Class<C> configClass, Function<UUID, C> defaultFactory, Gson gson) {
        this.configClass = configClass;
        this.defaultFactory = defaultFactory;
        this.gson = gson;
    }

    /**
     * Registers a server-side receiver so that whenever a client sends {@code type}, the payload is stored under
     * the authenticated sender id. This is the one-call wiring a consumer mod needs: declare a packet, hand it to
     * the store, done. Returns {@code this} for chaining.
     */
    public ServerSidePlayerConfigStorage<C> handleOn(PacketType<C> type) {
        CommunicationManager.onServerReceive(type, (payload, context) -> put(context.senderId(), payload));
        return this;
    }

    /** The stored config for {@code playerId}, if any. Never creates one. */
    public Optional<C> get(UUID playerId) {
        return Optional.ofNullable(configs.get(playerId));
    }

    /** Whether a config is stored for {@code playerId}. */
    public boolean contains(UUID playerId) {
        return configs.containsKey(playerId);
    }

    /**
     * The stored config for {@code playerId}, creating, storing and returning a default when absent. Use this on
     * the read-modify-write path where the caller intends to keep the result.
     */
    public C getOrCreate(UUID playerId) {
        return configs.computeIfAbsent(playerId, this::newDefault);
    }

    /**
     * The stored config for {@code playerId}, or a fresh (unstored) default when absent - a read-only fallback
     * for callers that just need a value to render/compare and must not mutate the store.
     */
    public C getOrDefault(UUID playerId) {
        C existing = configs.get(playerId);
        return existing != null ? existing : newDefault(playerId);
    }

    /**
     * Stores {@code config} for {@code playerId}, re-stamping the config's own player id to match the key. Any
     * pending schema migration is applied first. Returns the stored instance (which may differ from the argument
     * if a migration produced a new instance).
     */
    public C put(UUID playerId, C config) {
        if (playerId == null || config == null) {
            throw new IllegalArgumentException("playerId and config must be non-null");
        }
        C stored = config.ensureSchemaFrom(config);
        stored.setPlayerId(playerId);
        configs.put(playerId, stored);
        return stored;
    }

    /** Removes and returns the config for {@code playerId}, if one was stored. */
    public Optional<C> remove(UUID playerId) {
        return Optional.ofNullable(configs.remove(playerId));
    }

    /** An immutable snapshot of the whole store, keyed by player id. */
    public Map<UUID, C> snapshot() {
        return Map.copyOf(configs);
    }

    /** An immutable snapshot of the stored configs. */
    public Collection<C> values() {
        return List.copyOf(configs.values());
    }

    public int size() {
        return configs.size();
    }

    public boolean isEmpty() {
        return configs.isEmpty();
    }

    public void clear() {
        configs.clear();
    }

    // --- persistence -------------------------------------------------------------------------------------

    /** Serializes the whole store to JSON: a single object of {@code playerId -> config}. */
    public String toJson() {
        return serializer().toJson(new HashMap<>(configs), mapType());
    }

    /**
     * Replaces the store's contents with the entries parsed from {@code json}, healing each along the way
     * (dropping null entries, reconciling id with key, applying migrations). A blank/null input clears the store.
     */
    public void applyJson(String json) {
        clear();
        if (json == null || json.isBlank()) {
            return;
        }
        Map<UUID, C> loaded = serializer().fromJson(json, mapType());
        if (loaded == null) {
            return;
        }
        loaded.forEach((key, config) -> {
            if (key != null && config != null) {
                put(key, config);
            }
        });
    }

    /**
     * Loads the store from {@code file}, or leaves it empty if the file does not exist. IO/parse failures are
     * logged and swallowed so a corrupt file can never take down the server on start - the store simply comes up
     * empty and repopulates as clients reconnect.
     */
    public void loadFrom(Path file) {
        try {
            if (Files.exists(file)) {
                applyJson(Files.readString(file, StandardCharsets.UTF_8));
            }
        } catch (IOException | RuntimeException e) {
            Eunomia.LOGGER.error("Failed to load player config store from {}; starting empty.", file, e);
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
            Eunomia.LOGGER.error("Failed to save player config store to {}.", file, e);
        }
    }

    private C newDefault(UUID playerId) {
        C fresh = defaultFactory.apply(playerId);
        if (fresh == null) {
            throw new IllegalStateException("Default factory returned null for player " + playerId);
        }
        fresh.setPlayerId(playerId);
        return fresh;
    }

    private Type mapType() {
        return TypeToken.getParameterized(HashMap.class, UUID.class, configClass).getType();
    }

    private Gson serializer() {
        Gson resolved = gson != null ? gson : Eunomia.SERIALIZER;
        if (resolved == null) {
            throw new IllegalStateException(
                    "No Gson available for ServerSidePlayerConfigStorage; pass one to the constructor or call Eunomia.init() first.");
        }
        return resolved;
    }
}
