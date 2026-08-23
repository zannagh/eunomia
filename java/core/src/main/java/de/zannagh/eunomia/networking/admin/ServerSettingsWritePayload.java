package de.zannagh.eunomia.networking.admin;

/**
 * "Store this Cloud Sync policy." Carries the three operator-facing values of
 * {@code EunomiaServerConfig} and nothing else - in particular no permission claim, because the server
 * decides that for itself on arrival and would ignore one anyway.
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

    public ServerSettingsWritePayload() {
    }

    public ServerSettingsWritePayload(
            long correlationId,
            boolean enableExternalFallback,
            String externalServerAddress,
            boolean preferExternalTransport) {
        this.correlationId = correlationId;
        this.enableExternalFallback = enableExternalFallback;
        this.externalServerAddress = externalServerAddress;
        this.preferExternalTransport = preferExternalTransport;
    }
}
