package de.zannagh.eunomia.configuration;

import com.google.gson.Gson;
import de.zannagh.eunomia.common.SemanticVersion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EunomiaConfigTest {

    private static final Gson GSON = new Gson();

    @Test
    void defaultConstructorOptsFallbackOutAndLeavesAddressBlank() {
        EunomiaConfig config = new EunomiaConfig();

        assertThat(config.enableExternalFallback).isFalse();
        assertThat(config.externalFallbackEnabled()).isFalse();
        assertThat(config.externalServerAddress).isEmpty();
        assertThat(config.externalServerAddress()).isEmpty();
        assertThat(config.hasExternalServerAddress()).isFalse();
    }

    @Test
    void parameterizedConstructorStoresBothKnobs() {
        EunomiaConfig config = new EunomiaConfig(true, "relay.example:25566");

        assertThat(config.enableExternalFallback).isTrue();
        assertThat(config.externalFallbackEnabled()).isTrue();
        assertThat(config.externalServerAddress()).isEqualTo("relay.example:25566");
        assertThat(config.hasExternalServerAddress()).isTrue();
    }

    @Test
    void hasExternalServerAddressIsFalseForNull() {
        EunomiaConfig config = new EunomiaConfig(true, null);

        assertThat(config.hasExternalServerAddress()).isFalse();
    }

    @Test
    void hasExternalServerAddressIsFalseForEmptyString() {
        EunomiaConfig config = new EunomiaConfig(false, "");

        assertThat(config.hasExternalServerAddress()).isFalse();
    }

    @Test
    void hasExternalServerAddressIsFalseForBlankWhitespaceOnly() {
        EunomiaConfig config = new EunomiaConfig(false, "   \t\n");

        assertThat(config.hasExternalServerAddress()).isFalse();
    }

    @Test
    void hasExternalServerAddressIsTrueWhenAddressHasContent() {
        EunomiaConfig config = new EunomiaConfig(false, "  host  ");

        // Leading/trailing whitespace does not make a non-blank string blank.
        assertThat(config.hasExternalServerAddress()).isTrue();
    }

    @Test
    void getValueReturnsSameInstance() {
        EunomiaConfig config = new EunomiaConfig(true, "addr");

        assertThat(config.getValue()).isSameAs(config);
    }

    @Test
    void setValueCopiesFieldsFromOtherInstanceWithoutIdentity() {
        EunomiaConfig target = new EunomiaConfig(false, "old");
        EunomiaConfig source = new EunomiaConfig(true, "new-relay");

        target.setValue(source);

        assertThat(target).isNotSameAs(source);
        assertThat(target.enableExternalFallback).isTrue();
        assertThat(target.externalServerAddress()).isEqualTo("new-relay");
    }

    @Test
    void setValuePropagatesNullAddress() {
        EunomiaConfig target = new EunomiaConfig(true, "old");

        target.setValue(new EunomiaConfig(false, null));

        assertThat(target.enableExternalFallback).isFalse();
        assertThat(target.externalServerAddress()).isNull();
        assertThat(target.hasExternalServerAddress()).isFalse();
    }

    @Test
    void getDefaultValueReturnsFreshDefaultInstance() {
        EunomiaConfig config = new EunomiaConfig(true, "addr");

        EunomiaConfig defaults = config.getDefaultValue();

        assertThat(defaults).isNotSameAs(config);
        assertThat(defaults.enableExternalFallback).isFalse();
        assertThat(defaults.externalServerAddress()).isEmpty();
    }

    @Test
    void changedFlagStartsFalseAndFlipsOnceMarked() {
        EunomiaConfig config = new EunomiaConfig();

        assertThat(config.hasChangedFromSerializedContent()).isFalse();

        config.setHasChangedFromSerializedContent();

        assertThat(config.hasChangedFromSerializedContent()).isTrue();
    }

    @Test
    void markingChangedIsIdempotent() {
        EunomiaConfig config = new EunomiaConfig();

        config.setHasChangedFromSerializedContent();
        config.setHasChangedFromSerializedContent();

        assertThat(config.hasChangedFromSerializedContent()).isTrue();
    }

    @Test
    void schemaVersionsAreOneZeroZeroAndEqual() {
        EunomiaConfig config = new EunomiaConfig();

        SemanticVersion expected = new SemanticVersion(1, 0, 0, null);
        assertThat(config.getSchemaVersion()).isEqualTo(expected);
        assertThat(config.getCurrentSchemaVersion()).isEqualTo(expected);
        assertThat(config.getSchemaVersion()).isEqualTo(config.getCurrentSchemaVersion());
    }

    @Test
    void shouldMigrateIsFalseBecauseSchemaMatchesCurrent() {
        EunomiaConfig config = new EunomiaConfig();

        assertThat(config.shouldMigrate()).isFalse();
    }

    @Test
    void migrateFromReturnsSuppliedInstanceUnchanged() {
        EunomiaConfig config = new EunomiaConfig();
        EunomiaConfig old = new EunomiaConfig(true, "legacy");

        EunomiaConfig migrated = config.migrateFrom(old);

        assertThat(migrated).isSameAs(old);
        assertThat(migrated.enableExternalFallback).isTrue();
        assertThat(migrated.externalServerAddress()).isEqualTo("legacy");
    }

    @Test
    void ensureSchemaFromReturnsSameInstanceAndLeavesChangedFlagUntouched() {
        EunomiaConfig config = new EunomiaConfig(true, "addr");

        EunomiaConfig result = config.ensureSchemaFrom(config);

        // No migration needed (schema == current), so the same instance comes back untouched.
        assertThat(result).isSameAs(config);
        assertThat(result.hasChangedFromSerializedContent()).isFalse();
    }

    @Test
    void gsonRoundTripPreservesBothKnobs() {
        EunomiaConfig original = new EunomiaConfig(true, "relay:25565");

        String json = GSON.toJson(original);
        EunomiaConfig restored = GSON.fromJson(json, EunomiaConfig.class);

        assertThat(restored.enableExternalFallback).isTrue();
        assertThat(restored.externalServerAddress()).isEqualTo("relay:25565");
        assertThat(restored.hasExternalServerAddress()).isTrue();
    }

    @Test
    void gsonSerializationOmitsTransientChangedFlag() {
        EunomiaConfig config = new EunomiaConfig(true, "addr");
        config.setHasChangedFromSerializedContent();

        String json = GSON.toJson(config);

        assertThat(json).doesNotContain("changed");
        assertThat(json).contains("enableExternalFallback");
        assertThat(json).contains("externalServerAddress");
    }

    @Test
    void gsonDeserializationLeavesChangedFlagAtDefaultFalse() {
        EunomiaConfig original = new EunomiaConfig(true, "addr");
        original.setHasChangedFromSerializedContent();

        EunomiaConfig restored = GSON.fromJson(GSON.toJson(original), EunomiaConfig.class);

        // The transient flag is not carried across the wire; a freshly parsed config is "unchanged".
        assertThat(restored.hasChangedFromSerializedContent()).isFalse();
    }

    @Test
    void gsonDeserializesEmptyObjectToDefaults() {
        EunomiaConfig restored = GSON.fromJson("{}", EunomiaConfig.class);

        assertThat(restored.enableExternalFallback).isFalse();
        assertThat(restored.externalServerAddress()).isEmpty();
        assertThat(restored.hasExternalServerAddress()).isFalse();
        assertThat(restored.hasChangedFromSerializedContent()).isFalse();
    }

    @Test
    void gsonDeserializesPartialObjectFillingMissingFieldWithDefault() {
        EunomiaConfig restored = GSON.fromJson("{\"enableExternalFallback\":true}", EunomiaConfig.class);

        assertThat(restored.enableExternalFallback).isTrue();
        // externalServerAddress absent from JSON -> Gson leaves the field-initializer default ("").
        assertThat(restored.externalServerAddress()).isEmpty();
        assertThat(restored.hasExternalServerAddress()).isFalse();
    }
}
