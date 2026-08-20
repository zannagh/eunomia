package de.zannagh.eunomia.networking.serialization;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as purely local: kept when a config is saved to disk, but stripped whenever it is
 * serialized for the wire. Pair with the network Gson built by {@code SerializationManager} (or add
 * {@link LocalOnlyExclusionStrategy} to a consumer's own network {@code GsonBuilder} via
 * {@link NetworkSerializer#localOnlyExclusion()}) - no {@code forNetwork()} copy method required.
 *
 * @since 0.1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface LocalOnly {
}
