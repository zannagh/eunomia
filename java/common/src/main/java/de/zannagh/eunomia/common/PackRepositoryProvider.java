package de.zannagh.eunomia.common;

import org.jspecify.annotations.NonNull;

public interface PackRepositoryProvider {
    /**
     * The mod identifier declared by the mod's metadata.
     * @return A string representing the mod identifier.
     */
    @NonNull String getModId();

    /**
     * The asset directory name declared by the mod's metadata, usually "assets".
     * @return A string representing the asset directory name.
     */
    @NonNull String getAssetDirectoryName();

    /**
     * The mod name declared by the mod metadata to register pack resources.
     * @return
     */
    @NonNull String getModName();

    /**
     * The mod resource name declared by the mod metadata to register pack resources, usually "{Name} Resources".
     * @return
     */
    @NonNull String getModResourceName();
}
