package de.zannagh.eunomia.configuration;

import com.google.gson.Gson;
import de.zannagh.eunomia.common.SemanticVersion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The 1.0.0 -> 1.1.0 migration, exercised the way {@code FileConfigurationProvider} exercises it: parse a
 * document written by the old build, then {@code ensureSchemaFrom} it. The old shape had two primitive knobs and
 * no schema marker at all, which is precisely why an absent marker has to read as 1.0.0.
 */
class EunomiaConfigMigrationTest {

    private static final Gson GSON = new Gson();

    private static final String LEGACY_DOCUMENT =
            "{\"enableExternalFallback\":true,\"externalServerAddress\":\"https://relay.example\"}";

    @Test
    void legacyDocumentIsDetectedAsNeedingMigration() {
        EunomiaConfig loaded = GSON.fromJson(LEGACY_DOCUMENT, EunomiaConfig.class);

        assertThat(loaded.getSchemaVersion()).isEqualTo(EunomiaConfig.SCHEMA_1_0_0);
        assertThat(loaded.shouldMigrate()).isTrue();
    }

    @Test
    void migrationPreservesBothLegacyValuesAsExplicitOverrides() {
        EunomiaConfig loaded = GSON.fromJson(LEGACY_DOCUMENT, EunomiaConfig.class);

        EunomiaConfig migrated = loaded.ensureSchemaFrom(loaded);

        assertThat(migrated.enableExternalFallbackOverride()).isTrue();
        assertThat(migrated.externalServerAddressOverride()).isEqualTo("https://relay.example");
        assertThat(migrated.preferExternalTransportOverride()).isNull();
    }

    /**
     * The owner's call, and the reversal of what this migration used to do. {@code false} was the 1.0.0
     * <em>default</em>, written into every config file the moment it was created, so it cannot be told apart
     * from a player who never opened the settings screen - and reading it as a decision pinned every early
     * adopter below their mod pack's and their server's defaults forever.
     *
     * <p>The cost is stated plainly in {@code EunomiaConfig#migrateFrom}: this can result in Cloud Sync
     * switching on for someone whose file said {@code false}. The mitigation is the new-relay-host
     * notification, which is exercised in {@code EunomiaSyncSettingsTest}.</p>
     */
    @Test
    void migrationTurnsARecordedFalseIntoInheritBecause10DidNotRecordIntent() {
        EunomiaConfig loaded = GSON.fromJson(
                "{\"enableExternalFallback\":false,\"externalServerAddress\":\"a\"}", EunomiaConfig.class);

        EunomiaConfig migrated = loaded.ensureSchemaFrom(loaded);

        assertThat(migrated.enableExternalFallbackOverride()).isNull();
        // The address was not a default, so it does survive as a real override.
        assertThat(migrated.externalServerAddressOverride()).isEqualTo("a");
    }

    /** A recorded {@code true} is unambiguous - nothing in 1.0.0 set it by itself - so it stays a decision. */
    @Test
    void migrationStillPreservesARecordedTrueAsADecision() {
        EunomiaConfig loaded = GSON.fromJson("{\"enableExternalFallback\":true}", EunomiaConfig.class);

        assertThat(loaded.ensureSchemaFrom(loaded).enableExternalFallbackOverride()).isTrue();
    }

    /** An absent key was already "inherit" and stays so; migration must not invent a {@code false}. */
    @Test
    void migrationLeavesAnAbsentFallbackKeyAsInherit() {
        EunomiaConfig loaded = GSON.fromJson("{\"externalServerAddress\":\"a\"}", EunomiaConfig.class);

        assertThat(loaded.ensureSchemaFrom(loaded).enableExternalFallbackOverride()).isNull();
    }

    @Test
    void migrationCollapsesTheBlankLegacyAddressToInherit() {
        // "" was the 1.0.0 default and never meant anything; carrying it over would pin an unusable address.
        EunomiaConfig loaded = GSON.fromJson(
                "{\"enableExternalFallback\":false,\"externalServerAddress\":\"\"}", EunomiaConfig.class);

        EunomiaConfig migrated = loaded.ensureSchemaFrom(loaded);

        assertThat(migrated.externalServerAddressOverride()).isNull();
        assertThat(migrated.hasExternalServerAddress()).isFalse();
    }

    @Test
    void migrationStampsTheCurrentSchemaAndFlagsTheConfigForRewrite() {
        EunomiaConfig loaded = GSON.fromJson(LEGACY_DOCUMENT, EunomiaConfig.class);

        EunomiaConfig migrated = loaded.ensureSchemaFrom(loaded);

        assertThat(migrated.getSchemaVersion()).isEqualTo(new SemanticVersion(1, 1, 0, null));
        assertThat(migrated.shouldMigrate()).isFalse();
        // ensureSchemaFrom marks the result changed, which is what makes the provider persist the new shape.
        assertThat(migrated.hasChangedFromSerializedContent()).isTrue();
    }

    @Test
    void migrationIsIdempotentAndAnAlreadyCurrentDocumentIsUntouched() {
        EunomiaConfig loaded = GSON.fromJson(LEGACY_DOCUMENT, EunomiaConfig.class);
        EunomiaConfig once = loaded.ensureSchemaFrom(loaded);

        EunomiaConfig twice = once.ensureSchemaFrom(once);

        assertThat(twice).isSameAs(once);
        assertThat(twice.externalServerAddressOverride()).isEqualTo("https://relay.example");
    }

    @Test
    void migratedDocumentSurvivesARoundTripThroughDisk() {
        EunomiaConfig loaded = GSON.fromJson(LEGACY_DOCUMENT, EunomiaConfig.class);
        EunomiaConfig migrated = loaded.ensureSchemaFrom(loaded);

        EunomiaConfig reloaded = GSON.fromJson(GSON.toJson(migrated), EunomiaConfig.class);

        assertThat(reloaded.shouldMigrate()).isFalse();
        assertThat(reloaded.enableExternalFallbackOverride()).isTrue();
        assertThat(reloaded.externalServerAddressOverride()).isEqualTo("https://relay.example");
    }
}
