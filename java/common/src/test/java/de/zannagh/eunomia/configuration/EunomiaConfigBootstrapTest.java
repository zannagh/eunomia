package de.zannagh.eunomia.configuration;

import de.zannagh.eunomia.Eunomia;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression cover for the bootstrap bug: {@code CONFIG_PROVIDER} used to be assigned only inside a private
 * constructor that nothing ever called, so {@code Eunomia.getConfig()} threw a NullPointerException and the
 * config file was never read or written.
 */
class EunomiaConfigBootstrapTest {

    @TempDir
    Path configDirectory;

    @BeforeEach
    void setUp() {
        Eunomia.resetConfigurationForTesting();
    }

    @AfterEach
    void tearDown() {
        Eunomia.resetConfigurationForTesting();
    }

    @Test
    void configIsAvailableAfterBootstrapAndDefaultsWhenTheFileIsMissing() {
        Eunomia.initConfiguration(configDirectory);

        assertThat(Eunomia.CONFIG_PROVIDER).isNotNull();
        assertThat(Eunomia.getConfig()).isNotNull();
        assertThat(Eunomia.getConfig().enableExternalFallbackOverride()).isNull();
        assertThat(Eunomia.getConfig().externalServerAddressOverride()).isNull();
        assertThat(Eunomia.getConfig().preferExternalTransportOverride()).isNull();
    }

    @Test
    void serverConfigIsAvailableAfterBootstrapAndDefaultsWhenTheFileIsMissing() {
        Eunomia.initConfiguration(configDirectory);

        assertThat(Eunomia.SERVER_CONFIG_PROVIDER).isNotNull();
        assertThat(Eunomia.getServerConfig()).isNotNull();
        assertThat(Eunomia.getServerConfig().externalFallbackEnabled()).isFalse();
        assertThat(Eunomia.getServerConfig().preferExternalTransport()).isFalse();
        assertThat(Eunomia.getServerConfig().externalServerAddress())
                .isEqualTo(EunomiaDefaults.DEFAULT_EXTERNAL_SERVER_ADDRESS);
    }

    @Test
    void bootstrapWritesBothFilesSoTheyAreDiscoverable() {
        Eunomia.initConfiguration(configDirectory);

        assertThat(Files.exists(configDirectory.resolve("eunomia-client.json"))).isTrue();
        assertThat(Files.exists(configDirectory.resolve("eunomia-server.json"))).isTrue();
    }

    @Test
    void bootstrapIsIdempotentSoASecondLoaderEntryPointDoesNotDiscardInMemoryState() {
        Eunomia.initConfiguration(configDirectory);
        var provider = Eunomia.CONFIG_PROVIDER;
        Eunomia.getConfig().setExternalServerAddress("https://edited.example");

        Eunomia.initConfiguration(configDirectory);

        assertThat(Eunomia.CONFIG_PROVIDER).isSameAs(provider);
        assertThat(Eunomia.getConfig().externalServerAddressOverride()).isEqualTo("https://edited.example");
    }

    @Test
    void bootstrapLoadsAnExistingClientDocumentAndMigratesIt() throws Exception {
        Files.writeString(configDirectory.resolve("eunomia-client.json"),
                "{\"enableExternalFallback\":true,\"externalServerAddress\":\"https://legacy.example\"}");

        Eunomia.initConfiguration(configDirectory);

        assertThat(Eunomia.getConfig().enableExternalFallbackOverride()).isTrue();
        assertThat(Eunomia.getConfig().externalServerAddressOverride()).isEqualTo("https://legacy.example");
        // The migrated shape is written straight back, so the marker survives a restart.
        assertThat(Files.readString(configDirectory.resolve("eunomia-client.json"))).contains("1.1.0");
    }

    @Test
    void bootstrapBindsTheResolverToThePlayerOverrides() {
        Eunomia.initConfiguration(configDirectory);
        Eunomia.getConfig().setExternalServerAddress("https://player.example");

        assertThat(EunomiaSyncSettings.externalServerAddress()).isEqualTo("https://player.example");
    }
}
