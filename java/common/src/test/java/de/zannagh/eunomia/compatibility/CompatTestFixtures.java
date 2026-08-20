package de.zannagh.eunomia.compatibility;

import de.zannagh.eunomia.common.SemanticVersion;
import de.zannagh.eunomia.compatibility.known.LuckPermsCompat;

import java.lang.reflect.Field;
import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Package-local helpers for the {@link CompatManager} tests. {@code CompatManager} is an all-static
 * singleton with no reset hook, so {@link #reset()} scrubs its static collections between tests via
 * reflection, and {@link #markPresent(CompatFlag)} / {@link #setEnsured(boolean)} drive the pieces of
 * its lifecycle that are otherwise private.
 */
final class CompatTestFixtures {

    static final URL DUMMY_URL = dummyUrl();

    private CompatTestFixtures() {
    }

    /** Scrubs all of {@link CompatManager}'s static state back to a clean slate. */
    static void reset() {
        clear("FLAG_DEPENDENCIES");
        clear("COMPAT_FLAGS");
        CompatManager.RESOURCE_PROBED_FLAGS.clear();
        clear("INITIALIZATIONS");
        setEnsured(false);
    }

    @SuppressWarnings("unchecked")
    static Set<CompatFlag> compatFlags() {
        return (Set<CompatFlag>) field("COMPAT_FLAGS");
    }

    /** Forces a flag into the detected-present set, as if a probe had found it. */
    static void markPresent(CompatFlag flag) {
        compatFlags().add(flag);
    }

    /** Flips the internal "flags already ensured" latch so lifecycle calls skip the classloader probe. */
    static void setEnsured(boolean value) {
        try {
            Field f = CompatManager.class.getDeclaredField("CompatFlagEnsured");
            f.setAccessible(true);
            f.setBoolean(null, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static TestFlag flag(String className, boolean needsInit) {
        return new TestFlag(List.of(className), needsInit, List.of());
    }

    /** A concrete {@link LuckPermsCompat} (the class itself only leaves {@code since()} abstract). */
    static LuckPermsCompat luckPerms() {
        return new LuckPermsCompat() {
            @Override
            public SemanticVersion since() {
                return new SemanticVersion(5, 0, 0, null);
            }
        };
    }

    private static void clear(String name) {
        Object value = field(name);
        if (value instanceof Map<?, ?> map) {
            map.clear();
        } else {
            ((Collection<?>) value).clear();
        }
    }

    private static Object field(String name) {
        try {
            Field f = CompatManager.class.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static URL dummyUrl() {
        try {
            return URI.create("file:///dummy").toURL();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** A simple in-test {@link CompatFlag}; identity is driven by its {@code classNames}. */
    record TestFlag(List<String> classNames, boolean needsInitialization, List<CompatFlag> dependencies)
            implements CompatFlag {
        @Override
        public SemanticVersion since() {
            return new SemanticVersion(1, 0, 0, null);
        }
    }

    /**
     * A {@link ClassLoader} whose resource view is fully controlled by the supplied set of resource
     * paths. It never loads real classes, so it is a safe stand-in for a mod jar during probing.
     */
    static final class FakeClassLoader extends ClassLoader {

        private final Set<String> resources;

        FakeClassLoader(Set<String> resources) {
            super(null);
            this.resources = resources;
        }

        @Override
        public URL getResource(String name) {
            if (resources.contains(name)) {
                return DUMMY_URL;
            }
            return null;
        }

        @Override
        public Enumeration<URL> getResources(String name) {
            if (resources.contains(name)) {
                return Collections.enumeration(List.of(DUMMY_URL));
            }
            return Collections.emptyEnumeration();
        }
    }
}
