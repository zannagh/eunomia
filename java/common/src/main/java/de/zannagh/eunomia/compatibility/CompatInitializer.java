package de.zannagh.eunomia.compatibility;

/**
 * A compat initializer with a target flag and an initialization method.
 */
public interface CompatInitializer {

    /**
     * The target compat flag of the initializer.
     * @return The {@link CompatFlag} target of the initializer.
     */
    CompatFlag targetFlag();

    /**
     * Initializes the compat.
     *
     * @return The {@link CompatInitializationResult} of the initialization.
     * @implSpec Implementations should not throw; return a failure result instead.
     */
    CompatInitializationResult init();
}
