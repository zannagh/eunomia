package de.zannagh.eunomia.networking.admin;

import de.zannagh.eunomia.configuration.EunomiaServerConfig;

/**
 * The server's answer to a settings request or a settings write: its current Cloud Sync policy, whether
 * the receiving player may change it, and how the exchange concluded.
 *
 * <p>{@link #editable} is a <em>rendering hint and nothing more</em>. It travels server to client only,
 * and the server never reads it back off an incoming packet - a client that flips it to {@code true} gets
 * an editable-looking screen whose every write is still refused by the permission check on the server. Its
 * only job is to spare the UI from guessing, so a non-administrator sees greyed-out controls rather than
 * discovering the refusal after typing.</p>
 *
 * <p>The values themselves are not privileged: a server already advertises exactly these three to every
 * joining client as its {@code ServerSyncPolicy} in the capability handshake. Withholding them from a
 * non-administrator here would be theatre, so a non-administrator receives a genuine, read-only snapshot
 * ({@code editable = false}) rather than a refusal.</p>
 *
 * @since 0.3.0
 */
public class ServerSettingsPayload {

    /** The correlation id of the request or write this answers, echoed verbatim. */
    public long correlationId;

    /** The server's current {@code enableExternalFallback}. */
    public boolean enableExternalFallback;

    /** The server's current relay address, already normalised. May be blank, meaning "no relay". */
    public String externalServerAddress;

    /** The server's current {@code preferExternalTransport}. */
    public boolean preferExternalTransport;

    /** Whether the <em>server</em> considers this player allowed to edit. A hint for rendering only. */
    public boolean editable;

    /** How the exchange concluded. Never {@code null} on a payload a server produced. */
    public ServerSettingsStatus status;

    /** A human-readable reason when {@link #status} is a refusal, otherwise {@code null}. */
    public String detail;

    public ServerSettingsPayload() {
    }

    public ServerSettingsPayload(
            long correlationId,
            boolean enableExternalFallback,
            String externalServerAddress,
            boolean preferExternalTransport,
            boolean editable,
            ServerSettingsStatus status,
            String detail) {
        this.correlationId = correlationId;
        this.enableExternalFallback = enableExternalFallback;
        this.externalServerAddress = externalServerAddress;
        this.preferExternalTransport = preferExternalTransport;
        this.editable = editable;
        this.status = status;
        this.detail = detail;
    }

    /**
     * Builds an answer from the configuration as it currently stands on the server.
     *
     * @param correlationId the id of the request being answered.
     * @param config        the live server configuration, read after any write was persisted.
     * @param editable      whether the server's own permission check said this player may edit.
     * @param status        how the exchange concluded.
     * @param detail        a refusal reason, or {@code null}.
     * @return the payload to reply with.
     */
    public static ServerSettingsPayload of(
            long correlationId,
            EunomiaServerConfig config,
            boolean editable,
            ServerSettingsStatus status,
            String detail) {
        String address = config.externalServerAddress();
        return new ServerSettingsPayload(
                correlationId,
                config.externalFallbackEnabled(),
                address == null ? "" : address,
                config.preferExternalTransport(),
                editable,
                status,
                detail);
    }

    /** The status, defaulting to {@link ServerSettingsStatus#OK} for a payload that arrived without one. */
    public ServerSettingsStatus statusOrOk() {
        return status == null ? ServerSettingsStatus.OK : status;
    }
}
