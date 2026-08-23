package de.zannagh.eunomia.configuration;

import com.google.gson.Gson;
import de.zannagh.eunomia.networking.handshake.ServerSyncPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EunomiaServerConfigTest {

    private static final Gson GSON = new Gson();

    @Test
    void defaultsAreFallbackOffAtTheFrameworkAddress() {
        EunomiaServerConfig config = new EunomiaServerConfig();

        assertThat(config.externalFallbackEnabled()).isFalse();
        assertThat(config.preferExternalTransport()).isFalse();
        assertThat(config.externalServerAddress())
                .isEqualTo(EunomiaDefaults.DEFAULT_EXTERNAL_SERVER_ADDRESS);
        assertThat(config.hasExternalServerAddress()).isTrue();
    }

    @Test
    void anEmptyDocumentDeserializesOntoTheDefaults() {
        EunomiaServerConfig config = GSON.fromJson("{}", EunomiaServerConfig.class);

        assertThat(config.externalFallbackEnabled()).isFalse();
        assertThat(config.externalServerAddress())
                .isEqualTo(EunomiaDefaults.DEFAULT_EXTERNAL_SERVER_ADDRESS);
    }

    @Test
    void anUntouchedServerAdvertisesNoOpinionAtAllSoTheModDefaultsSurvive() {
        // The whole point of the opinion shape: an operator who never opened eunomia-server.json must not
        // erase rung three (the consuming mod's defaults) for every client that joins.
        assertThat(new EunomiaServerConfig().toSyncPolicy().isEmpty()).isTrue();
        assertThat(EunomiaServerConfig.untouched().toSyncPolicy()).isEqualTo(ServerSyncPolicy.UNKNOWN);
        assertThat(GSON.fromJson("{}", EunomiaServerConfig.class).toSyncPolicy().isEmpty()).isTrue();
    }

    @Test
    void aConfiguredServerStillAdvertisesEvenWhenItPicksTheFrameworkDefaults() {
        // Going through the operator-facing constructor - which is what an admin write does - is a decision,
        // so "off" set deliberately is advertised as an opinion and outranks a mod that wants it on.
        ServerSyncPolicy policy = new EunomiaServerConfig(false, "", false).toSyncPolicy();

        assertThat(policy.isEmpty()).isFalse();
        assertThat(policy.enableExternalFallback()).isFalse();
        assertThat(policy.preferExternalTransport()).isFalse();
        assertThat(policy.externalServerAddress()).isNull();
    }

    @Test
    void aHandEditedFileThatMentionsOneKeyAdvertisesOnlyThatKey() {
        EunomiaServerConfig config = GSON.fromJson(
                "{\"schemaVersion\":\"1.1.0\",\"enableExternalFallback\":true}", EunomiaServerConfig.class);

        ServerSyncPolicy policy = config.toSyncPolicy();

        assertThat(policy.enableExternalFallback()).isTrue();
        assertThat(policy.externalServerAddress()).isNull();
        assertThat(policy.preferExternalTransport()).isNull();
    }

    @Test
    void aLegacyDocumentThatMerelyRepeatsTheDefaultsMigratesToNoOpinion() {
        EunomiaServerConfig loaded = GSON.fromJson(
                "{\"enableExternalFallback\":false,\"externalServerAddress\":\""
                        + EunomiaDefaults.DEFAULT_EXTERNAL_SERVER_ADDRESS
                        + "\",\"preferExternalTransport\":false}", EunomiaServerConfig.class);

        assertThat(loaded.shouldMigrate()).isTrue();
        EunomiaServerConfig migrated = loaded.ensureSchemaFrom(loaded);

        assertThat(migrated.toSyncPolicy().isEmpty()).isTrue();
        assertThat(migrated.getSchemaVersion()).isEqualTo(EunomiaServerConfig.SCHEMA_1_1_0);
        assertThat(migrated.hasChangedFromSerializedContent()).isTrue();
    }

    @Test
    void aLegacyDocumentThatDiffersFromTheDefaultsKeepsThoseValuesAsOpinions() {
        EunomiaServerConfig loaded = GSON.fromJson(
                "{\"enableExternalFallback\":true,\"externalServerAddress\":\"https://relay.example\"}",
                EunomiaServerConfig.class);

        ServerSyncPolicy policy = loaded.ensureSchemaFrom(loaded).toSyncPolicy();

        assertThat(policy.enableExternalFallback()).isTrue();
        assertThat(policy.externalServerAddress()).isEqualTo("https://relay.example");
        // preferExternalTransport had no 1.0.0 counterpart, so it stays "no opinion".
        assertThat(policy.preferExternalTransport()).isNull();
    }

    @Test
    void toSyncPolicyAdvertisesEveryValue() {
        EunomiaServerConfig config = new EunomiaServerConfig(true, "https://relay.example", true);

        ServerSyncPolicy policy = config.toSyncPolicy();

        assertThat(policy.enableExternalFallback()).isTrue();
        assertThat(policy.externalServerAddress()).isEqualTo("https://relay.example");
        assertThat(policy.preferExternalTransport()).isTrue();
    }

    @Test
    void toSyncPolicyDropsABlankAddressRatherThanAdvertisingAnEmptyOne() {
        EunomiaServerConfig config = new EunomiaServerConfig(true, "  ", false);

        assertThat(config.toSyncPolicy().externalServerAddress()).isNull();
        assertThat(config.hasExternalServerAddress()).isFalse();
    }

    @Test
    void roundTripsThroughGson() {
        EunomiaServerConfig original = new EunomiaServerConfig(true, "https://relay.example", true);

        EunomiaServerConfig restored = GSON.fromJson(GSON.toJson(original), EunomiaServerConfig.class);

        assertThat(restored.externalFallbackEnabled()).isTrue();
        assertThat(restored.preferExternalTransport()).isTrue();
        assertThat(restored.externalServerAddress()).isEqualTo("https://relay.example");
        assertThat(restored.shouldMigrate()).isFalse();
    }

    @Test
    void setValueCopiesEveryField() {
        EunomiaServerConfig target = new EunomiaServerConfig();

        target.setValue(new EunomiaServerConfig(true, "https://other.example", true));

        assertThat(target.externalFallbackEnabled()).isTrue();
        assertThat(target.preferExternalTransport()).isTrue();
        assertThat(target.externalServerAddress()).isEqualTo("https://other.example");
    }

    @Test
    void anAlreadyCurrentDocumentIsNotMigratedAgain() {
        EunomiaServerConfig config = new EunomiaServerConfig(true, "a", false);

        assertThat(config.shouldMigrate()).isFalse();
        assertThat(config.ensureSchemaFrom(config)).isSameAs(config);
    }
}
