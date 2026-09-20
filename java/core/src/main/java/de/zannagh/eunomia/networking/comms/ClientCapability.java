package de.zannagh.eunomia.networking.comms;

import java.util.UUID;

/**
 * The server's per-player answer to "does this client speak Eunomia?" - the server-side mirror of the
 * client's {@link de.zannagh.eunomia.networking.handshake.ServerCapabilities} view.
 *
 * <p>It is deliberately tri-state rather than a boolean. "Not yet known" and "known not to" must lead to
 * different behaviour: the first parks a clientbound send until the answer lands, the second drops it. A
 * boolean would collapse the two and silently break every well-behaved client whose HELLO simply had not
 * arrived yet when a consumer pushed its join-time payload.</p>
 *
 * @see CommunicationManager#playerCapability(UUID)
 */
public enum ClientCapability {

    /** No HELLO has arrived from this player yet, and the capability probe window has not closed. */
    UNKNOWN,

    /** The player sent a Eunomia HELLO, so their client understands Eunomia's wire format. */
    PRESENT,

    /**
     * No HELLO arrived before the window closed. The client is a vanilla client, or - the case this whole
     * mechanism exists for - runs a pre-Eunomia build of the consuming mod whose shipped decoder would read
     * Eunomia's framing as a bogus length and disconnect itself.
     */
    ABSENT
}
