package de.zannagh.eunomia.smoke;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tier-3 end-to-end smoke: forks {@code ./gradlew :fabric:<variant>:runClientGametest} against a real
 * Minecraft client to exercise the Eunomia networking path in-game (see {@code smoke/README.md} and
 * {@code common/src/client/.../smoke/NetworkingSmokeTest.java}).
 *
 * <p>The FCGT harness is dormant until a game version pins {@code fabricapi.semver} in
 * {@code stonecutter.properties.toml}. This test therefore <em>discovers</em> the enabled variants (or
 * takes an explicit {@code -Dsmoke.variant=fabric-26.2}) and <em>skips</em> when none are enabled, so
 * {@code ./gradlew smokeTest} is always runnable and green out of the box.
 */
class FcgtSmokeTest {

    // A `["fabric-<mc>"]` section header in stonecutter.properties.toml.
    private static final Pattern FABRIC_SECTION = Pattern.compile("^\\[\"?(fabric-[^\"\\]]+)\"?]\\s*$");

    @Test
    @DisplayName("FCGT networking gametest passes on every enabled Fabric variant")
    void fcgtGametestPasses() throws IOException, InterruptedException {
        File repoRoot = repoRoot();
        List<String> variants = enabledFcgtVariants(repoRoot);

        Assumptions.assumeFalse(
                variants.isEmpty(),
                "No FCGT-enabled Fabric variant (no 'fabricapi.semver' in stonecutter.properties.toml, "
                        + "and no -Dsmoke.variant given). Skipping - see smoke/README.md to enable.");

        for (String variant : variants) {
            int exit = runGradle(repoRoot, ":fabric:" + variant + ":runClientGametest", "--stacktrace");
            assertEquals(0, exit, "runClientGametest failed for " + variant + " (exit " + exit + ")");
        }
    }

    private static File repoRoot() {
        String root = System.getProperty("eunomia.repo.root");
        if (root == null || root.isBlank()) {
            fail("eunomia.repo.root system property not set (should be wired by smoke/build.gradle.kts)");
        }
        return new File(root);
    }

    /**
     * Explicit {@code -Dsmoke.variant} wins; otherwise every {@code ["fabric-<mc>"]} section in
     * stonecutter.properties.toml that carries a {@code fabricapi.semver} key is FCGT-enabled.
     */
    private static List<String> enabledFcgtVariants(File repoRoot) throws IOException {
        String explicit = System.getProperty("smoke.variant");
        if (explicit != null && !explicit.isBlank()) {
            List<String> single = new ArrayList<>();
            single.add(explicit.trim());
            return single;
        }

        File toml = new File(repoRoot, "stonecutter.properties.toml");
        List<String> enabled = new ArrayList<>();
        String currentSection = null;
        for (String raw : Files.readAllLines(toml.toPath(), StandardCharsets.UTF_8)) {
            String line = raw.trim();
            Matcher section = FABRIC_SECTION.matcher(line);
            if (section.matches()) {
                currentSection = section.group(1);
                continue;
            }
            if (line.startsWith("[")) {
                currentSection = null;
                continue;
            }
            if (currentSection != null && line.startsWith("fabricapi.semver")) {
                enabled.add(currentSection.substring("fabric-".length()));
            }
        }
        return enabled;
    }

    private static int runGradle(File repoRoot, String... args) throws IOException, InterruptedException {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        List<String> command = new ArrayList<>();
        command.add(new File(repoRoot, windows ? "gradlew.bat" : "gradlew").getAbsolutePath());
        for (String arg : args) {
            command.add(arg);
        }

        Process process = new ProcessBuilder(command)
                .directory(repoRoot)
                .redirectErrorStream(true)
                .start();

        try (InputStream in = process.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[gradle] " + line);
            }
        }
        return process.waitFor();
    }
}
