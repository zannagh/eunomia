package de.zannagh.eunomia.clients;

/**
 * What the relay's receive WebSocket is currently doing, reported by {@link ExternalServerClient} so the caller
 * can decide where serverbound packets may go.
 *
 * <p>This matters because the relay refuses a REST {@code PUT} with HTTP 409 unless a live WebSocket session
 * exists for the same identity and scope, and nothing retries a 409 - the packet is simply lost. So sends must
 * be held while the socket is down rather than fired at a session that does not exist yet.
 */
public enum RelayConnectionState {

    /** The socket is open: the relay has a live session and will accept REST sends. */
    OPEN,

    /** The socket dropped after having been open; a backoff reconnect is scheduled. Hold sends. */
    RECONNECTING,

    /**
     * The socket has never opened. The relay answered {@code /health} but its WebSocket is not usable, so
     * anything held for it should be released rather than waiting forever; a later {@link #OPEN} still recovers.
     */
    UNAVAILABLE
}
