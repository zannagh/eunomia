package de.zannagh.eunomia.clients;

import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit-covers the "blocked" fallback contract of {@link ExternalServerClient} without a live relay: a hard block
 * (HTTP 403 on a REST send, or a WebSocket close with code 1008/policy-violation) must stop the client and fire
 * the fallback callback exactly once, while the benign 409 ("no live socket"), other 5xx errors, and ordinary
 * close codes must leave the client running and un-blocked so it keeps retrying/reconnecting.
 */
class RelayBlockedFallbackTest {

    private static ExternalServerClient client(AtomicInteger blockedCalls) {
        return new ExternalServerClient(
                "127.0.0.1:1", "mc.example:25565", "Example SMP", UUID.randomUUID(),
                NOPLogger.NOP_LOGGER, blockedCalls::incrementAndGet);
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
}
