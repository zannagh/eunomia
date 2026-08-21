package de.zannagh.eunomia.clients;

import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit-covers the terminal-outcome contract of {@link ExternalServerClient} without a live relay. Two outcomes
 * stop the client and fire the fallback callback exactly once: a hard block (HTTP 403 on a REST send, or a
 * WebSocket close with code 1008/policy-violation) and an unsupported API version (WebSocket close with code
 * 4001). The benign 409 ("no live socket"), other 5xx errors, and ordinary close codes must leave the client
 * running and un-blocked so it keeps retrying/reconnecting.
 */
class RelayBlockedFallbackTest {

    private static ExternalServerClient client(AtomicInteger blockedCalls) {
        return client(blockedCalls, new AtomicInteger());
    }

    /**
     * A client whose connect action only counts attempts, so {@code start()} opens no real socket and a scheduled
     * reconnect is observable as a second count rather than as a background dial at a dead address.
     */
    private static ExternalServerClient client(AtomicInteger blockedCalls, AtomicInteger connectAttempts) {
        ExternalServerClient client = new ExternalServerClient(
                "127.0.0.1:1", "mc.example:25565", "Example SMP", UUID.randomUUID(),
                NOPLogger.NOP_LOGGER, blockedCalls::incrementAndGet);
        client.connector = connectAttempts::incrementAndGet;
        return client;
    }

    /** Waits up to {@code timeoutMs} for {@code counter} to reach {@code target}; returns what it actually saw. */
    private static int awaitCount(AtomicInteger counter, int target, long timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (counter.get() < target && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        return counter.get();
    }

    @Test
    void http403StopsAndFallsBackOnce() {
        AtomicInteger blocked = new AtomicInteger();
        ExternalServerClient client = client(blocked);

        client.handleSendStatus("test:ch", 403, "blocked");

        assertTrue(client.isBlocked(), "403 must mark the client blocked");
        assertFalse(client.isRunning(), "a blocked client must stop (no further sends)");
        assertEquals(1, blocked.get(), "fallback callback fires once on block");

        // A second block signal must not re-fire the fallback.
        client.handleSendStatus("test:ch", 403, "blocked again");
        assertEquals(1, blocked.get(), "block handling is idempotent");
    }

    @Test
    void http409DoesNotFallBack() {
        AtomicInteger blocked = new AtomicInteger();
        ExternalServerClient client = client(blocked);

        client.handleSendStatus("test:ch", 409, "no live socket");

        assertFalse(client.isBlocked(), "409 is benign and must not block");
        assertEquals(0, blocked.get(), "no fallback on 409");
    }

    @Test
    void http500DoesNotFallBack() {
        AtomicInteger blocked = new AtomicInteger();
        ExternalServerClient client = client(blocked);

        client.handleSendStatus("test:ch", 500, "boom");

        assertFalse(client.isBlocked(), "5xx is transient and must not block");
        assertEquals(0, blocked.get(), "no fallback on 500");
    }

    @Test
    void wsClose1008StopsAndFallsBack() {
        AtomicInteger blocked = new AtomicInteger();
        ExternalServerClient client = client(blocked);

        client.handleClose(1008, "server is blocked");

        assertTrue(client.isBlocked(), "close code 1008 must mark the client blocked");
        assertFalse(client.isRunning(), "a blocked client must stop reconnecting");
        assertEquals(1, blocked.get(), "fallback callback fires once on 1008");
    }

    @Test
    void wsCloseOtherCodeDoesNotFallBack() {
        AtomicInteger blocked = new AtomicInteger();
        ExternalServerClient client = client(blocked);

        // Ordinary/abnormal closes (normal closure, going away, abnormal) must not trip the block path.
        client.handleClose(1000, "normal");
        client.handleClose(1001, "going away");
        client.handleClose(1006, "abnormal");

        assertFalse(client.isBlocked(), "non-1008 closes must not block");
        assertEquals(0, blocked.get(), "no fallback on ordinary closes");
    }

    @Test
    void wsClose4001IsTerminalAndFallsBackWithoutBeingABlock() {
        AtomicInteger blocked = new AtomicInteger();
        AtomicInteger connects = new AtomicInteger();
        ExternalServerClient client = client(blocked, connects);
        client.start();
        assertEquals(1, connects.get(), "start() makes exactly one connect attempt");

        client.handleClose(4001, "unsupported api version");

        assertFalse(client.isRunning(), "an unsupported API version must stop the client, not reconnect");
        assertEquals(1, blocked.get(), "the fallback to the Minecraft transport fires once");
        assertFalse(client.isBlocked(),
                "a version mismatch is not a block - conflating the two would misreport why syncing stopped");
        assertEquals(1, connects.get(), "no further connect attempt may be scheduled after 4001");

        // A second 4001 (e.g. a late close on an already-dead socket) must not re-fire the fallback.
        client.handleClose(4001, "unsupported api version");
        assertEquals(1, blocked.get(), "terminal handling is idempotent");
    }

    @Test
    void wsClose4001MatchesTheRelaysUnsupportedVersionCode() {
        // The mod and the C# relay have to agree on the literal; if this drifts, the mod reconnects forever.
        assertEquals(4001, RelayProtocol.UNSUPPORTED_VERSION_CLOSE);
        assertEquals(1008, RelayProtocol.POLICY_VIOLATION_CLOSE);
        assertEquals(403, RelayProtocol.BLOCKED_STATUS);
    }

    @Test
    void anOrdinaryCloseStillSchedulesAReconnect() throws InterruptedException {
        AtomicInteger blocked = new AtomicInteger();
        AtomicInteger connects = new AtomicInteger();
        ExternalServerClient client = client(blocked, connects);
        client.start();

        client.handleClose(1006, "abnormal");

        assertTrue(client.isRunning(), "an ordinary close keeps the client alive");
        // The first backoff step is ~1s plus jitter; give it room without making the wait itself the assertion.
        assertEquals(2, awaitCount(connects, 2, 5000), "a non-terminal close must schedule another connect");
        assertEquals(0, blocked.get(), "no fallback on an ordinary close");
        client.stop();
    }
}
