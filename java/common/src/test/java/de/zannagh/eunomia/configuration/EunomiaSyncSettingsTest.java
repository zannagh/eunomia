package de.zannagh.eunomia.configuration;

import com.google.gson.Gson;
import de.zannagh.eunomia.networking.handshake.ServerSyncPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The four-rung precedence chain: player override > server-advertised policy > consuming mod default >
 * framework default. Each rung is asserted both when it wins and when the rung above it takes over.
 */
class EunomiaSyncSettingsTest {

    private EunomiaConfig config;

    @BeforeEach
    void setUp() {
        EunomiaSyncDefaults.reset();
        config = EunomiaConfig.withCurrentSchema();
        EunomiaSyncSettings.bindClientConfigSource(() -> config);
        EunomiaSyncSettings.bindAdvertisedPolicySource(() -> ServerSyncPolicy.UNKNOWN);
    }

    @AfterEach
    void tearDown() {
        EunomiaSyncDefaults.reset();
        EunomiaSyncSettings.resetForTesting();
    }

    private void advertise(Boolean fallback, String address, Boolean prefer) {
        EunomiaSyncSettings.bindAdvertisedPolicySource(
                () -> new ServerSyncPolicy(fallback, address, prefer));
    }

    private void enforce(Boolean fallback, String address, Boolean prefer, SyncSetting... enforced) {
        EunomiaSyncSettings.bindAdvertisedPolicySource(
                () -> new ServerSyncPolicy(fallback, address, prefer, Set.of(enforced)));
    }

    // ── Rung 4: framework defaults ──────────────────────────────────────────────────────────────

    @Test
    void fallsAllTheWayThroughToTheFrameworkDefaults() {
        assertThat(EunomiaSyncSettings.externalFallbackEnabled())
                .isEqualTo(EunomiaDefaults.DEFAULT_ENABLE_EXTERNAL_FALLBACK);
        assertThat(EunomiaSyncSettings.preferExternalTransport())
                .isEqualTo(EunomiaDefaults.DEFAULT_PREFER_EXTERNAL_TRANSPORT);
        assertThat(EunomiaSyncSettings.externalServerAddress())
                .isEqualTo(EunomiaDefaults.DEFAULT_EXTERNAL_SERVER_ADDRESS);
    }

    @Test
    void frameworkDefaultsAlsoApplyWithNoConfigProviderBoundAtAll() {
        EunomiaSyncSettings.bindClientConfigSource(null);

        assertThat(EunomiaSyncSettings.externalFallbackEnabled()).isFalse();
        assertThat(EunomiaSyncSettings.externalServerAddress())
                .isEqualTo(EunomiaDefaults.DEFAULT_EXTERNAL_SERVER_ADDRESS);
    }

    // ── Rung 3: the consuming mod's defaults ────────────────────────────────────────────────────

    @Test
    void modDefaultsBeatTheFrameworkDefaults() {
        EunomiaSyncDefaults.setEnableExternalFallback(true);
        EunomiaSyncDefaults.setExternalServerAddress("https://mod.example");
        EunomiaSyncDefaults.setPreferExternalTransport(true);

        assertThat(EunomiaSyncSettings.externalFallbackEnabled()).isTrue();
        assertThat(EunomiaSyncSettings.externalServerAddress()).isEqualTo("https://mod.example");
        assertThat(EunomiaSyncSettings.preferExternalTransport()).isTrue();
    }

    @Test
    void aBlankModDefaultAddressIsNoOpinion() {
        EunomiaSyncDefaults.setExternalServerAddress("   ");

        assertThat(EunomiaSyncSettings.externalServerAddress())
                .isEqualTo(EunomiaDefaults.DEFAULT_EXTERNAL_SERVER_ADDRESS);
    }

    // ── Rung 2: the server-advertised policy ────────────────────────────────────────────────────

    @Test
    void serverPolicyBeatsTheModDefaults() {
        EunomiaSyncDefaults.setEnableExternalFallback(false);
        EunomiaSyncDefaults.setExternalServerAddress("https://mod.example");
        EunomiaSyncDefaults.setPreferExternalTransport(false);
        advertise(true, "https://server.example", true);

        assertThat(EunomiaSyncSettings.externalFallbackEnabled()).isTrue();
        assertThat(EunomiaSyncSettings.externalServerAddress()).isEqualTo("https://server.example");
        assertThat(EunomiaSyncSettings.preferExternalTransport()).isTrue();
    }

    @Test
    void anEmptyServerPolicyDoesNotShadowTheRungsBelowIt() {
        EunomiaSyncDefaults.setExternalServerAddress("https://mod.example");
        advertise(null, null, null);

        assertThat(EunomiaSyncSettings.externalServerAddress()).isEqualTo("https://mod.example");
    }

    @Test
    void aServerAdvertisedFalseIsStillAnOpinionAndWinsOverTheModDefault() {
        EunomiaSyncDefaults.setEnableExternalFallback(true);
        advertise(false, null, null);

        assertThat(EunomiaSyncSettings.externalFallbackEnabled()).isFalse();
    }

    // ── Rung 1: the player's override ───────────────────────────────────────────────────────────

    @Test
    void playerOverrideBeatsTheServerPolicy() {
        advertise(true, "https://server.example", true);
        config.setEnableExternalFallback(false);
        config.setExternalServerAddress("https://player.example");
        config.setPreferExternalTransport(false);

        assertThat(EunomiaSyncSettings.externalFallbackEnabled()).isFalse();
        assertThat(EunomiaSyncSettings.externalServerAddress()).isEqualTo("https://player.example");
        assertThat(EunomiaSyncSettings.preferExternalTransport()).isFalse();
    }

    @Test
    void clearingAnOverrideHandsControlBackToTheServer() {
        advertise(true, "https://server.example", true);
        config.setEnableExternalFallback(false);

        assertThat(EunomiaSyncSettings.externalFallbackEnabled()).isFalse();

        config.setEnableExternalFallback(null);

        assertThat(EunomiaSyncSettings.externalFallbackEnabled()).isTrue();
    }

    @Test
    void eachSettingIsResolvedIndependentlyOfTheOthers() {
        advertise(true, "https://server.example", true);
        config.setExternalServerAddress("https://player.example");

        // Only the address was overridden; the other two still come from the server.
        assertThat(EunomiaSyncSettings.externalServerAddress()).isEqualTo("https://player.example");
        assertThat(EunomiaSyncSettings.externalFallbackEnabled()).isTrue();
        assertThat(EunomiaSyncSettings.preferExternalTransport()).isTrue();
    }

    @Test
    void aBlankPlayerAddressIsInheritNotAnEmptyAddress() {
        advertise(null, "https://server.example", null);
        config.setExternalServerAddress("  ");

        assertThat(EunomiaSyncSettings.externalServerAddress()).isEqualTo("https://server.example");
    }

    // ── Derived ─────────────────────────────────────────────────────────────────────────────────

    @Test
    void relayIsUsableOnlyOnceTheFallbackIsEffectivelyEnabled() {
        assertThat(EunomiaSyncSettings.externalRelayUsable()).isFalse();

        advertise(true, null, null);

        // Enabled with no address anywhere still resolves the framework address, so it is usable.
        assertThat(EunomiaSyncSettings.externalRelayUsable()).isTrue();
    }

    // ── S3: a real server config, fed through the real chain ───────────────────────────────────

    /**
     * The test that was missing, and whose absence let the whole defect through CI: every other case here
     * hand-builds a {@link ServerSyncPolicy}, so nothing ever asked what an actual untouched
     * {@code eunomia-server.json} advertises. It advertised a complete policy, which meant rung three - the
     * consuming mod's defaults - was dead against every eunomia server in existence.
     */
    @Test
    void anUntouchedServerConfigLetsTheConsumingModsDefaultsThrough() {
        EunomiaSyncDefaults.setEnableExternalFallback(true);
        EunomiaSyncDefaults.setExternalServerAddress("https://sync.mymod.example");
        EunomiaSyncDefaults.setPreferExternalTransport(true);
        EunomiaSyncSettings.bindAdvertisedPolicySource(() -> new EunomiaServerConfig().toSyncPolicy());

        assertThat(EunomiaSyncSettings.externalFallbackEnabled()).isTrue();
        assertThat(EunomiaSyncSettings.externalServerAddress()).isEqualTo("https://sync.mymod.example");
        assertThat(EunomiaSyncSettings.preferExternalTransport()).isTrue();
    }

    /** The same, with no mod defaults either: the chain must land on the framework defaults, not on nothing. */
    @Test
    void anUntouchedServerConfigFallsAllTheWayToTheFrameworkDefaults() {
        EunomiaSyncSettings.bindAdvertisedPolicySource(
                () -> EunomiaServerConfig.untouched().toSyncPolicy());

        assertThat(EunomiaSyncSettings.externalFallbackEnabled())
                .isEqualTo(EunomiaDefaults.DEFAULT_ENABLE_EXTERNAL_FALLBACK);
        assertThat(EunomiaSyncSettings.externalServerAddress())
                .isEqualTo(EunomiaDefaults.DEFAULT_EXTERNAL_SERVER_ADDRESS);
    }

    /** A server that HAS been configured still outranks the mod, which is the point of rung two existing. */
    @Test
    void aConfiguredServerConfigStillOutranksTheConsumingModsDefaults() {
        EunomiaSyncDefaults.setEnableExternalFallback(true);
        EunomiaSyncDefaults.setExternalServerAddress("https://sync.mymod.example");
        EunomiaSyncSettings.bindAdvertisedPolicySource(
                () -> new EunomiaServerConfig(false, "https://relay.example", false).toSyncPolicy());

        assertThat(EunomiaSyncSettings.externalFallbackEnabled()).isFalse();
        assertThat(EunomiaSyncSettings.externalServerAddress()).isEqualTo("https://relay.example");
    }

    // ── Item 3: the advertised address is validated in the resolver ─────────────────────────────

    /**
     * Nothing between the handshake and {@code PingClient}/{@code ExternalServerClient} used to look at the
     * advertised address at all, so a server could hand every joining player any string it liked. The check
     * lives in the resolver so that every consumer is covered without any of them having to remember.
     */
    @Test
    void anUnusableAdvertisedAddressIsTreatedAsIfTheServerHadSaidNothing() {
        EunomiaSyncDefaults.setExternalServerAddress("https://sync.mymod.example");

        advertise(true, "javascript:alert(1)", null);
        assertThat(EunomiaSyncSettings.externalServerAddress()).isEqualTo("https://sync.mymod.example");

        advertise(true, "http://relay.example#@evil.example", null);
        assertThat(EunomiaSyncSettings.externalServerAddress()).isEqualTo("https://sync.mymod.example");

        advertise(true, "not a url at all", null);
        assertThat(EunomiaSyncSettings.externalServerAddress()).isEqualTo("https://sync.mymod.example");
    }

    /** Falling through is not failing: the rest of the policy is still honoured and sync still happens. */
    @Test
    void anUnusableAdvertisedAddressDoesNotTakeTheRestOfThePolicyDownWithIt() {
        advertise(true, "ftp://relay.example", true);

        assertThat(EunomiaSyncSettings.externalFallbackEnabled()).isTrue();
        assertThat(EunomiaSyncSettings.preferExternalTransport()).isTrue();
        assertThat(EunomiaSyncSettings.externalServerAddress())
                .isEqualTo(EunomiaDefaults.DEFAULT_EXTERNAL_SERVER_ADDRESS);
        assertThat(EunomiaSyncSettings.externalRelayUsable()).isTrue();
    }

    /** A perfectly good advertised address, uppercase scheme and all, still wins - this is not a blocklist. */
    @Test
    void aUsableAdvertisedAddressIsStillHonoured() {
        advertise(true, "HTTPS://relay.example", null);

        assertThat(EunomiaSyncSettings.externalServerAddress()).isEqualTo("HTTPS://relay.example");
    }

    // ── Item 3: notifying about a relay host the player has not used before ─────────────────────

    @Test
    void aServerChosenRelayHostIsAnnouncedExactlyOnce() {
        advertise(true, "https://relay.example", null);

        assertThat(EunomiaSyncSettings.recordRelayHostAndReportIfNewlyServerChosen())
                .isEqualTo("relay.example");
        assertThat(EunomiaSyncSettings.recordRelayHostAndReportIfNewlyServerChosen()).isNull();
        assertThat(config.hasSeenRelayHost("relay.example")).isTrue();
    }

    /** Host, not URL: a port, path or scheme change is the same party and must not nag a second time. */
    @Test
    void aPortOrPathChangeOnAKnownHostIsNotAnnouncedAgain() {
        advertise(true, "https://relay.example", null);
        assertThat(EunomiaSyncSettings.recordRelayHostAndReportIfNewlyServerChosen()).isNotNull();

        advertise(true, "http://relay.example:8080/sync", null);

        assertThat(EunomiaSyncSettings.recordRelayHostAndReportIfNewlyServerChosen()).isNull();
    }

    @Test
    void aGenuinelyDifferentHostIsAnnouncedOnItsOwnAccount() {
        advertise(true, "https://relay.example", null);
        EunomiaSyncSettings.recordRelayHostAndReportIfNewlyServerChosen();

        advertise(true, "https://other.example", null);

        assertThat(EunomiaSyncSettings.recordRelayHostAndReportIfNewlyServerChosen())
                .isEqualTo("other.example");
    }

    /** Nothing is leaving the machine, so there is nothing to announce. */
    @Test
    void nothingIsAnnouncedWhileTheRelayIsNotEvenUsable() {
        advertise(null, "https://relay.example", null);

        assertThat(EunomiaSyncSettings.recordRelayHostAndReportIfNewlyServerChosen()).isNull();
        assertThat(config.hasSeenRelayHost("relay.example")).isFalse();
    }

    /**
     * A host the player chose for themselves is recorded but never announced - and, because it was recorded,
     * a server that later advertises the same host does not announce it either. "A relay you have not used
     * before" means exactly that.
     */
    @Test
    void aRelayThePlayerChoseIsRecordedSilentlyAndNeverAnnouncedLater() {
        config.setEnableExternalFallback(true);
        config.setExternalServerAddress("https://relay.example");

        assertThat(EunomiaSyncSettings.recordRelayHostAndReportIfNewlyServerChosen()).isNull();
        assertThat(config.hasSeenRelayHost("relay.example")).isTrue();

        config.setExternalServerAddress(null);
        advertise(true, "https://relay.example", null);

        assertThat(EunomiaSyncSettings.recordRelayHostAndReportIfNewlyServerChosen()).isNull();
    }

    /** The mod pack's own relay is the mod pack's choice, not the server's, so it is recorded quietly too. */
    @Test
    void aModPacksOwnRelayIsNotAnnouncedAsIfTheServerHadChosenIt() {
        EunomiaSyncDefaults.setEnableExternalFallback(true);
        EunomiaSyncDefaults.setExternalServerAddress("https://sync.mymod.example");

        assertThat(EunomiaSyncSettings.recordRelayHostAndReportIfNewlyServerChosen()).isNull();
        assertThat(config.hasSeenRelayHost("sync.mymod.example")).isTrue();
    }

    /**
     * A server that only flips the switch, leaving the address to the rungs below, has still put the player
     * on a relay - and that is the case the 1.0.0 -&gt; 1.1.0 config migration produces, so it has to fire.
     */
    @Test
    void aServerThatOnlyEnablesTheFallbackStillAnnouncesTheHostItLandsOn() {
        advertise(true, null, null);

        assertThat(EunomiaSyncSettings.recordRelayHostAndReportIfNewlyServerChosen())
                .isEqualTo("eunomia.zannagh.me");
    }

    /**
     * The end-to-end interaction the owner asked to be verified: a 1.0.0 player who had recorded
     * {@code false} now inherits, so a server advertising the relay switches Cloud Sync on for them - and
     * that very join tells them where their data is going.
     */
    @Test
    void anEarlyAdopterMigratedFromFalseIsToldWhereTheirDataIsGoingOnTheJoinThatEnablesIt() {
        EunomiaConfig legacy = new Gson().fromJson(
                "{\"enableExternalFallback\":false}", EunomiaConfig.class);
        EunomiaConfig migrated = legacy.ensureSchemaFrom(legacy);
        EunomiaSyncSettings.bindClientConfigSource(() -> migrated);
        advertise(true, "https://relay.example", null);

        assertThat(EunomiaSyncSettings.externalFallbackEnabled()).isTrue();
        assertThat(EunomiaSyncSettings.recordRelayHostAndReportIfNewlyServerChosen())
                .isEqualTo("relay.example");
    }

    // ── Rung 0: a setting the server enforces ──────────────────────────────────────────────────

    /**
     * The defect this whole feature exists to fix: {@code resolve} returned the player's override before the
     * server was ever consulted, so an administrator could not stop relay traffic bypassing their server.
     */
    @Test
    void anEnforcedSettingBeatsEvenThePlayersOwnOverride() {
        config.setEnableExternalFallback(true);
        config.setExternalServerAddress("https://player.example");
        config.setPreferExternalTransport(true);
        enforce(false, "https://server.example", false,
                SyncSetting.EXTERNAL_FALLBACK,
                SyncSetting.EXTERNAL_SERVER_ADDRESS,
                SyncSetting.PREFER_EXTERNAL_TRANSPORT);

        assertThat(EunomiaSyncSettings.externalFallbackEnabled()).isFalse();
        assertThat(EunomiaSyncSettings.externalServerAddress()).isEqualTo("https://server.example");
        assertThat(EunomiaSyncSettings.preferExternalTransport()).isFalse();
    }

    /** Enforcement is per setting: the ones the server did not enforce keep the ordinary four-rung order. */
    @Test
    void enforcementIsScopedToTheSettingsItNames() {
        config.setEnableExternalFallback(true);
        config.setPreferExternalTransport(true);
        enforce(false, null, false, SyncSetting.EXTERNAL_FALLBACK);

        assertThat(EunomiaSyncSettings.externalFallbackEnabled()).isFalse();
        // Not enforced, so the player still wins over the server's "false".
        assertThat(EunomiaSyncSettings.preferExternalTransport()).isTrue();
    }

    /**
     * Enforcement is a modifier on a value, never a value of its own. A server that enforces a setting it has
     * no opinion about has nothing to enforce, so the chain must proceed exactly as before - otherwise the
     * single {@code enforceSettings} flag would lock every setting to whatever the rungs below happened to say.
     */
    @Test
    void enforcingASettingTheServerHasNoOpinionAboutChangesNothing() {
        EunomiaSyncDefaults.setEnableExternalFallback(true);
        config.setPreferExternalTransport(true);
        enforce(null, null, null,
                SyncSetting.EXTERNAL_FALLBACK,
                SyncSetting.EXTERNAL_SERVER_ADDRESS,
                SyncSetting.PREFER_EXTERNAL_TRANSPORT);

        assertThat(EunomiaSyncSettings.externalFallbackEnabled()).isTrue();
        assertThat(EunomiaSyncSettings.preferExternalTransport()).isTrue();
        assertThat(EunomiaSyncSettings.externalServerAddress())
                .isEqualTo(EunomiaDefaults.DEFAULT_EXTERNAL_SERVER_ADDRESS);
        assertThat(EunomiaSyncSettings.lockedByServer()).isEmpty();
    }

    /**
     * An enforced address is still validated. A server with a typo must lose that one value rather than pin
     * every joining player to something nobody can dial - so the chain falls through to the player's override.
     */
    @Test
    void anEnforcedButUnusableAddressLocksNothingAndFallsThrough() {
        config.setExternalServerAddress("https://player.example");
        enforce(true, "javascript:alert(1)", null, SyncSetting.EXTERNAL_SERVER_ADDRESS);

        assertThat(EunomiaSyncSettings.externalServerAddress()).isEqualTo("https://player.example");
        assertThat(EunomiaSyncSettings.isLockedByServer(SyncSetting.EXTERNAL_SERVER_ADDRESS)).isFalse();
    }

    /** A server that advertises without enforcing keeps today's behaviour exactly: advisory, player wins. */
    @Test
    void aNonEnforcedServerOpinionStillLosesToThePlayer() {
        config.setEnableExternalFallback(true);
        advertise(false, "https://server.example", false);

        assertThat(EunomiaSyncSettings.externalFallbackEnabled()).isTrue();
        assertThat(EunomiaSyncSettings.isLockedByServer(SyncSetting.EXTERNAL_FALLBACK)).isFalse();
    }

    /** The full four rungs, unchanged, on a server that enforces nothing - the regression guard for item 5. */
    @Test
    void withoutEnforcementTheOrderIsStillPlayerThenServerThenModThenFramework() {
        EunomiaSyncDefaults.setEnableExternalFallback(true);
        advertise(null, null, null);
        assertThat(EunomiaSyncSettings.externalFallbackEnabled()).isTrue();

        advertise(false, null, null);
        assertThat(EunomiaSyncSettings.externalFallbackEnabled()).isFalse();

        config.setEnableExternalFallback(true);
        assertThat(EunomiaSyncSettings.externalFallbackEnabled()).isTrue();

        EunomiaSyncDefaults.setEnableExternalFallback(null);
        config.setEnableExternalFallback(null);
        advertise(null, null, null);
        assertThat(EunomiaSyncSettings.externalFallbackEnabled())
                .isEqualTo(EunomiaDefaults.DEFAULT_ENABLE_EXTERNAL_FALLBACK);
    }

    // ── The public lock API, which consuming mods grey their own controls on ───────────────────

    /**
     * The contract that matters for a consuming UI: a control reported as locked is exactly a control whose
     * value the resolver is taking from the server. If these two ever disagreed, a mod would grey out a
     * control the player could in fact still change - or leave one enabled that does nothing.
     */
    @Test
    void theLockApiAgreesWithWhatTheResolverActuallyOverrides() {
        config.setEnableExternalFallback(true);
        enforce(false, "https://server.example", null, SyncSetting.EXTERNAL_FALLBACK);

        assertThat(EunomiaSyncSettings.isLockedByServer(SyncSetting.EXTERNAL_FALLBACK)).isTrue();
        assertThat(EunomiaSyncSettings.externalFallbackEnabled()).isFalse();

        // Advertised but not enforced: a value, not a lock.
        assertThat(EunomiaSyncSettings.isLockedByServer(SyncSetting.EXTERNAL_SERVER_ADDRESS)).isFalse();
        assertThat(EunomiaSyncSettings.lockedByServer())
                .containsExactly(SyncSetting.EXTERNAL_FALLBACK);
    }

    @Test
    void nothingIsLockedOnAServerThatAdvertisesNoPolicyAtAll() {
        advertise(null, null, null);

        assertThat(EunomiaSyncSettings.lockedByServer()).isEmpty();
        assertThat(EunomiaSyncSettings.isLockedByServer(null)).isFalse();
    }

    /** Fail-soft: this is called while a screen is being built, so a dead capability view must not throw. */
    @Test
    void theLockApiDegradesToUnlockedInsteadOfThrowingIntoAUiThread() {
        EunomiaSyncSettings.bindAdvertisedPolicySource(() -> {
            throw new IllegalStateException("no connection");
        });

        assertThat(EunomiaSyncSettings.isLockedByServer(SyncSetting.EXTERNAL_FALLBACK)).isFalse();
        assertThat(EunomiaSyncSettings.lockedByServer()).isEmpty();
    }

    /** End to end through the real server config: one operator flag, three locked settings. */
    @Test
    void anEnforcingServerConfigLocksEveryValueItActuallyStates() {
        config.setEnableExternalFallback(true);
        EunomiaServerConfig enforcing =
                new EunomiaServerConfig(false, "https://relay.example", true, true);
        EunomiaSyncSettings.bindAdvertisedPolicySource(enforcing::toSyncPolicy);

        assertThat(EunomiaSyncSettings.externalFallbackEnabled()).isFalse();
        assertThat(EunomiaSyncSettings.externalServerAddress()).isEqualTo("https://relay.example");
        assertThat(EunomiaSyncSettings.preferExternalTransport()).isTrue();
        assertThat(EunomiaSyncSettings.lockedByServer()).containsExactlyInAnyOrder(
                SyncSetting.EXTERNAL_FALLBACK,
                SyncSetting.EXTERNAL_SERVER_ADDRESS,
                SyncSetting.PREFER_EXTERNAL_TRANSPORT);
    }

    // ── Degradation ─────────────────────────────────────────────────────────────────────────────

    @Test
    void aThrowingSourceDegradesToTheLowerRungsInsteadOfPropagating() {
        EunomiaSyncSettings.bindClientConfigSource(() -> {
            throw new IllegalStateException("provider not ready");
        });
        EunomiaSyncSettings.bindAdvertisedPolicySource(() -> {
            throw new IllegalStateException("no connection");
        });

        assertThat(EunomiaSyncSettings.externalFallbackEnabled()).isFalse();
        assertThat(EunomiaSyncSettings.externalServerAddress())
                .isEqualTo(EunomiaDefaults.DEFAULT_EXTERNAL_SERVER_ADDRESS);
    }

    // ── Item 4: announcing a server's missing sync only once ────────────────────────────────────

    @Test
    void aServerWithoutSyncIsAnnouncedOnceAndThenNeverAgain() {
        assertThat(EunomiaSyncSettings.recordSyncUnavailableAnnouncement("play.example:25565")).isTrue();

        // The situation is permanent for a server that does not run eunomia, so without the record this
        // would be raised on every join for the lifetime of the install.
        assertThat(EunomiaSyncSettings.recordSyncUnavailableAnnouncement("play.example:25565")).isFalse();
        assertThat(config.hasAnnouncedSyncUnavailable("play.example:25565")).isTrue();
    }

    @Test
    void aDifferentServerIsAnnouncedOnItsOwnAccount() {
        EunomiaSyncSettings.recordSyncUnavailableAnnouncement("play.example:25565");

        assertThat(EunomiaSyncSettings.recordSyncUnavailableAnnouncement("other.example:25565")).isTrue();
    }

    /** Case is not identity for a server address the player typed into their server list. */
    @Test
    void theSameServerInADifferentCaseIsNotAnnouncedAgain() {
        EunomiaSyncSettings.recordSyncUnavailableAnnouncement("play.example:25565");

        assertThat(EunomiaSyncSettings.recordSyncUnavailableAnnouncement("PLAY.Example:25565")).isFalse();
    }

    /** No identifiable server means no way to announce it only once, so it is not announced at all. */
    @Test
    void anUnidentifiableServerIsNotAnnounced() {
        assertThat(EunomiaSyncSettings.recordSyncUnavailableAnnouncement(null)).isFalse();
        assertThat(EunomiaSyncSettings.recordSyncUnavailableAnnouncement("  ")).isFalse();
    }

    @Test
    void withNoConfigProviderBoundThereIsNowhereToRecordAndNothingIsAnnounced() {
        EunomiaSyncSettings.bindClientConfigSource(null);

        assertThat(EunomiaSyncSettings.recordSyncUnavailableAnnouncement("play.example:25565")).isFalse();
    }

    @Test
    void clearingTheRecordLetsAServerBeAnnouncedOnceMore() {
        EunomiaSyncSettings.recordSyncUnavailableAnnouncement("play.example:25565");

        config.forgetAnnouncedSyncUnavailableServers();

        assertThat(EunomiaSyncSettings.recordSyncUnavailableAnnouncement("play.example:25565")).isTrue();
    }
}
