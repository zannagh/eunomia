package de.zannagh.eunomia.clients;

import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the socket-lifecycle reporting that keeps serverbound sends from being fired at the relay before it has
 * a session for them. The relay answers a REST {@code PUT} with HTTP 409 when no live WebSocket session exists
 * for the identity/scope and nothing retries a 409, so anything sent during the handshake - or during a backoff
 * reconnect - would be lost outright. Driven through the package-private lifecycle hooks, no live relay involved.
 */
class RelayConnectionStateTest {

    private final List<RelayConnectionState> states = new ArrayList<>();

    private ExternalServerClient client() {
        return client(() -> { }, states::add);
    }

    /**
     * Builds a client whose connect action is a no-op, so {@code start()} never opens a real socket. Without that
     * seam every test here would dial a dead address, and the resulting failure would schedule a recurring
     * backoff reconnect that outlives the test and races extra states into the list it is asserting on.
     */
    private static ExternalServerClient client(Runnable onBlocked, Consumer<RelayConnectionState> onState) {
        ExternalServerClient client = new ExternalServerClient(
                "127.0.0.1:1", "mc.example:25565", "Example SMP", UUID.randomUUID(),
                NOPLogger.NOP_LOGGER, onBlocked, onState);
        client.connector = () -> { };
        return client;
    }

    @Test
    void aSuccessfulOpenIsWhatReportsTheRelayUsable() {
        ExternalServerClient client = client();
        client.start();
        assertEquals(List.of(), states, "starting only initiates the connect - it must not claim OPEN");

        client.handleConnected(null);

        assertEquals(List.of(RelayConnectionState.OPEN), states);
        assertTrue(client.hasEverConnected());
    }

    @Test
    void aSocketThatNeverOpensReportsUnavailableSoNothingIsHeldForever() {
        ExternalServerClient client = client();
        client.start();

        client.handleConnectFailure(new IllegalStateException("connection refused"));

        assertEquals(List.of(RelayConnectionState.UNAVAILABLE), states,
                "before any successful open the relay is unavailable, not merely reconnecting");
        assertFalse(client.hasEverConnected());
        assertTrue(client.isRunning(), "it still keeps retrying in the background");
    }

    @Test
    void aDropAfterHavingBeenOpenReportsReconnectingSoSendsParkAgain() {
        ExternalServerClient client = client();
        client.start();
        client.handleConnected(null);

        // 1006 abnormal closure: the relay went away mid-session.
        client.handleClose(1006, "abnormal");

        assertEquals(List.of(RelayConnectionState.OPEN, RelayConnectionState.RECONNECTING), states);
        assertTrue(client.isRunning(), "a non-1008 close keeps the client alive for the backoff reconnect");
    }

    @Test
    void aFailureDuringReconnectIsStillOnlyReconnecting() {
        ExternalServerClient client = client();
        client.start();
        client.handleConnected(null);
        client.handleClose(1006, "abnormal");

        client.handleConnectFailure(new IllegalStateException("still down"));

        assertEquals(
                List.of(RelayConnectionState.OPEN, RelayConnectionState.RECONNECTING,
                        RelayConnectionState.RECONNECTING),
                states,
                "once the socket has worked, a failed reconnect must keep sends parked rather than release them");
    }

    @Test
    void aReconnectReportsOpenAgainSoParkedSendsFlush() {
        ExternalServerClient client = client();
        client.start();
        client.handleConnected(null);
        client.handleClose(1006, "abnormal");

        client.handleConnected(null);

        assertEquals(
                List.of(RelayConnectionState.OPEN, RelayConnectionState.RECONNECTING, RelayConnectionState.OPEN),
                states);
    }

    @Test
    void aHardBlockReportsNoStateAndLeavesTheBlockedPathToTakeOver() {
        List<RelayConnectionState> seen = new ArrayList<>();
        boolean[] blocked = {false};
        ExternalServerClient client = client(() -> blocked[0] = true, seen::add);
        client.start();
        client.handleConnected(null);
        seen.clear();

        client.handleClose(1008, "blocked");

        assertTrue(blocked[0], "1008 still hands control back to the Minecraft transport");
        assertFalse(client.isRunning());
        assertEquals(List.of(), seen, "a terminal block is not a reconnect - it must not re-park sends");
    }

    @Test
    void aStoppedClientReportsNothing() {
        ExternalServerClient client = client();
        client.start();
        client.stop();
        states.clear();

        client.handleConnectFailure(new IllegalStateException("late failure"));
        client.handleClose(1006, "late close");

        assertEquals(List.of(), states, "lifecycle events arriving after stop() must not reactivate the gate");
    }
}
