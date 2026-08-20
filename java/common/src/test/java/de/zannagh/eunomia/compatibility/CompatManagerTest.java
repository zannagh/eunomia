package de.zannagh.eunomia.compatibility;

import de.zannagh.eunomia.compatibility.CompatTestFixtures.TestFlag;
import de.zannagh.eunomia.compatibility.known.LuckPermsCompat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static de.zannagh.eunomia.compatibility.CompatTestFixtures.compatFlags;
import static de.zannagh.eunomia.compatibility.CompatTestFixtures.flag;
import static de.zannagh.eunomia.compatibility.CompatTestFixtures.luckPerms;
import static de.zannagh.eunomia.compatibility.CompatTestFixtures.markPresent;
import static de.zannagh.eunomia.compatibility.CompatTestFixtures.reset;
import static de.zannagh.eunomia.compatibility.CompatTestFixtures.setEnsured;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises the flag-query and deferred-initialization core of {@link CompatManager}: presence queries
 * (including dependency composition and type-based lookup), the "add is silently ignored" rules, and the
 * result aggregation of {@link CompatManager#initializeCompats()} / {@link CompatManager#runInitializationRoutine}.
 * The lower-level presence probing lives in {@code CompatManagerProbingTest}.
 */
class CompatManagerTest {

    @BeforeEach
    void setUp() {
        reset();
    }

    @AfterEach
    void tearDown() {
        reset();
    }

    // region Flag queries

    @Test
    void requiresCompatToTracksSimpleFlagPresence() {
        TestFlag a = flag("mod.a.A", false);

        assertThat(CompatManager.requiresCompatTo(a)).isFalse();
        assertThat(CompatManager.isPresent(a)).isFalse();

        markPresent(a);

        assertThat(CompatManager.requiresCompatTo(a)).isTrue();
        assertThat(CompatManager.isPresent(a)).isTrue();
    }

    @Test
    void requiresCompatToWithDependenciesNeedsEveryDependencyPresent() {
        TestFlag b = flag("mod.b.B", false);
        TestFlag c = flag("mod.c.C", false);
        TestFlag composite = new TestFlag(List.of("mod.z.Z"), false, List.of(b, c));

        markPresent(b);
        assertThat(CompatManager.requiresCompatTo(composite)).isFalse();

        markPresent(c);
        assertThat(CompatManager.requiresCompatTo(composite)).isTrue();

        // Presence of a composite flag is decided purely by its dependencies, not by the flag itself.
        assertThat(compatFlags()).doesNotContain(composite);
    }

    @Test
    void requiresCompatToByTypeMatchesAnyPresentInstance() {
        LuckPermsCompat lp = luckPerms();

        assertThat(CompatManager.requiresCompatTo(LuckPermsCompat.class)).isFalse();

        markPresent(lp);

        assertThat(CompatManager.requiresCompatTo(LuckPermsCompat.class)).isTrue();
        assertThat(CompatManager.requiresCompatTo(TestFlag.class)).isFalse();
    }

    @Test
    void requiresCompatToAnyOfIsFalseUntilOneOfTheFlagsIsPresent() {
        TestFlag x = flag("mod.x.X", false);
        TestFlag y = flag("mod.y.Y", false);

        assertThat(CompatManager.requiresCompatToAnyOf()).isFalse();
        assertThat(CompatManager.requiresCompatToAnyOf(x, y)).isFalse();

        markPresent(y);

        assertThat(CompatManager.requiresCompatToAnyOf(x, y)).isTrue();
    }

    // endregion

    // region Initializer registration guards

    @Test
    void assignInitializationIsIgnoredForAnAbsentFlag() {
        TestFlag f = flag("mod.f.F", true);
        setEnsured(true);

        // Flag is not present when the initializer is added, so the registration is dropped...
        CompatManager.assignInitialization(f, () -> CompatInitializationResult.SUCCESS);

        // ...and even once the flag later shows up, no initializer exists for it.
        markPresent(f);
        assertThat(CompatManager.initializeCompats().get(f))
                .containsExactly(CompatInitializationResult.MISSING);
    }

    @Test
    void assignInitializationIsIgnoredWhenTheFlagDoesNotNeedInitialization() {
        TestFlag f = flag("mod.g.G", false);
        markPresent(f);
        setEnsured(true);

        CompatManager.assignInitialization(f, () -> CompatInitializationResult.SUCCESS);

        // A flag that does not need initialization never appears in the results.
        assertThat(CompatManager.initializeCompats()).doesNotContainKey(f);
    }

    // endregion

    // region Initialization result aggregation

    @Test
    void initializeCompatsReportsMissingWhenNoInitializerWasRegistered() {
        TestFlag f = flag("mod.h.H", true);
        markPresent(f);

        var results = CompatManager.initializeCompats();

        assertThat(results.get(f)).containsExactly(CompatInitializationResult.MISSING);
        assertThat(results.get(f).iterator().next().isMissingInitializerResult()).isTrue();
    }

    @Test
    void initializeCompatsSkipsPresentFlagsThatDoNotNeedInitialization() {
        TestFlag needs = flag("mod.k.K", true);
        TestFlag noNeed = flag("mod.l.L", false);
        markPresent(needs);
        markPresent(noNeed);

        var results = CompatManager.initializeCompats();

        assertThat(results).containsOnlyKeys(needs);
        assertThat(results.get(needs)).containsExactly(CompatInitializationResult.MISSING);
    }

    @Test
    void initializeCompatsRunsEveryRegisteredInitializerAndAggregatesResults() {
        TestFlag f = flag("mod.i.I", true);
        markPresent(f);
        setEnsured(true);

        CompatManager.assignInitialization(f, () -> CompatInitializationResult.SUCCESS);
        CompatManager.assignInitialization(f, () -> CompatInitializationResult.FAILURE);

        assertThat(CompatManager.initializeCompats().get(f))
                .containsExactlyInAnyOrder(CompatInitializationResult.SUCCESS, CompatInitializationResult.FAILURE);
    }

    @Test
    void initializeCompatsDeduplicatesIdenticalResults() {
        TestFlag f = flag("mod.j.J", true);
        markPresent(f);
        setEnsured(true);

        CompatManager.assignInitialization(f, () -> CompatInitializationResult.SUCCESS);
        CompatManager.assignInitialization(f, () -> CompatInitializationResult.SUCCESS);

        assertThat(CompatManager.initializeCompats().get(f))
                .containsExactly(CompatInitializationResult.SUCCESS);
    }

    @Test
    void initializeCompatsIsEmptyWhenNothingIsPresent() {
        assertThat(CompatManager.initializeCompats()).isEmpty();
    }

    // endregion

    // region runInitializationRoutine dispatch

    @Test
    void runInitializationRoutineDispatchesToTheInitializerForItsTargetFlag() {
        TestFlag f = flag("mod.m.M", true);
        markPresent(f);
        setEnsured(true);

        CompatInitializer initializer = mock(CompatInitializer.class);
        when(initializer.targetFlag()).thenReturn(f);
        when(initializer.init()).thenReturn(CompatInitializationResult.SUCCESS);

        var results = CompatManager.runInitializationRoutine(initializer);

        assertThat(results.get(f)).containsExactly(CompatInitializationResult.SUCCESS);
        verify(initializer).init();
    }

    @Test
    void runInitializationRoutineIgnoresInitializersForAbsentFlags() {
        TestFlag absentFlag = flag("mod.n.N", true);
        setEnsured(true);

        CompatInitializer initializer = mock(CompatInitializer.class);
        when(initializer.targetFlag()).thenReturn(absentFlag);

        // The flag is not present, so its initializer is never registered nor run.
        var results = CompatManager.runInitializationRoutine(initializer);

        assertThat(results).doesNotContainKey(absentFlag);
        verify(initializer, org.mockito.Mockito.never()).init();
    }

    // endregion
}
