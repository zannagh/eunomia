package de.zannagh.eunomia.configuration;

import de.zannagh.eunomia.common.SemanticVersion;

/**
 * Marker interface for configuration classes that should have their
 * ConfigurationItemBase fields automatically initialized when missing from JSON.
 *
 * <p>This type is deliberately Minecraft-free: config items travel the wire as plain POJOs wrapped in
 * the loader's single {@code CustomPacketPayload} (serialized as {@code gzip(json)} via the core
 * {@code PayloadCodec}), so a config never has to be a payload itself.</p>
 *
 * Any inheritor of this interface should be registered with {@link ConfigurationItemFactoryRegistry} for
 * serialization.
 *
 * @since 0.1.0
 */
public interface ConfigurationItem<T extends ConfigurationItem<T>> {

    /**
     * Retrieves the current value of the configuration item.
     * @return The current value of the configuration item.
     */
    T getValue();

    /**
     * Sets the new value of the configuration item.
     * @param newValue The new value of the configuration item.
     */
    void setValue(T newValue);

    /**
     * Retrieves the default value of the configuration item.
     * @return The default value of the configuration item.
     */
    T getDefaultValue();

    /**
     * Checks whether the configuration source has been modified from its
     * serialized state.
     *
     * @return true if the configuration has been changed since it was last
     *         serialized; false otherwise.
     */
    boolean hasChangedFromSerializedContent();


    /**
     * Marks the configuration source as having been modified from its serialized state.
     */
    void setHasChangedFromSerializedContent();

    /**
     * Config schema version. Absent (0) in configs from before versioning was introduced.
     * Incremented when the structure changes in a way that requires migration.
     */
    SemanticVersion getSchemaVersion();

    /**
     * Retrieves the current schema version used by the configuration system.
     * This version reflects the latest version of the configuration structure,
     * allowing compatibility and migration strategies when updates are introduced.
     *
     * @return the integer value representing the current schema version.
     */
    SemanticVersion getCurrentSchemaVersion();

    /**
     * Determines if a migration is necessary based on the schema version of the configuration.
     * Migration is required if the schema version associated with the configuration is
     * older than the current schema version defined in the system.
     *
     * @return true if the schema version of the configuration is outdated and needs migration;
     *         false otherwise.
     */
    default boolean shouldMigrate() {
        return getSchemaVersion().isSmallerThan(getCurrentSchemaVersion());
    }

    /**
     * Migrates the configuration item from an older schema version to the current schema version.
     * @param old The configuration item with the older schema version.
     * @return The migrated configuration item with the current schema version.
     */
    T migrateFrom(T old);

    /**
     * Returns {@code old} migrated to the current schema, or {@code old} unchanged when it is already current.
     * The decision and the changed-flag are driven by {@code old} itself (not the receiver), so the result is
     * independent of which instance this default method is dispatched on - the intended call is
     * {@code x.ensureSchemaFrom(x)}. When a migration produces a new instance, the changed-flag is set on that
     * returned instance rather than on {@code old}.
     */
    default T ensureSchemaFrom(T old) {
        if (!old.shouldMigrate()) {
            return old;
        }
        T migrated = old.migrateFrom(old);
        migrated.setHasChangedFromSerializedContent();
        return migrated;
    }
}
