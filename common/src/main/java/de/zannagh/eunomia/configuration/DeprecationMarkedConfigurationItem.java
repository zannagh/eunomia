package de.zannagh.eunomia.configuration;

/**
 * A marker interface for configuration items that can exist in a deprecated form on servers or other clients and potentially need to be 'healed' to a newer form.
 * @param <T>
 */
public interface DeprecationMarkedConfigurationItem<T extends ConfigurationItem<T>> extends ConfigurationItem<T>{
    void heal(Object received);
}
