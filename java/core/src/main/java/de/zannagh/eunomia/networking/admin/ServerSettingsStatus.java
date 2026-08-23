package de.zannagh.eunomia.networking.admin;

/**
 * How a Cloud Sync settings exchange concluded, as reported back to the asking client.
 *
 * <p>Kept as a distinct field rather than inferred from the values: a client that submitted a write and
 * got the old values back cannot otherwise tell "refused" from "accepted, and the server normalised your
 * address into something that happens to differ from what you typed".</p>
 *
 * @since 0.3.0
 */
public enum ServerSettingsStatus {

    /** A plain read: the accompanying values are the server's current policy. */
    OK,

    /** A write was accepted, persisted, and is what the accompanying values now show. */
    APPLIED,

    /** A write was refused because the server does not consider the sender an administrator. */
    DENIED,

    /** A write was refused because the submitted relay address is not a usable http/https address. */
    INVALID_ADDRESS,

    /**
     * Client-side only: no answer arrived before the exchange timed out. Never put on the wire by a
     * server - it is what the client-side API synthesises so a caller's callback always fires exactly
     * once, even when the joined server does not run eunomia at all.
     */
    UNAVAILABLE
}
