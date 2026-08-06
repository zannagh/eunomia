package de.zannagh.eunomia.configuration;

import java.util.UUID;

/**
 * A {@link ConfigurationItem} that belongs to a single player, identified by their UUID.
 * <p>
 * This is the eunomia equivalent of a mod's per-player settings object (Armor Hider's {@code PlayerConfig}):
 * the whole aggregate of a player's mod configuration, carried on the wire as a {@link ConfigurationItem} and
 * looked up server-side by UUID in a {@link ServerSidePlayerConfigStorage}. Lookups are UUID-only by design -
 * player names are ambiguous, mutable and reusable, so they are never a key here.
 *
 * @param <T> the self type of the configuration (CRTP), e.g. {@code MyConfig implements PlayerLinkedConfigurationItem<MyConfig>}
 * @since 0.1.0
 */
public interface PlayerLinkedConfigurationItem<T extends ConfigurationItem<T>> extends ConfigurationItem<T> {

    /** The UUID of the player this configuration belongs to. May be {@code null} before it has been assigned. */
    UUID getPlayerId();

    /**
     * Assigns the owning player's UUID. The {@link ServerSidePlayerConfigStorage} calls this to keep a config's
     * own id in lockstep with the map key it is stored under - the server trusts the authenticated sender id
     * over whatever id a client may have serialized into the payload.
     */
    void setPlayerId(UUID playerId);
}
