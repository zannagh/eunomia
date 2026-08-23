package de.zannagh.eunomia.configuration;

import org.jspecify.annotations.Nullable;

/**
 * The consuming mod's client-side defaults for the external transport settings - rung three of the
 * effective-settings precedence chain, below the player's own override and the joined server's advertised
 * policy, but above {@link EunomiaDefaults}.
 * <p>
 * This is a deliberately bare programmatic surface: a mod that embeds eunomia calls the setters once during
 * its client init to say "unless the player or the server says otherwise, my users get the relay". The fluent
 * {@code Eunomia} builder API is expected to delegate here rather than to hold a second copy of this state.
 * <p>
 * Every value is nullable, and {@code null} means "no opinion" - the resolver then falls through to
 * {@link EunomiaDefaults}. Setting a value back to {@code null} clears the opinion again.
 *
 * @since 0.3.0
 */
public final class EunomiaSyncDefaults {

    private static volatile @Nullable Boolean enableExternalFallback;

    private static volatile @Nullable String externalServerAddress;

    private static volatile @Nullable Boolean preferExternalTransport;

    private EunomiaSyncDefaults() {
    }

    /** The mod's default for the external fallback opt-in, or {@code null} when it has no opinion. */
    public static @Nullable Boolean enableExternalFallback() {
        return enableExternalFallback;
    }

    /** Sets (or clears, with {@code null}) the mod's default for the external fallback opt-in. */
    public static void setEnableExternalFallback(@Nullable Boolean value) {
        enableExternalFallback = value;
    }

    /** The mod's default relay address, or {@code null} when it has no opinion. Blank counts as no opinion. */
    public static @Nullable String externalServerAddress() {
        String value = externalServerAddress;
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    /** Sets (or clears, with {@code null} or blank) the mod's default relay address. */
    public static void setExternalServerAddress(@Nullable String value) {
        externalServerAddress = value;
    }

    /** The mod's default for preferring the relay over the in-game transport, or {@code null} for no opinion. */
    public static @Nullable Boolean preferExternalTransport() {
        return preferExternalTransport;
    }

    /** Sets (or clears, with {@code null}) the mod's default for preferring the relay. */
    public static void setPreferExternalTransport(@Nullable Boolean value) {
        preferExternalTransport = value;
    }

    /** Drops every mod-level opinion. Intended for tests and for a consumer that wants a clean slate. */
    public static void reset() {
        enableExternalFallback = null;
        externalServerAddress = null;
        preferExternalTransport = null;
    }
}
