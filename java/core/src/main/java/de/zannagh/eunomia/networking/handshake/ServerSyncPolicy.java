package de.zannagh.eunomia.networking.handshake;

import de.zannagh.eunomia.configuration.SyncSetting;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * The external-transport policy a server advertises to its clients in the capability handshake - rung two of
 * eunomia's effective-settings precedence chain, below the player's own override and above the consuming mod's
 * defaults, <em>unless</em> the server enforces the setting, in which case it is rung zero.
 * <p>
 * Every value component is nullable and {@code null} strictly means "the server said nothing about this". That is
 * the shape that makes a protocol-v1 server (which had no policy fields at all) decode into {@link #UNKNOWN}
 * rather than into a set of invented values: an absent opinion falls through the chain instead of overriding the
 * rungs below it. Never substitute framework defaults while decoding - that would make an old server look like it
 * had actively advertised the defaults.
 * <p>
 * <strong>Enforcement is a modifier on an opinion, never an opinion of its own.</strong> {@code enforced} only
 * says which settings the server insists on; what it insists on still comes from the value components. A setting
 * that is enforced but carries no value enforces nothing - see {@link #isEmpty()}, which deliberately ignores
 * {@code enforced} entirely.
 *
 * @param enableExternalFallback whether the server permits/expects the external relay, or {@code null}
 * @param externalServerAddress the relay address the server points at, or {@code null}
 * @param preferExternalTransport whether the server wants clients on the relay even though it speaks eunomia
 * @param enforced the settings this server forces rather than merely suggests; never {@code null} after
 *                 construction, but may be empty
 * @since 0.3.0
 */
public record ServerSyncPolicy(
        @Nullable Boolean enableExternalFallback,
        @Nullable String externalServerAddress,
        @Nullable Boolean preferExternalTransport,
        Set<SyncSetting> enforced) {

    /** The "no opinion at all" policy: what an unresolved probe, an absent server or a v1 server yields. */
    public static final ServerSyncPolicy UNKNOWN = new ServerSyncPolicy(null, null, null, null);

    /**
     * Normalises a blank advertised address to {@code null}, so "" never masquerades as an opinion, and an
     * absent enforcement set to the empty set, so no caller has to null-check what is conceptually "nothing is
     * enforced". The copy also makes the set immutable: a policy is shared across every consumer that reads the
     * capability view, and one of them mutating it would change what every other one enforces.
     */
    public ServerSyncPolicy {
        if (externalServerAddress != null && externalServerAddress.isBlank()) {
            externalServerAddress = null;
        }
        enforced = enforced == null ? Set.of() : Set.copyOf(enforced);
    }

    /** An advisory policy: the values are suggestions the rungs above may override. */
    public ServerSyncPolicy(
            @Nullable Boolean enableExternalFallback,
            @Nullable String externalServerAddress,
            @Nullable Boolean preferExternalTransport) {
        this(enableExternalFallback, externalServerAddress, preferExternalTransport, null);
    }

    /**
     * Whether this server forces {@code setting} rather than merely suggesting it.
     * <p>
     * A {@code true} here is <em>not</em> on its own enough to lock anything: the caller must still find a value
     * to lock to. Enforcement over a non-opinion is meaningless and must fall through.
     *
     * @param setting the setting to test, may be {@code null}.
     * @return whether the server insists on its own value for that setting.
     */
    public boolean enforces(@Nullable SyncSetting setting) {
        return setting != null && enforced.contains(setting);
    }

    /**
     * Whether this policy carries any opinion at all.
     * <p>
     * Value-only, on purpose: an enforcement set over no values is not an opinion, and counting it as one would
     * make a server that enforces nothing in particular look - to the handshake, the capability view and the
     * tests that pin v1 compatibility - like a server that had actively advertised a policy.
     */
    public boolean isEmpty() {
        return enableExternalFallback == null && externalServerAddress == null && preferExternalTransport == null;
    }
}
