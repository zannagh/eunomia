package de.zannagh.eunomia.configuration;

import de.zannagh.eunomia.networking.handshake.ServerSyncPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The fluent common-side builder: every setting round-trips into the holder that actually governs it, an
 * unconfigured install is bit-for-bit the framework default, and the chain is order-independent.
 */
class EunomiaConfigurationTest {

    @BeforeEach
    void setUp() {
        EunomiaSyncDefaults.reset();
        EunomiaClientOptions.reset();
        EunomiaSyncSettings.bindClientConfigSource(null);
        EunomiaSyncSettings.bindAdvertisedPolicySource(() -> ServerSyncPolicy.UNKNOWN);
    }

    @AfterEach
    void tearDown() {
        EunomiaSyncDefaults.reset();
        EunomiaClientOptions.reset();
        EunomiaSyncSettings.resetForTesting();
    }

    // ── Defaults ────────────────────────────────────────────────────────────────────────────────

    @Test
    void anUnconfiguredInstallIsExactlyTheFrameworkDefault() {
        assertThat(EunomiaSyncDefaults.enableExternalFallback()).isNull();
        assertThat(EunomiaSyncDefaults.externalServerAddress()).isNull();
        assertThat(EunomiaSyncDefaults.preferExternalTransport()).isNull();
        assertThat(EunomiaClientOptions.toastsEnabled()).isTrue();
        assertThat(EunomiaClientOptions.settingsButtonEnabled()).isTrue();
        assertThat(EunomiaSyncSettings.externalServerAddress())
                .isEqualTo(EunomiaDefaults.DEFAULT_EXTERNAL_SERVER_ADDRESS);
    }

    @Test
    void aChainThatIsNeverAppliedChangesNothing() {
        new EunomiaConfiguration()
                .externalFallback(true)
                .externalServerAddress("https://sync.mymod.example")
                .toasts(false)
                .settingsButton(false);

        assertThat(EunomiaSyncDefaults.enableExternalFallback()).isNull();
        assertThat(EunomiaClientOptions.toastsEnabled()).isTrue();
        assertThat(EunomiaClientOptions.settingsButtonEnabled()).isTrue();
    }

    // ── Round trips ─────────────────────────────────────────────────────────────────────────────

    @Test
    void everySettingRoundTripsIntoItsHolder() {
        new EunomiaConfiguration()
                .externalFallback(true)
                .externalServerAddress("https://sync.mymod.example")
                .preferExternalTransport(true)
                .toasts(false)
                .settingsButton(false)
                .apply();

        assertThat(EunomiaSyncDefaults.enableExternalFallback()).isTrue();
        assertThat(EunomiaSyncDefaults.externalServerAddress()).isEqualTo("https://sync.mymod.example");
        assertThat(EunomiaSyncDefaults.preferExternalTransport()).isTrue();
        assertThat(EunomiaClientOptions.toastsEnabled()).isFalse();
        assertThat(EunomiaClientOptions.settingsButtonEnabled()).isFalse();
    }

    @Test
    void theSyncDefaultsLandOnRungThreeOfThePrecedenceChain() {
        new EunomiaConfiguration()
                .externalFallback(true)
                .externalServerAddress("https://sync.mymod.example")
                .apply();

        assertThat(EunomiaSyncSettings.externalFallbackEnabled()).isTrue();
        assertThat(EunomiaSyncSettings.externalServerAddress()).isEqualTo("https://sync.mymod.example");
    }

    @Test
    void theServerAdvertisedPolicyStillOutranksWhatTheBuilderSet() {
        new EunomiaConfiguration().externalFallback(true).apply();
        EunomiaSyncSettings.bindAdvertisedPolicySource(() -> new ServerSyncPolicy(false, null, null));

        assertThat(EunomiaSyncSettings.externalFallbackEnabled()).isFalse();
    }

    @Test
    void passingNullClearsAPreviouslyExpressedOpinion() {
        new EunomiaConfiguration().externalFallback(true).apply();
        new EunomiaConfiguration().externalFallback(null).apply();

        assertThat(EunomiaSyncDefaults.enableExternalFallback()).isNull();
    }

    @Test
    void resetSyncDefaultsWipesEarlierOpinionsBeforeTheChainsOwnValuesLand() {
        new EunomiaConfiguration()
                .externalFallback(true)
                .preferExternalTransport(true)
                .apply();

        new EunomiaConfiguration()
                .resetSyncDefaults()
                .externalFallback(false)
                .apply();

        assertThat(EunomiaSyncDefaults.enableExternalFallback()).isFalse();
        assertThat(EunomiaSyncDefaults.preferExternalTransport()).isNull();
    }

    // ── Order independence ──────────────────────────────────────────────────────────────────────

    @Test
    void theChainIsOrderIndependent() {
        new EunomiaConfiguration()
                .toasts(false)
                .externalServerAddress("https://sync.mymod.example")
                .externalFallback(true)
                .apply();

        Boolean fallback = EunomiaSyncDefaults.enableExternalFallback();
        String address = EunomiaSyncDefaults.externalServerAddress();
        boolean toasts = EunomiaClientOptions.toastsEnabled();

        EunomiaSyncDefaults.reset();
        EunomiaClientOptions.reset();

        new EunomiaConfiguration()
                .externalFallback(true)
                .toasts(false)
                .externalServerAddress("https://sync.mymod.example")
                .apply();

        assertThat(EunomiaSyncDefaults.enableExternalFallback()).isEqualTo(fallback);
        assertThat(EunomiaSyncDefaults.externalServerAddress()).isEqualTo(address);
        assertThat(EunomiaClientOptions.toastsEnabled()).isEqualTo(toasts);
    }

    @Test
    void configuringAfterTheClientInstalledItsObserverStillReconcilesTheButton() {
        // Stands in for EunomiaClient#init() having already installed EunomiaSettingsEntryPoint's reconciler.
        AtomicInteger reconciles = new AtomicInteger();
        EunomiaClientOptions.setSettingsButtonObserver(reconciles::incrementAndGet);

        new EunomiaConfiguration().settingsButton(false).apply();

        assertThat(EunomiaClientOptions.settingsButtonEnabled()).isFalse();
        assertThat(reconciles.get()).isEqualTo(1);
    }

    @Test
    void configuringBeforeTheClientInstalledItsObserverIsStillObservedLater() {
        new EunomiaConfiguration().settingsButton(false).apply();

        // The observer arrives afterwards, exactly as a late EunomiaClient#init() would; the flag is a
        // holder, not an event, so the value it reads is the one the consumer set.
        EunomiaClientOptions.setSettingsButtonObserver(() -> { });

        assertThat(EunomiaClientOptions.settingsButtonEnabled()).isFalse();
    }

    // ── Misuse ──────────────────────────────────────────────────────────────────────────────────

    @Test
    void reusingASpentBuilderFailsLoudly() {
        EunomiaConfiguration configuration = new EunomiaConfiguration().toasts(false);
        configuration.apply();

        assertThatThrownBy(() -> configuration.externalFallback(true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already applied");
        assertThatThrownBy(configuration::apply).isInstanceOf(IllegalStateException.class);
    }
}
