package de.zannagh.eunomia;

import de.zannagh.eunomia.networking.comms.CommunicationManager;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A dedicated server loads the common source set and nothing else. If any class on that side named a
 * {@code net.minecraft.client.*} type - even only in a method descriptor - the JVM would resolve it the
 * first time that member was touched and die with a {@code NoClassDefFoundError} on a server that has no
 * client classes at all. NeoForge defers client init to {@code FMLClientSetupEvent} for exactly this reason.
 *
 * <p>This is the machine-checkable version of that claim, and it is deliberately <em>exhaustive</em> rather
 * than a spot check on a hand-maintained list of class names: it walks the whole compiled output of the
 * {@code :common} main source set and of {@code :core}, reads every {@code .class} file back as bytes and
 * scans the whole thing - constant pool included - for the internal-name prefixes of eunomia's client half.
 * A list of names only ever covers the classes somebody remembered to add; a walk covers the next one too.
 *
 * <p>A regression here is a compile-time mistake that no unit test exercising behaviour would ever catch,
 * because on a development client the classes are present and everything works.
 *
 * <p>{@code :core} is held to the same rule even though it is Minecraft-free by contract - a client
 * reference there would be a worse break, not a lesser one, since {@code :paper} and any future non-game
 * server consume it on a classpath with no Minecraft at all.
 *
 * <p><strong>This test must never pass vacuously.</strong> Both roots are asserted to yield classes, and the
 * classes that historically carried the highest risk of acquiring a client reference are asserted to be
 * among the ones actually walked, so a wrong or stale output directory fails loudly instead of reporting a
 * clean sweep of nothing.
 */
class DedicatedServerSafetyTest {

    /**
     * The internal-name prefixes are matched <em>with</em> their trailing package separator. Without it
     * {@code de/zannagh/eunomia/client} also matches {@code de/zannagh/eunomia/clients/...} - the relay
     * client package in {@code :core}, which is server-side code that a dedicated server is supposed to
     * load - and the guard would report fifteen offenders on a clean tree. A guard that cries wolf gets
     * deleted just as fast as one that never fires.
     */
    private static final String MINECRAFT_CLIENT = "net/minecraft/client/";

    private static final String EUNOMIA_CLIENT = "de/zannagh/eunomia/client/";

    /**
     * Classes that must turn up in the walk. Two jobs: they are the members whose position in the codebase
     * makes an accidental client import most likely (the entry points, the configuration surface, the admin
     * settings channel that sits one import away from the settings screen that drives it, the diagnostics
     * that are deliberately kept Minecraft-free so they can be tested, and the mixin plugin), and together
     * they are the anti-vacuity guard: if the output-directory discovery ever finds the wrong tree, these
     * names are missing from it and the test fails instead of silently checking nothing.
     */
    private static final List<String> MUST_BE_COVERED = List.of(
            "de.zannagh.eunomia.Eunomia",
            "de.zannagh.eunomia.EunomiaMixinPlugin",
            "de.zannagh.eunomia.configuration.EunomiaConfiguration",
            "de.zannagh.eunomia.configuration.EunomiaClientOptions",
            "de.zannagh.eunomia.configuration.EunomiaSyncSettings",
            "de.zannagh.eunomia.diagnostics.SyncDiagnostics",
            "de.zannagh.eunomia.diagnostics.ClientSyncState",
            "de.zannagh.eunomia.compatibility.known.LuckPermsCompat",
            "de.zannagh.eunomia.compatibility.known.LuckPermsHook",
            "de.zannagh.eunomia.utils.ServerUtil",
            "de.zannagh.eunomia.admin.ServerSettingsServerHandlers",
            "de.zannagh.eunomia.admin.ServerConfigProviderAccess",
            "de.zannagh.eunomia.admin.LoaderSettingsAuthority");

    @Test
    void noClassOnTheServerSideNamesAClientOnlyType() {
        Map<String, List<CompiledClass>> byRoot = compiledClassesByRoot();

        List<String> offenders = new ArrayList<>();
        int total = 0;
        for (Map.Entry<String, List<CompiledClass>> root : byRoot.entrySet()) {
            assertThat(root.getValue())
                    .as("found no compiled classes under the %s output - the discovery below found the "
                            + "wrong directory, and a guard that checks nothing is worse than no guard",
                            root.getKey())
                    .isNotEmpty();
            total += root.getValue().size();
            for (CompiledClass compiled : root.getValue()) {
                String bytecode = compiled.bytecode();
                if (bytecode.contains(MINECRAFT_CLIENT)) {
                    offenders.add(compiled.className() + " (" + root.getKey() + ") names " + MINECRAFT_CLIENT);
                }
                if (bytecode.contains(EUNOMIA_CLIENT)) {
                    offenders.add(compiled.className() + " (" + root.getKey() + ") names " + EUNOMIA_CLIENT);
                }
            }
        }

        assertThat(total)
                .as("the whole walk turned up no classes at all")
                .isPositive();
        assertThat(offenders)
                .as("every class a dedicated server loads must be free of client-only references; "
                        + "scanned %d classes across %s", total, byRoot.keySet())
                .isEmpty();
    }

    /**
     * The walk must actually reach the classes that matter. Discovery by code source is robust across the
     * stonecutter variants (each has its own {@code versions/<variant>/build} tree) precisely because it
     * never spells a path out, but that same indirection is what would let it quietly land somewhere empty
     * or somewhere else. Naming the highest-risk classes keeps that failure loud.
     */
    @Test
    void theWalkCoversTheClassesMostLikelyToAcquireAClientReference() {
        List<String> walked = compiledClassesByRoot().values().stream()
                .flatMap(List::stream)
                .map(CompiledClass::className)
                .toList();

        assertThat(walked)
                .as("the compiled-output walk must reach the common main source set's known classes")
                .containsAll(MUST_BE_COVERED);
    }

    // ── Discovery ───────────────────────────────────────────────────────────────────────────────

    /**
     * The compiled output of every source set a dedicated server loads, keyed by a human-readable label.
     *
     * <p>Located from the runtime rather than from a hardcoded path: the code source of a class known to
     * live in each source set <em>is</em> that source set's output, whichever stonecutter variant is active
     * and whichever build directory it writes to. {@link Eunomia} anchors {@code :common}'s main output
     * (never the client output, which is a sibling directory it is not compiled into) and
     * {@link CommunicationManager} anchors {@code :core}'s, which may be a directory in a composite build or
     * a jar on the resolved classpath - both are handled.
     */
    private static Map<String, List<CompiledClass>> compiledClassesByRoot() {
        Map<String, List<CompiledClass>> roots = new LinkedHashMap<>();
        roots.put(":common main", classesIn(codeSourceOf(Eunomia.class)));
        Path core = codeSourceOf(CommunicationManager.class);
        // A composite build hands :core over as a classes directory, a resolved dependency as a jar. Only
        // add it as a second root when it really is a separate one: if both anchors resolve to the same
        // location the classes are already covered and a duplicate root would double every count.
        if (!core.equals(codeSourceOf(Eunomia.class))) {
            roots.put(":core", classesIn(core));
        }
        return roots;
    }

    private static Path codeSourceOf(Class<?> type) {
        CodeSource source = type.getProtectionDomain().getCodeSource();
        assertThat(source)
                .as("%s must have a code source to locate its compiled output from", type.getName())
                .isNotNull();
        assertThat(source.getLocation())
                .as("%s must have a code source location", type.getName())
                .isNotNull();
        try {
            return Path.of(source.getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new AssertionError("Could not resolve the compiled output of " + type.getName(), e);
        }
    }

    /** Every {@code .class} under {@code root}, whether {@code root} is a classes directory or a jar. */
    private static List<CompiledClass> classesIn(Path root) {
        assertThat(root).as("compiled output %s must exist", root).exists();
        if (Files.isDirectory(root)) {
            return classesInDirectory(root);
        }
        return classesInJar(root);
    }

    private static List<CompiledClass> classesInDirectory(Path root) {
        try (Stream<Path> tree = Files.walk(root)) {
            return tree.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".class"))
                    .map(path -> new CompiledClass(classNameOf(root.relativize(path).toString()), readAll(path)))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not walk " + root, e);
        }
    }

    private static List<CompiledClass> classesInJar(Path jar) {
        List<CompiledClass> classes = new ArrayList<>();
        try (JarFile file = new JarFile(jar.toFile())) {
            Enumeration<JarEntry> entries = file.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                    continue;
                }
                try (InputStream stream = file.getInputStream(entry)) {
                    classes.add(new CompiledClass(classNameOf(entry.getName()), decode(stream.readAllBytes())));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + jar, e);
        }
        return classes;
    }

    /** {@code de/zannagh/eunomia/Eunomia.class} (or the Windows-separator form) as a binary class name. */
    private static String classNameOf(String relativePath) {
        String normalized = relativePath.replace('\\', '/');
        return normalized.substring(0, normalized.length() - ".class".length()).replace('/', '.');
    }

    private static String readAll(Path path) {
        try {
            return decode(Files.readAllBytes(path));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + path, e);
        }
    }

    /**
     * The raw class file, decoded as ISO-8859-1 so every byte maps to exactly one char and no byte sequence
     * is lost or merged by a multi-byte decoder. Constant-pool UTF-8 entries hold internal names verbatim,
     * so a plain substring search over this string sees every type the class names.
     */
    private static String decode(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    /** One compiled class file: its binary name, and its bytes as a byte-preserving string. */
    private record CompiledClass(String className, String bytecode) {
    }
}
