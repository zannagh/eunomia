package de.zannagh.eunomia.configuration;

import de.zannagh.eunomia.networking.serialization.NetworkHealable;

/**
 * A marker interface for configuration items that can exist in a deprecated form on servers or other clients and potentially need to be 'healed' to a newer form.
 * @param <T>
 */
public interface DeprecationMarkedConfigurationItem<T extends ConfigurationItem<T>> extends ConfigurationItem<T>, NetworkHealable {
    void heal(Object received);

    /**
     * Bridges the core's post-decode {@link NetworkHealable} hook to this type's healing: when such an
     * item arrives over the network the codec calls {@link #heal()}, which repairs it in place.
     */
    @Override
    default void heal() {
        heal(this);
    }
}
