package de.zannagh.eunomia.common;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.Comparator;

/**
 * Represents a semantic version, following the semantic versioning specification.
 *
 * @since 0.1.0
 */
public record SemanticVersion(
        int major,
        int minor,
        int patch,
      @Nullable String build) implements Comparable<SemanticVersion>, Comparator<SemanticVersion> {

    @Override
    public @NonNull String toString() {
        var returnValue = major + "." + minor + "." + patch;
        if (build != null && !build.isEmpty()) {
            returnValue += "-" + build;
        }
        return returnValue;
    }

    /**
     * Whether this semantic version is smaller than the other semantic version.
     * @param other The semantic version to compare to.
     * @return True if this semantic version is smaller than the other semantic version.
     */
    public boolean isSmallerThan(SemanticVersion other) {
        return compareTo(other) < 0;
    }

    @Override
    public int compareTo(SemanticVersion otherVersion) {
        if (major > otherVersion.major) {
            return 1;
        }
        if (major < otherVersion.major) {
            return -1;
        }
        if (minor > otherVersion.minor) {
            return 1;
        }
        if (minor < otherVersion.minor) {
            return -1;
        }
        var patchResult = Integer.compare(patch, otherVersion.patch);
        if (patchResult == 0) {
            return compareBuild(otherVersion);
        }
        return patchResult;
    }

    private int compareBuild(SemanticVersion other) {
        return 0; // TODO, compare SemVer builds which can contain string values.
    }

    @Override
    public int compare(SemanticVersion firstVersion, SemanticVersion secondVersion) {
        return firstVersion.compareTo(secondVersion);
    }
}
