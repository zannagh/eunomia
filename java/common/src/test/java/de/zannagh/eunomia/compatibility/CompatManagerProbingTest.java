package de.zannagh.eunomia.compatibility;

import de.zannagh.eunomia.compatibility.CompatTestFixtures.FakeClassLoader;
import de.zannagh.eunomia.compatibility.CompatTestFixtures.TestFlag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static de.zannagh.eunomia.compatibility.CompatTestFixtures.flag;
import static de.zannagh.eunomia.compatibility.CompatTestFixtures.reset;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link CompatManager}'s presence-probing surface: the class-load-free resource probe
 * ({@link CompatManager#isModPresent} / {@link CompatManager#setCompatFlagByResourceProbing}), the
 * {@code Class.forName} gap-fill ({@link CompatManager#classExists} / {@link CompatManager#setCompatFlag}),
 * and the {@link CompatManager#resourceProbingGaps} consistency check.
 */
class CompatManagerProbingTest {

    @BeforeEach
    void setUp() {
        reset();
    }

    @AfterEach
    void tearDown() {
        reset();
    }

    @Test
    void isModPresentFindsExactClassResource() {
        FakeClassLoader cl = new FakeClassLoader(Set.of("a/b/C.class"));

        assertThat(CompatManager.isModPresent(cl, "a.b.C")).isTrue();
    }

    @Test
    void isModPresentFallsBackToPackageDirectoryForRenamedEntrypoint() {
        // The exact class resource is gone, but the package directory is still exposed.
        FakeClassLoader cl = new FakeClassLoader(Set.of("a/b/"));

        assertThat(CompatManager.isModPresent(cl, "a.b.RenamedEntry")).isTrue();
    }

    @Test
    void isModPresentReturnsFalseWhenNeitherClassNorPackageIsPresent() {
        FakeClassLoader cl = new FakeClassLoader(Set.of());

        assertThat(CompatManager.isModPresent(cl, "a.b.Missing")).isFalse();
    }

    @Test
    void isModPresentReturnsFalseForClassInTheDefaultPackage() {
        // No dot means there is no package directory to fall back to.
        FakeClassLoader cl = new FakeClassLoader(Set.of());

        assertThat(CompatManager.isModPresent(cl, "NoPackageClass")).isFalse();
    }

    @Test
    void classExistsIsTrueForALoadableClassAndFalseForAnUnknownOne() {
        assertThat(CompatManager.classExists("java.lang.String")).isTrue();
        assertThat(CompatManager.classExists("de.zannagh.eunomia.does.NotExist")).isFalse();
    }

    @Test
    void classExistsFallsBackToResourceProbeWhenTheClassCannotBeLoaded() {
        // Class.forName cannot load these through the fake loader, so the resource probe decides.
        FakeClassLoader cl = new FakeClassLoader(Set.of("mod/x/Entry.class"));

        assertThat(CompatManager.classExists("mod.x.Entry", cl)).isTrue();
        assertThat(CompatManager.classExists("mod.y.Absent", cl)).isFalse();
    }

    @Test
    void resourceProbingFlagsPresentModsAndTracksThemSeparately() {
        TestFlag present = flag("mod.p.Entry", false);
        TestFlag absent = flag("mod.a.Entry", false);
        CompatManager.registerCompatFlag(present);
        CompatManager.registerCompatFlag(absent);
        FakeClassLoader cl = new FakeClassLoader(Set.of("mod/p/Entry.class"));

        CompatManager.setCompatFlagByResourceProbing(cl);

        assertThat(CompatManager.isPresent(present)).isTrue();
        assertThat(CompatManager.isPresent(absent)).isFalse();
        assertThat(CompatManager.RESOURCE_PROBED_FLAGS).contains(present).doesNotContain(absent);
    }

    @Test
    void setCompatFlagGapFillsLoadableFlagsAndIsIdempotent() {
        TestFlag loadable = flag("java.util.ArrayList", false);
        CompatManager.registerCompatFlag(loadable);

        CompatManager.setCompatFlag(getClass().getClassLoader());
        assertThat(CompatManager.isPresent(loadable)).isTrue();

        // A flag registered after the ensure latch closes must NOT be picked up by a second pass.
        TestFlag late = flag("java.util.LinkedList", false);
        CompatManager.registerCompatFlag(late);
        CompatManager.setCompatFlag(getClass().getClassLoader());
        assertThat(CompatManager.isPresent(late)).isFalse();
    }

    @Test
    void resourceProbingGapsReportsFlagsThatLoadedButWereNeverResourceProbed() {
        TestFlag loadedButUnprobed = flag("java.util.ArrayList", false);
        TestFlag loadedAndProbed = flag("java.util.LinkedList", false);
        TestFlag notLoadable = flag("no.such.Class", false);
        CompatManager.registerCompatFlag(loadedButUnprobed);
        CompatManager.registerCompatFlag(loadedAndProbed);
        CompatManager.registerCompatFlag(notLoadable);
        CompatManager.RESOURCE_PROBED_FLAGS.add(loadedAndProbed);

        var gaps = CompatManager.resourceProbingGaps(getClass().getClassLoader());

        assertThat(gaps)
                .contains(loadedButUnprobed)
                .doesNotContain(loadedAndProbed)
                .doesNotContain(notLoadable);
    }

    @Test
    void resourceProbingGapsIsEmptyWhenEverythingLoadableWasProbed() {
        TestFlag loadable = flag("java.util.ArrayList", false);
        CompatManager.registerCompatFlag(loadable);
        CompatManager.RESOURCE_PROBED_FLAGS.add(loadable);

        assertThat(CompatManager.resourceProbingGaps(getClass().getClassLoader())).isEmpty();
    }
}
