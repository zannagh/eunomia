package de.zannagh.eunomia.configuration;

import com.google.gson.Gson;
import de.zannagh.eunomia.keyed.KeyPath;
import de.zannagh.eunomia.keyed.ReplicatedKeyedStore;
import de.zannagh.eunomia.networking.packets.KeyedPacket;
import de.zannagh.eunomia.networking.packets.ServerContext;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * The replicated counterpart of {@link ServerSidePlayerConfigStorage}: a per-player config store keyed by UUID
 * that additionally persists, relays each update to the other clients, and pushes its whole contents to every
 * newcomer on join. This is the ready-made server side of the Armor Hider use case - declare a
 * {@link ReplicatedPlayerConfig} DTO and a bidirectional {@link KeyedPacket}, hand them here, call
 * {@link #enableServer()}.
 * <p>
 * Like the non-replicated store it is authoritative about identity: an inbound config is keyed and stamped by the
 * <em>authenticated sender</em> ({@link ServerContext#senderId()}), never by an id the client serialized, and any
 * pending schema migration is applied on the way in.
 *
 * @param <C> the replicated player-config type
 * @since 0.1.0
 */
public final class ReplicatedPlayerConfigStore<C extends ReplicatedPlayerConfig<C>> extends ReplicatedKeyedStore<C> {

    public ReplicatedPlayerConfigStore(Class<C> configClass, Function<UUID, C> defaultFactory, KeyedPacket<C> channel) {
        this(configClass, defaultFactory, channel, null, null);
    }

    /**
     * @param persistenceFile where to persist on every change (or {@code null} for in-memory only).
     * @param gson            the persistence {@link Gson}, or {@code null} to resolve the shared network Gson lazily.
     */
    public ReplicatedPlayerConfigStore(Class<C> configClass, Function<UUID, C> defaultFactory, KeyedPacket<C> channel,
                                       Path persistenceFile, Gson gson) {
        super(1, configClass, channel, persistenceFile, gson, key -> defaultFactory.apply(playerId(key)));
    }

    @Override
    public ReplicatedPlayerConfigStore<C> enableServer() {
        super.enableServer();
        return this;
    }

    @Override
    protected KeyPath keyFor(C payload, ServerContext context) {
        // The authenticated sender owns the entry; a client cannot write another player's config.
        return KeyPath.of(context.senderId());
    }

    @Override
    protected C beforeStore(KeyPath key, C config) {
        C stored = config.ensureSchemaFrom(config);
        stored.setPlayerId(playerId(key));
        return stored;
    }

    /** The stored config for {@code playerId}, if any. */
    public Optional<C> get(UUID playerId) {
        return get(key(playerId));
    }

    /** Whether a config is stored for {@code playerId}. */
    public boolean contains(UUID playerId) {
        return contains(key(playerId));
    }

    /** The config for {@code playerId}, creating and storing a default when absent. */
    public C getOrCreate(UUID playerId) {
        return getOrCreate(key(playerId));
    }

    /** The config for {@code playerId}, or a fresh unstored default when absent. */
    public C getOrDefault(UUID playerId) {
        return getOrDefault(key(playerId));
    }

    /** Stores {@code config} under {@code playerId}, re-stamping its id and applying migrations. */
    public C put(UUID playerId, C config) {
        if (playerId == null || config == null) {
            throw new IllegalArgumentException("playerId and config must be non-null");
        }
        return put(key(playerId), config);
    }

    /** Removes and returns the config for {@code playerId}, if stored. */
    public Optional<C> remove(UUID playerId) {
        return remove(key(playerId));
    }

    /** An immutable snapshot of the store keyed by player id. */
    public Map<UUID, C> byPlayer() {
        Map<UUID, C> out = new LinkedHashMap<>();
        snapshot().forEach((path, config) -> out.put(playerId(path), config));
        return out;
    }

    private static KeyPath key(UUID playerId) {
        return KeyPath.of(Objects.requireNonNull(playerId, "playerId"));
    }

    private static UUID playerId(KeyPath key) {
        return UUID.fromString(key.segment(0));
    }
}
