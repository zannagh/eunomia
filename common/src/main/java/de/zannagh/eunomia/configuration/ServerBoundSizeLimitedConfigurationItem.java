package de.zannagh.eunomia.configuration;

import de.zannagh.eunomia.networking.payloads.CompressedJsonCodec; /**
 * A marker interface for configuration items that can exceed the default minecraft package sizes.
 * These are {@link CompressedJsonCodec#MAX_PAYLOAD_BYTES} in general and {@link CompressedJsonCodec#MAX_SERVERBOUND_PAYLOAD_BYTES} for server bound packages.<br/></br>
 * If a configuration item implements this interface, it can exceed the default minecraft package sizes for server bound packages and serialization will apply the serverbound payload byte ceiling.
 * @param <T>
 */
public interface ServerBoundSizeLimitedConfigurationItem<T extends ConfigurationItem<T>> extends ConfigurationItem<T> {

    default boolean canExceedPackageSizes() {
        return true;
    }
}
