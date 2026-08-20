package de.zannagh.eunomia.configuration;

import com.google.gson.Gson;
import de.zannagh.eunomia.keyed.KeyPath;
import de.zannagh.eunomia.keyed.KeyedStore;
import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.packets.PacketType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * A server-side, in-memory store of {@link PlayerLinkedConfigurationItem}s keyed by player UUID - the eunomia
 * replacement for a mod hand-rolling its own {@code HashMap<UUID, PlayerConfig>} plus the ad-hoc name-based
 * mirror Armor Hider carried. Lookups are UUID-only; there is deliberately no name index.
 * <p>
 * This is the canonical single-segment specialization of the generic {@link KeyedStore}: the player id is a
 * one-segment {@link KeyPath}, so the store inherits all of the persistence, tree and snapshot machinery and
 * only adds the UUID-typed convenience surface. The store never hands back {@code null} for a known-good query
 * ({@link #getOrCreate}/{@link #getOrDefault} fall back to the configured default factory), and {@link #beforeStore}
 * reconciles every entry's own {@link PlayerLinkedConfigurationItem#getPlayerId() player id} with the key it lives
 * under - the key is authoritative, since server-side it is the authenticated sender - after applying any pending
 * schema migration. The on-disk shape is a single object of {@code playerId -> config}.
 *
 * @param <C> the player-linked config type stored per player
 * @since 0.1.0
 */
public final class ServerSidePlayerConfigStorage<C extends PlayerLinkedConfigurationItem<C>> extends KeyedStore<C> {

    /**
     * @param configClass    the concrete config type; needed to reconstruct configs from JSON where {@code C}
     *                       would otherwise be erased.
     * @param defaultFactory builds a fresh default config for a player id; must return a non-null instance (the
     *                       store re-stamps its id defensively anyway).
     */
    public ServerSidePlayerConfigStorage(Class<C> configClass, Function<UUID, C> defaultFactory) {
        this(configClass, defaultFactory, null);
    }

    /**
     * @param gson the {@link Gson} used for persistence; when {@code null}, the shared network Gson is resolved
     *             lazily at each (de)serialization so a store built before the loader installs its enriched Gson
     *             still works.
     */
    public ServerSidePlayerConfigStorage(Class<C> configClass, Function<UUID, C> defaultFactory, Gson gson) {
        super(1, configClass, key -> defaultFactory.apply(playerId(key)), gson);
    }

    /**
     * Registers a server-side receiver so that whenever a client sends {@code type}, the payload is stored under
     * the authenticated sender id (not any id the client serialized into the payload). This is the one-call wiring
     * a consumer mod needs: declare a packet, hand it to the store, done. Returns {@code this} for chaining.
     */
    public ServerSidePlayerConfigStorage<C> handleOn(PacketType<C> type) {
        CommunicationManager.onServerReceive(type, (payload, context) -> put(context.senderId(), payload));
        return this;
    }

    /** The stored config for {@code playerId}, if any. Never creates one. */
    public Optional<C> get(UUID playerId) {
        return get(key(playerId));
    }

    /** Whether a config is stored for {@code playerId}. */
    public boolean contains(UUID playerId) {
        return contains(key(playerId));
    }

    /**
     * The stored config for {@code playerId}, creating, storing and returning a default when absent. Use this on
     * the read-modify-write path where the caller intends to keep the result.
     */
    public C getOrCreate(UUID playerId) {
        return getOrCreate(key(playerId));
    }

    /**
     * The stored config for {@code playerId}, or a fresh (unstored) default when absent - a read-only fallback
     * for callers that just need a value to render/compare and must not mutate the store.
     */
    public C getOrDefault(UUID playerId) {
        return getOrDefault(key(playerId));
    }

    /**
     * Stores {@code config} for {@code playerId}, re-stamping the config's own player id to match the key and
     * applying any pending schema migration first. Returns the stored instance (which may differ from the argument
     * if a migration produced a new one).
     */
    public C put(UUID playerId, C config) {
        if (playerId == null || config == null) {
            throw new IllegalArgumentException("playerId and config must be non-null");
        }
        return put(key(playerId), config);
    }

    /** Removes and returns the config for {@code playerId}, if one was stored. */
    public Optional<C> remove(UUID playerId) {
        return remove(key(playerId));
    }

    /** An immutable snapshot of the whole store, keyed by player id. */
    public Map<UUID, C> byPlayer() {
        Map<UUID, C> out = new LinkedHashMap<>();
        snapshot().forEach((path, config) -> out.put(playerId(path), config));
        return out;
    }

    @Override
    protected C beforeStore(KeyPath key, C config) {
        C stored = config.ensureSchemaFrom(config);
        stored.setPlayerId(playerId(key));
        return stored;
    }

    private static KeyPath key(UUID playerId) {
        return KeyPath.of(Objects.requireNonNull(playerId, "playerId"));
    }

    private static UUID playerId(KeyPath key) {
        return UUID.fromString(key.segment(0));
    }
}
