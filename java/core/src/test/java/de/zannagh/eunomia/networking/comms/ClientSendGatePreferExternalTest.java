package de.zannagh.eunomia.networking.comms;

import com.google.gson.Gson;
import de.zannagh.eunomia.networking.handshake.ServerCapabilities;
import de.zannagh.eunomia.networking.packets.PacketType;
import de.zannagh.eunomia.networking.serialization.NetworkSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression cover for the {@code preferExternalTransport} case, where the joined Minecraft server <em>does</em>
 * run Eunomia and the relay is nevertheless supposed to win.
 *
 * <p>The gate used to fast-path on {@link ServerCapabilities#isPresent()} alone. That put the join-time payload
 * on the Minecraft transport - the exact destination the preference exists to bypass - and, once the relay
 * transport had been installed but before its socket opened, sent later packets over REST into the relay's
 * HTTP 409 ("no live session"), which nothing retries. Both are silent losses.</p>
 *
 * <p>The fix is {@link CommunicationManager#setExternalTransportPreferred}: while it answers true, a "present"
 * resolution parks instead of flushing, and only an explicit conclusion from the transport selector -
 * {@link CommunicationManager#setExternalTransportActive(boolean) setExternalTransportActive(true)},
 * {@link CommunicationManager#concludeMinecraftTransport()} or {@link CommunicationManager#concludeNoRelay()} -
 * releases the queue.</p>
 */
class ClientSendGatePreferExternalTest {

    private static final PacketType<Msg> C2S = PacketType.serverbound("test", "prefer_external", Msg.class);

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

    /** The relay is configured and preferred, so the selector will decide asynchronously even on a hit. */
    private void preferExternal() {
        CommunicationManager.setExternalTransportPreferred(() -> true);
    }

    private void resolvePresent() {
        CommunicationManager.serverCapabilities().markPresent(1, List.of(C2S.channelKey()));
    }

    @Test
    void aPresentResolutionDoesNotFlushToMinecraftWhileTheRelayMayStillWin() {
        preferExternal();
        CommunicationManager.beginClientConnection();
        CommunicationManager.sendToServer(C2S, new Msg("join"));

        resolvePresent();

        assertTrue(delivered.isEmpty(),
                "the join payload must not land on the MC server preferExternalTransport exists to bypass");

        CommunicationManager.setExternalTransportActive(true);
        assertEquals(List.of("join"), delivered, "it flushes to the relay once the relay's socket is open");
    }

    @Test
    void sendsMadeAfterThePresentResolutionAlsoPark() {
        // This is the second half of the defect: the relay transport is installed before its socket opens, so a
        // send that fast-paths here goes out over REST and comes back 409 - dropped with only a warning.
        preferExternal();
        CommunicationManager.beginClientConnection();
        resolvePresent();
        // What the selector does the instant it installs ExternalClientTransport.
        CommunicationManager.setExternalTransportActive(false);

        CommunicationManager.sendToServer(C2S, new Msg("during-handshake"));
        assertTrue(delivered.isEmpty(), "no destination is settled yet - park, do not fire into a 409");

        CommunicationManager.setExternalTransportActive(true);
        assertEquals(List.of("during-handshake"), delivered);
    }

    @Test
    void theRelayFailingReleasesTheQueueOverMinecraftRatherThanDroppingIt() {
        // Reachability probe fails / the socket never opens, while the MC server does speak Eunomia. There is a
        // perfectly good destination, so concluding "no relay" here would throw the queue away for nothing.
        preferExternal();
        CommunicationManager.beginClientConnection();
        CommunicationManager.sendToServer(C2S, new Msg("join"));
        resolvePresent();
        assertTrue(delivered.isEmpty(), "parked while the relay decision is in flight");

        CommunicationManager.concludeMinecraftTransport();
        assertEquals(List.of("join"), delivered, "the parked send falls back onto the game connection");

        CommunicationManager.sendToServer(C2S, new Msg("later"));
        assertEquals(List.of("join", "later"), delivered, "and the connection stays settled on Minecraft");
    }

    @Test
    void withoutThePreferenceAPresentResolutionStillFlushesImmediately() {
        // The ordinary case must be untouched: no preference installed, so present means Minecraft, at once.
        CommunicationManager.beginClientConnection();
        CommunicationManager.sendToServer(C2S, new Msg("join"));

        resolvePresent();

        assertEquals(List.of("join"), delivered);
    }

    @Test
    void aPreferenceThatThrowsFailsOpenInsteadOfParkingForever() {
        // The predicate reads the effective settings chain, which is consumer-configurable; if it blows up the
        // gate must still resolve to something rather than holding the connection's packets indefinitely.
        CommunicationManager.setExternalTransportPreferred(() -> {
            throw new IllegalStateException("settings source exploded");
        });
        CommunicationManager.beginClientConnection();
        CommunicationManager.sendToServer(C2S, new Msg("join"));

        resolvePresent();

        assertEquals(List.of("join"), delivered);
    }

    @Test
    void theParkedQueueIsBounded() {
        // Parking is open-ended by design (a flapping relay stays RECONNECTING), so the queue needs a ceiling.
        // Oldest-first eviction: the newest state update is the one worth keeping.
        preferExternal();
        CommunicationManager.beginClientConnection();
        int overflow = 40;
        for (int i = 0; i < ClientSendGate.MAX_PENDING + overflow; i++) {
            CommunicationManager.sendToServer(C2S, new Msg(Integer.toString(i)));
        }

        CommunicationManager.setExternalTransportActive(true);

        assertEquals(ClientSendGate.MAX_PENDING, delivered.size(), "the queue never grows past its cap");
        assertEquals(Integer.toString(overflow), delivered.get(0), "the oldest sends are the ones evicted");
        assertEquals(Integer.toString(ClientSendGate.MAX_PENDING + overflow - 1),
                delivered.get(delivered.size() - 1), "the newest send survives");
    }

    /** Records only the test channel's sends; the handshake HELLO the probe emits is on another channel. */
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
