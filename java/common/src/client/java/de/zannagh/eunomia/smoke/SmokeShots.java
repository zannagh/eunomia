//? if fcgt {
package de.zannagh.eunomia.smoke;

import de.zannagh.eunomia.Eunomia;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

// Screenshot plumbing shared by the UI smokes. Two jobs, both of which exist so the PNGs are a
// deliverable rather than debris:
//
//  - Collection. Screenshots default to <runDir>/screenshots, which on this build is
//    java/fabric/versions/fabric-<variant>/run/screenshots - seven directories a human then has to
//    hunt through. The `eunomia.smoke.screenshotDir` system property (set by the clientGametest run
//    config in the loom convention plugin) points every shot at
//    java/build/ui-screenshots/<variant>/ instead, so one directory holds the whole matrix.
//  - Freshness. The directory is emptied exactly once per client launch, before the first shot. A
//    stale PNG from a previous, possibly failing run is worse than a missing one, because it looks
//    exactly like a passing result.
//
// The window is resized once per launch as well: the shot is the framebuffer, so the framebuffer is
// what caps the file size. Resizing through TestInput (rather than the screenshot options' withSize)
// goes through the real resize path, which re-runs Screen#init - so what is captured is a screen
// actually laid out for that size, not one laid out for the old size and rendered into a new target.
public final class SmokeShots {

    // 720p: legible for a human eye-balling the UI, small enough that seven variants of these fit in
    // a base64-inlined HTML page.
    private static final int WINDOW_WIDTH = 1280;

    private static final int WINDOW_HEIGHT = 720;

    private static final AtomicBoolean PREPARED = new AtomicBoolean();

    private SmokeShots() {
    }

    // Empties the destination directory and fixes the window size. Idempotent per client launch, so
    // every test class may call it first without stepping on a sibling's already-written shots.
    public static void prepare(ClientGameTestContext context) {
        if (PREPARED.compareAndSet(false, true)) {
            Path directory = destination();
            wipe(directory);
            Eunomia.LOGGER.info("[smoke/ui] screenshots -> {}", directory.toAbsolutePath());
        }
        // The resize is NOT once-per-launch: FCGT restores the window between test classes, so every
        // test has to ask for the size it wants or its shots come out at the default 854x480.
        context.getInput().resizeWindow(WINDOW_WIDTH, WINDOW_HEIGHT);
        context.waitTicks(2);
    }

    // Takes one shot under a stable, counter-free name, so a re-run overwrites rather than accumulates.
    public static Path shoot(ClientGameTestContext context, String name) {
        Path file = context.takeScreenshot(TestScreenshotOptions.of(name)
                .disableCounterPrefix()
                .withDestinationDir(destination()));
        Eunomia.LOGGER.info("[smoke/ui] screenshot '{}' -> {}", name, file.toAbsolutePath());
        return file;
    }

    // The collected destination, or the vanilla screenshots directory when the property is absent
    // (which is what happens if someone launches the run config by hand from an IDE).
    private static Path destination() {
        String configured = System.getProperty("eunomia.smoke.screenshotDir");
        if (configured == null || configured.isBlank()) {
            return Paths.get("screenshots").toAbsolutePath();
        }
        return Paths.get(configured).toAbsolutePath();
    }

    private static void wipe(Path directory) {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> entries = Files.walk(directory)) {
            entries.sorted(Comparator.reverseOrder())
                    .filter(path -> !path.equals(directory))
                    .forEach(SmokeShots::deleteQuietly);
        } catch (IOException e) {
            Eunomia.LOGGER.warn("[smoke/ui] could not clear {}", directory, e);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.delete(path);
        } catch (IOException e) {
            Eunomia.LOGGER.warn("[smoke/ui] could not delete {}", path, e);
        }
    }
}
//?}
