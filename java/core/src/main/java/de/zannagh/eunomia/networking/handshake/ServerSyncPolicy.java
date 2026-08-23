package de.zannagh.eunomia.networking.handshake;

import org.jspecify.annotations.Nullable;

/**
 * The external-transport policy a server advertises to its clients in the capability handshake - rung two of
 * eunomia's effective-settings precedence chain, below the player's own override and above the consuming mod's
 * defaults.
 * <p>
 * Every component is nullable and {@code null} strictly means "the server said nothing about this". That is the
 * shape that makes a protocol-v1 server (which had no policy fields at all) decode into {@link #UNKNOWN} rather
 * than into a set of invented values: an absent opinion falls through the chain instead of overriding the rungs
 * below it. Never substitute framework defaults while decoding - that would make an old server look like it had
 * actively advertised the defaults.
 *
 * @param enableExternalFallback whether the server permits/expects the external relay, or {@code null}
 * @param externalServerAddress the relay address the server points at, or {@code null}
 * @param preferExternalTransport whether the server wants clients on the relay even though it speaks eunomia
 * @since 0.3.0
 */
public record ServerSyncPolicy(
        @Nullable Boolean enableExternalFallback,
        @Nullable String externalServerAddress,
        @Nullable Boolean preferExternalTransport) {

    /** The "no opinion at all" policy: what an unresolved probe, an absent server or a v1 server yields. */
    public static final ServerSyncPolicy UNKNOWN = new ServerSyncPolicy(null, null, null);

    /** Normalises a blank advertised address to {@code null}, so "" never masquerades as an opinion. */
    public ServerSyncPolicy {
        if (externalServerAddress != null && externalServerAddress.isBlank()) {
            externalServerAddress = null;
        }
    }

    /** Whether this policy carries any opinion at all. */
    public boolean isEmpty() {
        return enableExternalFallback == null && externalServerAddress == null && preferExternalTransport == null;
    }
}
