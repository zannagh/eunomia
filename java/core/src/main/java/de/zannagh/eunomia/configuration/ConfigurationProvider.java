package de.zannagh.eunomia.configuration;

/**
 * A provider for a {@link ConfigurationItem} configuration item.
 * @param <T> The type of the {@link ConfigurationItem} configuration item.
 *
 * @since 0.1.0
 */
public interface ConfigurationProvider<T extends ConfigurationItem<T>> {

    /**
     * Loads the configuration, e.g. from a file, database or web API.
     * @return The loaded configuration.
     */
    T load();

    /**
     * Updates the current configuration without saving it. Use {@link #saveCurrent()} to save the configuration after calling update.
     * @param newValue The current configuration.
     */
    void update(T newValue);

    /**
     * Saves the current configuration, e.g. to a file, database or web API.
     */
    void saveCurrent();

    /**
     * Updates the current configuration and saves it.
     * @param newValue The current configuration.
     */
    default void updateAndSave(T newValue){
        update(newValue);
        saveCurrent();
    }

    /**
     * Returns the current configuration.
     * @return The current configuration.
     */
    T getValue();

    /**
     * Returns the default configuration.
     * @return The default configuration.
     */
    T getDefault();
}
