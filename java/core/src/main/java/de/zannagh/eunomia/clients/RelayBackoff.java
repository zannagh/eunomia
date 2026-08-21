package de.zannagh.eunomia.clients;

/**
 * Capped exponential backoff with jitter for the relay's receive-socket reconnects. Kept apart from
 * {@link ExternalServerClient} so the client's lifecycle code says <em>when</em> to reconnect without also
 * carrying the arithmetic of <em>how long</em> to wait.
 */
final class RelayBackoff {

    private static final long BASE_MS = 1000;
    private static final long MAX_MS = 30_000;
    private static final int MAX_DOUBLINGS = 5;

    /** How many delays have been handed out since the last successful open. */
    private volatile int attempt;

    /** Called on a successful open, so the next drop retries promptly instead of at the previous cap. */
    void reset() {
        attempt = 0;
    }

    /** The delay before the next connect attempt, in milliseconds, and advances the sequence. */
    long nextDelayMs() {
        long backoff = Math.min(MAX_MS, BASE_MS * (1L << Math.min(attempt, MAX_DOUBLINGS)));
        attempt++;
        // Jitter keeps a relay restart from being hit by every client at exactly the same instant.
        return backoff + (long) (backoff * 0.2 * Math.random());
    }
}
