package de.zannagh.eunomia.networking.serialization;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;

/**
 * Skips every field annotated {@link LocalOnly} - the Gson-level enforcement behind that annotation.
 * Add to a {@code GsonBuilder} that resolves payloads for the wire; the local/persistence Gson keeps no
 * such strategy, so a saved config file still carries the field.
 *
 * @since 0.1.0
 */
public final class LocalOnlyExclusionStrategy implements ExclusionStrategy {

    @Override
    public boolean shouldSkipField(FieldAttributes f) {
        return f.getAnnotation(LocalOnly.class) != null;
    }

    @Override
    public boolean shouldSkipClass(Class<?> clazz) {
        return false;
    }
}
