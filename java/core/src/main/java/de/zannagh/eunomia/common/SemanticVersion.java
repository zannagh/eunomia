package de.zannagh.eunomia.common;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /**
     * Matches the leading {@code major[.minor[.patch]]} of a version string. Everything after it (a
     * {@code -prerelease} tail, a {@code +build} tail, or a loader's {@code +mc-1.21.9-10} suffix) is captured
     * separately and never string-sliced by hand.
     * <p>
     * The tail is optional, but a {@code -} or {@code +} separator commits to one: it must be non-empty and
     * built only from the characters semver allows in prerelease/build identifiers (alphanumerics, {@code .},
     * {@code -} and {@code +}). A dangling {@code 0.3.0-} or a tail carrying whitespace is a malformed version
     * string, not a version with an odd suffix, and must not parse into a seemingly valid API segment.
     */
    private static final Pattern VERSION =
            Pattern.compile("^(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:[-+]([0-9A-Za-z.+-]+))?$");

    /**
     * Parses a version string into a {@link SemanticVersion}, tolerating the forms actually seen in this project:
     * plain {@code 0.3.0}, a GitVersion prerelease {@code 0.3.1-alpha.2+7}, and a loader artifact version
     * {@code 0.3.0+mc-1.21.9-10}. Missing minor/patch components default to {@code 0}.
     *
     * @param version The raw version string, may be {@code null}.
     * @return The parsed version, or {@code null} when {@code version} is null, blank, not version-shaped, or
     *     carries an empty/invalid prerelease-or-build tail such as {@code 0.3.0-} or {@code 0.3.0+}.
     */
    public static @Nullable SemanticVersion parse(@Nullable String version) {
        if (version == null) {
            return null;
        }
        Matcher matcher = VERSION.matcher(version.trim());
        if (!matcher.matches()) {
            return null;
        }
        try {
            int major = Integer.parseInt(matcher.group(1));
            int minor = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
            int patch = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
            return new SemanticVersion(major, minor, patch, matcher.group(4));
        } catch (NumberFormatException e) {
            // A component that out-ranges an int is not a version we can act on.
            return null;
        }
    }

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
