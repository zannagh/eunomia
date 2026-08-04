package de.zannagh.eunomia.compatibility;

import de.zannagh.eunomia.common.EnrichedLogger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.function.Supplier;

/**
 * Lightweight compat flags set during mixin plugin load - before MC classes are available.
 * This class must NOT import any Minecraft classes to avoid early class loading.
 *
 * <p>Flags are set via {@link #setCompatFlagByResourceProbing(ClassLoader)} during mixin-plugin load
 * (resource-based probing only), and may be gap-filled later via {@link #setCompatFlag(ClassLoader)}
 * (which can use {@code Class.forName(..., false, ...)}).</p>
 *
 * <p><b>Note:</b> Iris must not be reached through the {@code Class.forName} path at mixin time - loading
 * it early breaks client startup on NeoForge. It is only resource-probed early and initialised later.</p>
 */
public final class CompatManager {

    /** Standalone logger (no ArmorHider/Minecraft dependency) so this stays mixin-load safe. */
    private static final EnrichedLogger LOG = new EnrichedLogger(LoggerFactory.getLogger("Armor Hider - Compat"));

    /**
     * Compat flags that were registered and will be tested on probing via {@link #setCompatFlagByResourceProbing(ClassLoader)}.
     */
    private static final HashSet<CompatFlag> FLAG_DEPENDENCIES = new HashSet<>();

    /**
     * Compat flags that were probed.
     */
    private static final HashSet<CompatFlag> COMPAT_FLAGS = new HashSet<>();

    /**
     * The subset of {@link #COMPAT_FLAGS} that was detected by the mixin-safe resource probe (never
     * {@code Class.forName}). Kept separate from the {@link #setCompatFlag} class-load gap-fill so the
     * smoke consistency check ({@link #resourceProbingGaps}) can verify the resource probe alone detected
     * every mod that is actually present.
     */
    public static final HashSet<CompatFlag> RESOURCE_PROBED_FLAGS = new HashSet<>();

    private static final HashMap<CompatFlag, HashSet<Supplier<CompatInitializationResult>>> INITIALIZATIONS = new HashMap<>();

    private static boolean CompatFlagEnsured;

    private CompatManager() {}

    public static void registerCompatFlag(CompatFlag flag) {
        FLAG_DEPENDENCIES.add(flag);
    }

    // region Low-level presence probing

    /**
     * Probes whether a class exists without instantiating it (and loading imports). <br/>
     * Meant to probe whether other mods are present without causing mod loading exceptions.
     * @param name The name of the class to probe.
     * @return True when the class exists, otherwise false.
     */
    public static boolean classExists(String name) {
        return classExists(name, CompatManager.class.getClassLoader());
    }

    public static boolean classExists(String name, ClassLoader cl) {
        try {
            Class.forName(name, false, cl);
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            // ClassNotFoundException: class absent. LinkageError (NoClassDefFoundError,
            // UnsupportedClassVersionError, VerifyError, …): class found but unlinkable
            try {
                return isModPresent(cl, name);
            } catch (Exception ignored) {
                return false;
            }
        }
    }

    /**
     * Checks whether a mod is present without loading any classes.
     * <ol>
     *   <li>Probes for the exact {@code .class} resource.</li>
     *   <li>Falls back to checking the class's own package directory (every segment before the
     *       last), so a mod whose entrypoint class was renamed but still lives in the same package
     *       is still detected. Best-effort: a jar without explicit directory entries won't expose the
     *       package dir as a resource, so this can under-report; the primary probe covers the normal case.</li>
     * </ol>
     */
    public static boolean isModPresent(ClassLoader cl, String className) {
        if (cl.getResource(className.replace('.', '/') + ".class") != null) {
            return true;
        }
        int lastDot = className.lastIndexOf('.');
        if (lastDot > 0) {
            String packageProbe = className.substring(0, lastDot).replace('.', '/') + "/";
            try {
                return cl.getResources(packageProbe).hasMoreElements();
            } catch (IOException e) {
                return false;
            }
        }
        return false;
    }

    // endregion

    // region Flag detection passes

    public static void setCompatFlagByResourceProbing() {
        setCompatFlagByResourceProbing(CompatManager.class.getClassLoader());
    }

    public static void setCompatFlagByResourceProbing(ClassLoader classLoader) {
        for (var flag : FLAG_DEPENDENCIES) {
            if (flag.classNames().stream().anyMatch(string -> isModPresent(classLoader, string))) {
                RESOURCE_PROBED_FLAGS.add(flag);
                setCompatFlag(flag);
            }
        }
    }

    public static void setCompatFlag() {
        setCompatFlag(CompatManager.class.getClassLoader());
    }

    /**
     * Gap-fills compat flags with a {@code Class.forName} presence check (via {@link #classExists}), for
     * mods the mixin-safe resource probe missed. Skips flags already set so it never re-loads an
     * already-detected mod. Must run at client init, NOT at mixin time, so the class loads it triggers are
     * safe (see {@link #setCompatFlagByResourceProbing} for the mixin-time, class-load-free path).
     *
     * @param cl the classloader to probe (usually the MixinPlugin's own classloader)
     */
    public static void setCompatFlag(ClassLoader cl) {
        if (CompatFlagEnsured) {
            return;
        }
        for (var compat : FLAG_DEPENDENCIES) {
            // Resource probing (setCompatFlagByResourceProbing) runs first, at mixin-plugin load, and
            // already flagged everything it could see without loading a class. Skip those here so the
            // Class.forName pass never re-loads an already-detected mod - this matters for Iris, whose
            // early class load breaks client startup on NeoForge. Only gap-fill the mods resource
            // probing missed.
            if (COMPAT_FLAGS.contains(compat)) {
                continue;
            }
            if (compat.classNames().stream().anyMatch(name -> classExists(name, cl))) {
                setCompatFlag(compat);
            }
        }
        CompatFlagEnsured = true;
    }

    /**
     * Smoke/diagnostic consistency check: for every compat flag whose mod is <b>definitively</b> present -
     * verified here by {@code Class.forName}, safe to do post-boot unlike at mixin time - assert the
     * mixin-safe resource probe ({@link #RESOURCE_PROBED_FLAGS}) also detected it. A mod that loads but
     * was not resource-probed means the class-load-free probing path has a gap and compat gating would
     * silently misfire in production. Returns the set of such gaps ({@code empty} = probing is sound).
     *
     * @param cl the classloader to verify against (the mod's own classloader)
     */
    public static HashSet<CompatFlag> resourceProbingGaps(ClassLoader cl) {
        HashSet<CompatFlag> gaps = new HashSet<>();
        for (var flag : FLAG_DEPENDENCIES) {
            boolean presentByClassLoad = flag.classNames().stream().anyMatch(name -> {
                try {
                    Class.forName(name, false, cl);
                    return true;
                } catch (Throwable ignored) {
                    return false;
                }
            });
            if (presentByClassLoad && !RESOURCE_PROBED_FLAGS.contains(flag)) {
                gaps.add(flag);
            }
        }
        return gaps;
    }

    // endregion

    // region Flag queries
    public static boolean requiresCompatTo(CompatFlag flag) {
        if (!flag.dependencies().isEmpty()) {
            return COMPAT_FLAGS.containsAll(flag.dependencies());
        }
        return COMPAT_FLAGS.contains(flag);
    }

    public static <T> boolean requiresCompatTo(Class<T> flag) {
        return COMPAT_FLAGS.stream().anyMatch(flag::isInstance);
    }

    public static boolean requiresCompatToAnyOf(CompatFlag... flags) {
        for (CompatFlag flag : flags) {
            if (COMPAT_FLAGS.contains(flag)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the given mod was detected as present (distinct from the low-level {@link #isModPresent(ClassLoader, String)} probe). */
    public static boolean isPresent(CompatFlag flag) {
        return requiresCompatTo(flag);
    }

    // endregion

    // region Deferred initialization

    /**
     * Runs the initialization routine for the given compat initializers.
     * @param initializers The initializers for specific flag, {@link CompatInitializer}.
     * @return The results of the initialization routine.
     */
    public static HashMap<CompatFlag, HashSet<CompatInitializationResult>> runInitializationRoutine(CompatInitializer... initializers) {
        setCompatFlag();

        for (CompatInitializer initializer : initializers) {
            addInitializer(initializer);
        }

        return initializeCompats();
    }

    /**
     * Assigns an initializer for a compat flag.
     * It is safe to call this method even when the compat flag is not present. <br/>
     * This method will internally call {@link #setCompatFlag()} if the compat flags have not been ensured yet,
     * so it must be safe to classload at the point in time when initializers are added and this method is called.<br/><br/>
     * It is safe to add an initializer to a mod flag that is not present at runtime. In this case, adding
     * the initializer will be ignored.
     * @param initializer The initializer to assign
     */
    public static void addInitializer(CompatInitializer initializer) {
        assignInitialization(initializer.targetFlag(), initializer::init);
    }

    /**
     * Assigns an initialization method.<br/>
     * <br/>
     * This method will internally call {@link #setCompatFlag()} if the compat flags have not been ensured yet,
     * so it must be safe to classload at the point in time when initializers are added and this method is called.<br/><br/>
     * It is safe to add an initializer to a mod flag that is not present at runtime. In this case, adding
     * the initializer will be ignored.
     * @param flag The flag to assign an initialization method to.
     */
    public static void assignInitialization(CompatFlag flag, Supplier<CompatInitializationResult> initialization) {
        if (!CompatFlagEnsured) {
            setCompatFlag();
        }

        if (!flag.needsInitialization()) {
            return;
        }

        if (!isPresent(flag)) {
            return;
        }

        INITIALIZATIONS.computeIfAbsent(flag, key -> new HashSet<>()).add(initialization);
    }

    /**
     * Initializes compat flags that require initialization
     * @return A map of each compat flag to the results of its initializers ({@link CompatInitializationResult#MISSING} when a flag needs initialization but none was registered).
     */
    public static HashMap<CompatFlag, HashSet<CompatInitializationResult>> initializeCompats() {
        HashMap<CompatFlag, HashSet<CompatInitializationResult>> compatInitializations = new HashMap<>();
        for (var presentCompat : COMPAT_FLAGS) {
            if (!presentCompat.needsInitialization()) {
                continue;
            }
            var initializers = INITIALIZATIONS.get(presentCompat);
            if (initializers == null) {
                compatInitializations.put(presentCompat, new HashSet<>(List.of(CompatInitializationResult.MISSING)));
                continue;
            }
            var results = new HashSet<CompatInitializationResult>();
            for (var initializer : initializers) {
                results.add(initializer.get());
            }
            compatInitializations.put(presentCompat, results);
        }
        return compatInitializations;
    }

    // endregion

    private static void setCompatFlag(CompatFlag flag) {
        COMPAT_FLAGS.add(flag);
    }
}
