package de.zannagh.eunomia.diagnostics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the suppression rules for the two sync diagnostic toasts. These are the only part of the diagnostics
 * that carries judgement - the client-side call sites merely sample facts - so this is where "does eunomia nag
 * the player" is actually decided, and it is checked here rather than in a client gametest.
 */
class SyncDiagnosticsTest {

    private static ClientSyncState state(boolean resolved, boolean serverSpeaks, boolean remote, boolean relay) {
        return new ClientSyncState(resolved, serverSpeaks, remote, relay);
    }

    @Test
    void missingSyncWarnsOnARemoteServerWithNoEunomiaAndNoRelayConfigured() {
        assertThat(SyncDiagnostics.shouldWarnMissingServerSync(state(true, false, true, false))).isTrue();
    }

    @Test
    void missingSyncStaysQuietWhileTheProbeIsStillInFlight() {
        assertThat(SyncDiagnostics.shouldWarnMissingServerSync(state(false, false, true, false))).isFalse();
    }

    @Test
    void missingSyncStaysQuietWhenTheServerSpeaksEunomia() {
        assertThat(SyncDiagnostics.shouldWarnMissingServerSync(state(true, true, true, false))).isFalse();
    }

    @Test
    void missingSyncStaysQuietInSingleplayerOrOnLan() {
        assertThat(SyncDiagnostics.shouldWarnMissingServerSync(state(true, false, false, false))).isFalse();
    }

    @Test
    void missingSyncStaysQuietWhenCloudSyncIsAlreadyConfigured() {
        assertThat(SyncDiagnostics.shouldWarnMissingServerSync(state(true, false, true, true))).isFalse();
    }

    @Test
    void relayUnreachableWarnsWhenTheConfiguredRelayIsTheOnlyDestinationLeft() {
        assertThat(SyncDiagnostics.shouldWarnRelayUnreachable(state(true, false, true, true))).isTrue();
    }

    @Test
    void relayUnreachableStaysQuietWhenNoRelayIsConfigured() {
        assertThat(SyncDiagnostics.shouldWarnRelayUnreachable(state(true, false, true, false))).isFalse();
    }

    @Test
    void relayUnreachableStaysQuietWhenTheMinecraftServerStillCarriesSync() {
        assertThat(SyncDiagnostics.shouldWarnRelayUnreachable(state(true, true, true, true))).isFalse();
    }

    @Test
    void relayUnreachableStaysQuietWithoutARemoteConnection() {
        assertThat(SyncDiagnostics.shouldWarnRelayUnreachable(state(true, false, false, true))).isFalse();
    }

    @Test
    void theTwoToastsAreMutuallyExclusiveForEveryPossibleConnection() {
        for (int bits = 0; bits < 16; bits++) {
            ClientSyncState s = state((bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0, (bits & 8) != 0);
            boolean both = SyncDiagnostics.shouldWarnMissingServerSync(s)
                    && SyncDiagnostics.shouldWarnRelayUnreachable(s);
            assertThat(both).as("both toasts fire for %s", s).isFalse();
        }
    }
}
