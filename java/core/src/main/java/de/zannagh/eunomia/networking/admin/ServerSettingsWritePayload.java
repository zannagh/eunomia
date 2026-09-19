package de.zannagh.eunomia.networking.admin;

import org.jspecify.annotations.Nullable;

/**
 * "Store this Cloud Sync policy." Carries the operator-facing values of {@code EunomiaServerConfig} and
 * nothing else - in particular no permission claim, because the server decides that for itself on arrival
 * and would ignore one anyway.
 *
 * <p>The address is taken as typed and validated server-side before it is stored; see
 * {@link ServerSettingsExchange}. A blank or absent address is a legitimate value meaning "this server
 * points at no relay", not a malformed one.</p>
 *
 * @since 0.3.0
 */
public class ServerSettingsWritePayload {

    /** Echoed in the answer so the submitting client can match it to its success/failure callback. */
    public long correlationId;

    /** Requested value for {@code EunomiaServerConfig.enableExternalFallback}. */
    public boolean enableExternalFallback;

    /** Requested relay address, as typed. Validated and normalised server-side; blank means "clear it". */
    public String externalServerAddress;

    /** Requested value for {@code EunomiaServerConfig.preferExternalTransport}. */
    public boolean preferExternalTransport;

    /**
     * Requested value for {@code EunomiaServerConfig.enforceSettings} - whether the three values above are
     * forced on every client rather than merely advertised - or {@code null} for "I am not saying".
     * <p>
     * Boxed, and the only field here that is, because {@link ServerSettingsExchange} rebuilds the whole
     * configuration from this payload: a field the payload does not carry is a field the next save erases.
     * A primitive would therefore make every write from a client built before enforcement existed - and
     * every hand-rolled one - silently unlock a server that had locked its settings. {@code null} instead
     * means "leave enforcement as it stands", so only a client that actually has the control can change it.
     */
    public @Nullable Boolean enforce;

    public ServerSettingsWritePayload() {
    }

    /** A write that says nothing about enforcement, leaving whatever the server already had. */
    public ServerSettingsWritePayload(
            long correlationId,
            boolean enableExternalFallback,
            String externalServerAddress,
            boolean preferExternalTransport) {
        this(correlationId, enableExternalFallback, externalServerAddress, preferExternalTransport, null);
    }

    public ServerSettingsWritePayload(
            long correlationId,
            boolean enableExternalFallback,
            String externalServerAddress,
            boolean preferExternalTransport,
            @Nullable Boolean enforce) {
        this.correlationId = correlationId;
        this.enableExternalFallback = enableExternalFallback;
        this.externalServerAddress = externalServerAddress;
        this.preferExternalTransport = preferExternalTransport;
        this.enforce = enforce;
    }
}
