package de.zannagh.eunomia.common;

import de.zannagh.eunomia.Eunomia;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the {@link PackRepositoryProvider} contract and the registration bookkeeping in
 * {@link Eunomia#registerPackRespositoryProvider(PackRepositoryProvider)} /
 * {@link Eunomia#getPackRepositoryProviders()} - registration is a de-duplicating set keyed on provider
 * equality. A record fixture supplies value-based equality so the set semantics are observable.
 */
class PackRepositoryProviderTest {

    /** Value-based fixture; record components carry the interface's accessor names verbatim. */
    private record FakeProvider(
            String getModId,
            String getAssetDirectoryName,
            String getModName,
            String getModResourceName) implements PackRepositoryProvider {
    }

    private static FakeProvider unique() {
        String id = UUID.randomUUID().toString();
        return new FakeProvider(id, "assets", "Mod " + id, "Mod " + id + " Resources");
    }

    @Test
    void implementationExposesTheMetadataItWasBuiltWith() {
        var provider = new FakeProvider("mymod", "assets", "My Mod", "My Mod Resources");

        assertThat(provider.getModId()).isEqualTo("mymod");
        assertThat(provider.getAssetDirectoryName()).isEqualTo("assets");
        assertThat(provider.getModName()).isEqualTo("My Mod");
        assertThat(provider.getModResourceName()).isEqualTo("My Mod Resources");
    }

    @Test
    void registeredProviderBecomesRetrievable() {
        var provider = unique();

        Eunomia.registerPackRespositoryProvider(provider);

        assertThat(Eunomia.getPackRepositoryProviders()).contains(provider);
    }

    @Test
    void registeringEqualProvidersTwiceStoresOnlyOne() {
        var first = unique();
        // A distinct instance with identical component values - equal under record equality.
        var duplicate = new FakeProvider(
                first.getModId(), first.getAssetDirectoryName(), first.getModName(), first.getModResourceName());
        assertThat(first).isEqualTo(duplicate);

        int before = Eunomia.getPackRepositoryProviders().size();
        Eunomia.registerPackRespositoryProvider(first);
        int afterFirst = Eunomia.getPackRepositoryProviders().size();
        Eunomia.registerPackRespositoryProvider(duplicate);
        int afterDuplicate = Eunomia.getPackRepositoryProviders().size();

        assertThat(afterFirst).isEqualTo(before + 1);
        // The equal duplicate must not grow the set.
        assertThat(afterDuplicate).isEqualTo(afterFirst);
    }

    @Test
    void distinctProvidersAreAllRetained() {
        var a = unique();
        var b = unique();
        assertThat(a).isNotEqualTo(b);

        int before = Eunomia.getPackRepositoryProviders().size();
        Eunomia.registerPackRespositoryProvider(a);
        Eunomia.registerPackRespositoryProvider(b);

        var providers = Eunomia.getPackRepositoryProviders();
        assertThat(providers).contains(a, b);
        assertThat(providers.size()).isEqualTo(before + 2);
    }
}
