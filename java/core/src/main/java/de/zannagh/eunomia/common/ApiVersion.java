package de.zannagh.eunomia.common;

import org.jspecify.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * Derives the upstream relay's API version segment from the mod's own version.
 *
 * <p>The relay versions its REST surface by URL segment ({@code /api/v0.3/...}) and mirrors the mod's release
 * semver with the patch truncated, so a mod built at {@code 0.3.7} talks to {@code /api/v0.3}. The version
 * strings that reach here are not clean - GitVersion emits {@code 0.3.1-alpha.2+7} on a prerelease and the
 * loader artifacts carry {@code 0.3.0+mc-1.21.9-10} - so parsing goes through {@link SemanticVersion#parse}
 * and the result is validated before it can ever reach a URI.
 */
public final class ApiVersion {

    /** The only shape a segment may have. A {@code +} or a prerelease tail here would not match any relay route. */
    private static final Pattern SEGMENT = Pattern.compile("^\\d+\\.\\d+$");

    /** The segment this build talks to, e.g. {@code "0.3"}. */
    public static final String CURRENT = of(BuildInfo.VERSION);

    private ApiVersion() {
    }

    /**
     * Truncates a mod version to its {@code major.minor} API segment.
     *
     * @param modVersion The mod version, in any of the forms described on this class.
     * @return The validated {@code major.minor} segment.
     * @throws IllegalArgumentException When the version does not yield a usable segment. Failing loudly is
     *     deliberate: a silently malformed segment would produce URLs the relay answers with 404, which reads
     *     as an unreachable relay rather than a build misconfiguration.
     */
    public static String of(@Nullable String modVersion) {
        SemanticVersion parsed = SemanticVersion.parse(modVersion);
        if (parsed == null) {
            throw new IllegalArgumentException(
                    "Cannot derive the relay API version from mod version '" + modVersion
                            + "'. Check the -PsemVer build property (or the localSemVer fallback in gradle.properties).");
        }
        String segment = parsed.major() + "." + parsed.minor();
        if (!SEGMENT.matcher(segment).matches()) {
            throw new IllegalArgumentException(
                    "Derived relay API version '" + segment + "' from mod version '" + modVersion
                            + "' is not a valid major.minor segment. Check the -PsemVer build property.");
        }
        return segment;
    }
}
