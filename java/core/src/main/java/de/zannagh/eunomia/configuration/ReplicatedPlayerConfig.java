package de.zannagh.eunomia.configuration;

import de.zannagh.eunomia.keyed.KeyPath;
import de.zannagh.eunomia.keyed.Replicated;

/**
 * A per-player configuration that is also {@link Replicated}: stored server-side keyed by the owning player's
 * UUID, relayed to every other client on change, and dumped to newcomers on join. This is the exact contract an
 * Armor Hider-style mod implements - each player's settings are shared state everyone needs to render.
 * <p>
 * The {@link Replicated#keyPath() primary key} is derived from the player id automatically, so an implementor only
 * declares its fields and the usual {@link PlayerLinkedConfigurationItem} plumbing; the store keys, heals and
 * replicates it. Pair it with a {@link ReplicatedPlayerConfigStore} server-side and a
 * {@code ReplicatedClientStore} client-side.
 *
 * @param <T> the self type (CRTP)
 * @since 0.1.0
 */
public interface ReplicatedPlayerConfig<T extends ReplicatedPlayerConfig<T>>
        extends PlayerLinkedConfigurationItem<T>, Replicated {

    @Override
    default KeyPath keyPath() {
        return KeyPath.of(getPlayerId());
    }
}
