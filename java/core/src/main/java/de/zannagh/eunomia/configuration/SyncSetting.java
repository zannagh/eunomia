package de.zannagh.eunomia.configuration;

import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * The individual external-transport ("Cloud Sync") settings a server can state an opinion about, and - since
 * enforcement exists - can also <em>force</em> on its clients.
 * <p>
 * It is an enum rather than a bare string because the enforcement set travels the handshake and lands in a
 * client-side UI: an identifier that only ever exists as a literal is one typo away from an enforcement flag
 * that silently matches nothing. Enforcement failing open is the worst possible failure mode here, so the set of
 * things that can be enforced is closed and checked by the compiler.
 * <p>
 * It lives in {@code :core} beside {@link EunomiaDefaults} for the same reason that class does: the Bukkit-family
 * plugin ({@code :paper}) has to express the very same policy and does not depend on {@code :common}.
 *
 * @since 0.3.1
 */
public enum SyncSetting {

    /** Whether the external relay may be used at all - {@code EunomiaSyncSettings.externalFallbackEnabled()}. */
    EXTERNAL_FALLBACK,

    /** Which relay is dialled - {@code EunomiaSyncSettings.externalServerAddress()}. */
    EXTERNAL_SERVER_ADDRESS,

    /** Whether the relay is preferred over the in-game transport - {@code preferExternalTransport()}. */
    PREFER_EXTERNAL_TRANSPORT;

    /**
     * Parses a wire name back into a setting, or {@code null} when nothing matches.
     * <p>
     * Unknown names are dropped rather than thrown on, because the wire carries these as strings and a
     * <em>newer</em> server may well enforce a setting this client has never heard of. Ignoring it means the
     * client resolves that unknown setting the way it always did; throwing would take the whole handshake down
     * over a setting the client does not even have.
     *
     * @param name the wire name, may be {@code null}.
     * @return the matching setting, or {@code null} if this build does not know it.
     */
    public static @Nullable SyncSetting parse(@Nullable String name) {
        if (name == null) {
            return null;
        }
        for (SyncSetting setting : values()) {
            if (setting.name().equals(name)) {
                return setting;
            }
        }
        return null;
    }

    /**
     * Parses a collection of wire names, skipping any this build does not know (see {@link #parse(String)}).
     *
     * @param names the wire names, may be {@code null}.
     * @return the recognised settings, never {@code null}.
     */
    public static Set<SyncSetting> parseAll(@Nullable Collection<String> names) {
        if (names == null || names.isEmpty()) {
            return Set.of();
        }
        EnumSet<SyncSetting> parsed = EnumSet.noneOf(SyncSetting.class);
        for (String name : names) {
            SyncSetting setting = parse(name);
            if (setting != null) {
                parsed.add(setting);
            }
        }
        return Set.copyOf(parsed);
    }
}
