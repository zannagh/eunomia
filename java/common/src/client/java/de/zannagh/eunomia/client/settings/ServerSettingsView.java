package de.zannagh.eunomia.client.settings;

import de.zannagh.eunomia.networking.admin.ServerSettingsPayload;
import de.zannagh.eunomia.networking.admin.ServerSettingsStatus;

/**
 * An immutable client-side snapshot of the joined server's Cloud Sync policy, as the server most recently
 * described it.
 *
 * <p>A record rather than the wire payload itself so a caller cannot mutate a shared object and cannot
 * mistake the mutable {@code editable} field for something it may set. {@link #editable()} is the server's
 * verdict on whether this player may write, and is a <em>rendering</em> input only: flipping it here would
 * produce an editable-looking screen whose writes the server still refuses.</p>
 *
 * @param enableExternalFallback  the server's current external-fallback switch.
 * @param externalServerAddress   the server's current relay address, normalised; blank means "no relay".
 * @param preferExternalTransport whether the server wants clients on the relay regardless.
 * @param editable                whether the server said this player may change these values.
 * @param status                  how the exchange that produced this view concluded.
 * @param detail                  a human-readable refusal reason, or {@code null}.
 * @since 0.3.0
 */
public record ServerSettingsView(
        boolean enableExternalFallback,
        String externalServerAddress,
        boolean preferExternalTransport,
        boolean editable,
        ServerSettingsStatus status,
        String detail) {

    /** Adapts a payload off the wire. */
    static ServerSettingsView of(ServerSettingsPayload payload) {
        return new ServerSettingsView(
                payload.enableExternalFallback,
                payload.externalServerAddress == null ? "" : payload.externalServerAddress,
                payload.preferExternalTransport,
                payload.editable,
                payload.statusOrOk(),
                payload.detail);
    }

    /**
     * The view handed to a callback whose exchange never got an answer - a server without eunomia, or one
     * whose reply was lost. Values are the neutral defaults and {@link #editable()} is {@code false}, so a
     * screen rendering this shows something inert rather than inviting a write that cannot land.
     */
    static ServerSettingsView unavailable() {
        return new ServerSettingsView(false, "", false, false, ServerSettingsStatus.UNAVAILABLE,
                "The server did not answer - it may not be running eunomia.");
    }

    /** Whether a submitted write was accepted and persisted by the server. */
    public boolean applied() {
        return status == ServerSettingsStatus.APPLIED;
    }

    /** Whether the exchange was refused, for any reason (permissions, a bad address, or no answer). */
    public boolean refused() {
        return status == ServerSettingsStatus.DENIED
                || status == ServerSettingsStatus.INVALID_ADDRESS
                || status == ServerSettingsStatus.UNAVAILABLE;
    }
}
