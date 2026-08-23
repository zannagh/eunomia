package de.zannagh.eunomia.networking.admin;

/**
 * "Tell me your current Cloud Sync policy." The whole payload is a correlation id, and that is deliberate:
 * a request that carried an identity, a permission claim or a "I am an admin" flag would be a request the
 * server had to decide whether to believe. It carries none, so there is nothing to forge - the acting player
 * is whoever the authenticated connection says they are.
 *
 * @since 0.3.0
 */
public class ServerSettingsRequestPayload {

    /**
     * Echoed verbatim in the answer so a client with more than one in-flight exchange can match the reply to
     * the callback that asked for it. Never interpreted server-side, so a repeated or nonsensical value can
     * only ever confuse the sender's own bookkeeping.
     */
    public long correlationId;

    public ServerSettingsRequestPayload() {
    }

    public ServerSettingsRequestPayload(long correlationId) {
        this.correlationId = correlationId;
    }
}
