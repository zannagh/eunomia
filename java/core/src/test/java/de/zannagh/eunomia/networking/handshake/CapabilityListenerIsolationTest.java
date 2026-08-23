package de.zannagh.eunomia.networking.handshake;

import com.google.gson.Gson;
import de.zannagh.eunomia.networking.comms.ClientTransport;
import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.packets.PacketType;
import de.zannagh.eunomia.networking.serialization.NetworkSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression cover for the capability fan-out starving its own listeners.
 *
 * <p>{@link ServerCapabilities#onResolved} listeners are not peers that merely observe. One of them is the send
 * gate, which decides whether packets parked on join may leave; others dereference the running Minecraft client
 * or raise toasts. The fan-out used to be a bare loop, so the first listener to throw aborted it and every
 * listener behind it never ran - meaning every gated join-time send parked silently for the whole connection,
 * with nothing in the log connecting the missing packets to the unrelated listener that blew up.</p>
 *
 * <p>Two properties are asserted here: a throwing listener does not stop the ones after it, and it does not stop
 * the send gate in particular - which is also why the gate's listener is now installed eagerly, first, rather
 * than lazily on the first gated send (which always made it last).</p>
 */
class CapabilityListenerIsolationTest {

    private static final PacketType<Msg> C2S = PacketType.serverbound("test", "listener_isolation", Msg.class);

    private final List<String> delivered = new ArrayList<>();

    @BeforeEach
    void setUp() {
        CommunicationManager.resetForTesting();
        NetworkSerializer.setGson(new Gson());
        CommunicationManager.register(C2S);
        CommunicationManager.setClientTransport(new RecordingTransport());
    }

    @AfterEach
    void tearDown() {
        CommunicationManager.resetForTesting();
    }

    @Test
    void aThrowingListenerDoesNotStopTheListenersBehindIt() {
        ServerCapabilities caps = CommunicationManager.serverCapabilities();
        List<String> ran = new ArrayList<>();
        caps.onResolved(ignored -> {
            throw new IllegalStateException("the transport selector dereferenced a null client");
        });
        caps.onResolved(ignored -> ran.add("second"));
        caps.onResolved(ignored -> ran.add("third"));

        assertDoesNotThrow(() -> caps.markPresent(1, List.of(C2S.channelKey())));

        assertEquals(List.of("second", "third"), ran);
    }

    @Test
    void aThrowingListenerDoesNotStarveTheSendGate() {
        CommunicationManager.beginClientConnection();
        // Registered before the gated send, i.e. exactly the position that used to precede the gate's own
        // lazily attached listener.
        CommunicationManager.serverCapabilities().onResolved(ignored -> {
            throw new IllegalStateException("Minecraft.getInstance() was null");
        });

        CommunicationManager.sendToServer(C2S, new Msg("join"));
        assertTrue(delivered.isEmpty(), "parked while the probe is unresolved");

        CommunicationManager.serverCapabilities().markPresent(1, List.of(C2S.channelKey()));

        assertEquals(List.of("join"), delivered,
                "the gate must still flush even though an earlier listener threw");
    }

    @Test
    void aThrowingListenerRegisteredAfterResolutionDoesNotEscapeToTheRegistrant() {
        ServerCapabilities caps = CommunicationManager.serverCapabilities();
        caps.markPresent(1, List.of(C2S.channelKey()));

        // A consuming mod that initializes late gets its listener invoked immediately; if that throws back into
        // its initializer, the rest of that mod's client init - possibly including its own gated sends - dies.
        assertDoesNotThrow(() -> caps.onResolved(ignored -> {
            throw new IllegalStateException("late registration blew up");
        }));
    }

    private final class RecordingTransport implements ClientTransport {
        @Override
        public <T> void sendToServer(PacketType<T> type, T data) {
            if (type == C2S) {
                delivered.add(((Msg) data).value);
            }
        }
    }

    public static final class Msg {
        public String value;

        public Msg() {
        }

        public Msg(String value) {
            this.value = value;
        }
    }
}
