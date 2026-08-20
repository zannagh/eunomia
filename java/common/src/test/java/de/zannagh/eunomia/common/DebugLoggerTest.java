package de.zannagh.eunomia.common;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link DebugLogger}'s time-window gating, consecutive-duplicate suppression, SLF4J-style
 * placeholder formatting and throwable capture. The logger writes to {@code logs/eunomia/*.log} relative
 * to the working directory; each test tags its lines with a unique token so assertions are independent of
 * any other content in those files, and the directory is removed afterwards.
 */
class DebugLoggerTest {

    private static final Path LOG_DIR = Path.of("logs", "eunomia");

    @BeforeEach
    void reset() {
        // Bring the global logger to a known DISABLED state before each test.
        DebugLogger.disable();
    }

    @AfterEach
    void tearDown() {
        DebugLogger.disable();
    }

    @AfterAll
    static void cleanUp() throws IOException {
        if (!Files.exists(LOG_DIR)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(LOG_DIR)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            });
        }
    }

    private static String readAllLogs() throws IOException {
        if (!Files.exists(LOG_DIR)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try (Stream<Path> paths = Files.list(LOG_DIR)) {
            for (Path p : paths.filter(x -> x.getFileName().toString().endsWith(".log")).toList()) {
                sb.append(Files.readString(p));
            }
        }
        return sb.toString();
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    @Test
    void disabledByDefaultReportsNoRemainingTime() {
        assertThat(DebugLogger.isEnabled()).isFalse();
        assertThat(DebugLogger.remainingSeconds()).isZero();
    }

    @Test
    void enableTurnsOnGatingAndCountsDownFromFiveMinutes() {
        DebugLogger.enable();

        assertThat(DebugLogger.isEnabled()).isTrue();
        // Window is exactly 5 minutes; allow a few seconds of slack for test execution.
        assertThat(DebugLogger.remainingSeconds()).isBetween(295L, 300L);
    }

    @Test
    void disableClearsGatingImmediately() {
        DebugLogger.enable();
        assertThat(DebugLogger.isEnabled()).isTrue();

        DebugLogger.disable();

        assertThat(DebugLogger.isEnabled()).isFalse();
        assertThat(DebugLogger.remainingSeconds()).isZero();
    }

    @Test
    void logIsWrittenOnlyWhileEnabled() throws IOException {
        String skipped = "skip-" + UUID.randomUUID();
        String kept = "keep-" + UUID.randomUUID();

        // While disabled the call is a no-op - no active writer exists.
        DebugLogger.log(skipped);

        DebugLogger.enable();
        DebugLogger.log(kept);
        DebugLogger.disable();

        String contents = readAllLogs();
        assertThat(contents).contains(kept);
        assertThat(contents).doesNotContain(skipped);
    }

    @Test
    void consecutiveIdenticalMessagesAreSuppressedButRepeatsAroundOthersAreNot() throws IOException {
        String a = "dupA-" + UUID.randomUUID();
        String b = "dupB-" + UUID.randomUUID();

        DebugLogger.enable();
        DebugLogger.log(a);
        DebugLogger.log(a); // immediate duplicate -> suppressed
        DebugLogger.log(b);
        DebugLogger.log(a); // same text but not consecutive -> written again
        DebugLogger.disable();

        String contents = readAllLogs();
        assertThat(countOccurrences(contents, a)).isEqualTo(2);
        assertThat(countOccurrences(contents, b)).isEqualTo(1);
    }

    @Test
    void singlePlaceholderIsFormattedIntoTheLine() throws IOException {
        String token = "one-" + UUID.randomUUID();

        DebugLogger.enable();
        DebugLogger.log(token + " value={}", 42);
        DebugLogger.disable();

        assertThat(readAllLogs()).contains(token + " value=42");
    }

    @Test
    void twoPlaceholdersAreFormattedInOrder() throws IOException {
        String token = "two-" + UUID.randomUUID();

        DebugLogger.enable();
        DebugLogger.log(token + " {}->{}", "from", "to");
        DebugLogger.disable();

        assertThat(readAllLogs()).contains(token + " from->to");
    }

    @Test
    void varargsPlaceholdersAreFormattedAndTrailingThrowableStackIsCaptured() throws IOException {
        String token = "vararg-" + UUID.randomUUID();
        String marker = "stack-marker-" + UUID.randomUUID();
        RuntimeException boom = new RuntimeException(marker);

        DebugLogger.enable();
        // arrayFormat consumes the trailing Throwable as the exception, leaving two placeholders to fill.
        DebugLogger.log(token + " {} {}", "x", "y", boom);
        DebugLogger.disable();

        String contents = readAllLogs();
        assertThat(contents).contains(token + " x y");
        // The throwable's stack trace (carrying its message) is appended after the formatted line.
        assertThat(contents).contains(marker);
        assertThat(contents).contains("java.lang.RuntimeException");
    }

    @Test
    void writeConfigPersistsSnapshotNextToTheActiveLogFile() throws IOException {
        String content = "config-body-" + UUID.randomUUID();

        DebugLogger.enable();
        DebugLogger.writeConfig(content);
        DebugLogger.disable();

        boolean found = false;
        try (Stream<Path> paths = Files.list(LOG_DIR)) {
            for (Path p : paths.filter(x -> x.getFileName().toString().endsWith(".config.txt")).toList()) {
                if (Files.readString(p).contains(content)) {
                    found = true;
                    break;
                }
            }
        }
        assertThat(found).as("a .config.txt snapshot containing the written body must exist").isTrue();
    }
}
