package de.zannagh.eunomia.configuration;

import java.util.Objects;
import java.util.UUID;

/**
 * Convenience base for a {@link PlayerLinkedConfigurationItem}. It owns the one piece every player-linked
 * config needs identically - the owning player's UUID - plus the transient "changed since deserialized" flag,
 * and derives identity ({@link #equals}/{@link #hashCode}) from the player id so instances key cleanly in the
 * {@link ServerSidePlayerConfigStorage} map.
 * <p>
 * Everything that is genuinely per-config is left abstract: the value/default accessors, the schema version and
 * migration, and (on {@code >= 1.20.5}) the {@code CustomPacketPayload} type and codec. A consumer subclass
 * declares its fields, wires those, and gets the player-linking plumbing for free:
 * <pre>{@code
 * public final class MyConfig extends PlayerLinkedConfigurationItemBase<MyConfig> {
 *     public MyConfig() { }
 *     public MyConfig(UUID playerId) { setPlayerId(playerId); }
 *     // ... fields + the remaining ConfigurationItem methods ...
 * }
 * }</pre>
 *
 * @param <T> the self type of the configuration (CRTP)
 * @since 0.1.0
 */
public abstract class PlayerLinkedConfigurationItemBase<T extends ConfigurationItem<T>>
        implements PlayerLinkedConfigurationItem<T> {

    private UUID playerId;

    private transient boolean hasChangedFromSerializedContent;

    protected PlayerLinkedConfigurationItemBase() {
    }

    protected PlayerLinkedConfigurationItemBase(UUID playerId) {
        this.playerId = playerId;
    }

    @Override
    public UUID getPlayerId() {
        return playerId;
    }

    @Override
    public void setPlayerId(UUID playerId) {
        this.playerId = playerId;
    }

    @Override
    public boolean hasChangedFromSerializedContent() {
        return hasChangedFromSerializedContent;
    }

    @Override
    public void setHasChangedFromSerializedContent() {
        this.hasChangedFromSerializedContent = true;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PlayerLinkedConfigurationItemBase<?> that)) {
            return false;
        }
        return Objects.equals(playerId, that.playerId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(playerId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{playerId=" + playerId + "}";
    }
}
