package de.zannagh.eunomia.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ExponentialBackoffTest {

    /**
     * Drives the backoff to exhaustion, counting how many times it reports "keep going".
     * Uses a tiny/unit base delay in the fixtures so the real {@link Thread#sleep} calls are ~0-few ms.
     */
    private static int countContinues(ExponentialBackoff backoff) {
        int count = 0;
        while (backoff.shouldContinue()) {
            count++;
        }
        return count;
    }

    @Test
    void freshBackoffHasNotTimedOutAndElapsedIsNonNegative() {
        // Tiny base so no meaningful sleep can happen even if it were driven.
        ExponentialBackoff backoff = new ExponentialBackoff(0.0001, 30_000, 4);

        assertThat(backoff.hasTimedOut).isFalse();
        assertThat(backoff.getElapsedMillisSinceFirstAttempt()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void continuesMaxAttemptsMinusOneTimesThenTimesOut() {
        // Base is tiny so getDelayMillis() rounds to 0 for every attempt: fast, deterministic.
        // maxAttempts=4 is well below the delay-derived cap, so it is the effective limit.
        ExponentialBackoff backoff = new ExponentialBackoff(0.0001, 30_000, 4);

        int continues = countContinues(backoff);

        // attempts starts at 1 and must stay < maxAttempts to continue -> exactly maxAttempts-1 trues.
        assertThat(continues).isEqualTo(3);
        assertThat(backoff.hasTimedOut).isTrue();
        // Once timed out it stays timed out and never continues again.
        assertThat(backoff.shouldContinue()).isFalse();
    }

    @Test
    void maxAttemptsOfOneNeverContinuesAndImmediatelyTimesOut() {
        // Effective maxAttempts collapses to 1 -> attempts(1) >= maxAttempts(1) on the first call.
        ExponentialBackoff backoff = new ExponentialBackoff(0.0001, 30_000, 1);

        assertThat(backoff.shouldContinue()).isFalse();
        assertThat(backoff.hasTimedOut).isTrue();
    }

    @Test
    void attemptCountIsCappedByMaxDelayNotByRequestedMaxAttempts() {
        // maxDelay/base = 8 -> floor(log2(8 + 2)) = floor(log2(10)) = 3.
        // Requested maxAttempts is huge, so the delay-derived cap of 3 wins.
        ExponentialBackoff backoff = new ExponentialBackoff(1.0, 8.0, 1000);

        int continues = countContinues(backoff);

        assertThat(continues).isEqualTo(2); // cap 3 -> 3 - 1 continues
        assertThat(backoff.hasTimedOut).isTrue();
    }

    @Test
    void requestedMaxAttemptsWinsWhenSmallerThanDelayDerivedCap() {
        // Delay-derived cap would be large, but the caller asked for only 2 attempts.
        ExponentialBackoff backoff = new ExponentialBackoff(0.0001, 30_000, 2);

        assertThat(countContinues(backoff)).isEqualTo(1);
    }

    @Test
    void attemptCountNeverDropsBelowOneEvenWhenMaxDelayIsBelowBaseDelay() {
        // maxDelay(1) < base(10): (1/10 + 2) = 2.1 -> floor(log2(2.1)) = 1, floored up to at least 1.
        // Effective maxAttempts = 1 -> no continues, no sleep is ever reached.
        ExponentialBackoff backoff = new ExponentialBackoff(10.0, 1.0, 1000);

        assertThat(countContinues(backoff)).isZero();
        assertThat(backoff.hasTimedOut).isTrue();
    }

    @Test
    void delayThatOverflowsIntRangeThrowsBeforeSleeping() {
        // base * 2^attempts overflows int on the very first attempt:
        //   round(2e9 * 2^1) = 4_000_000_000 > Integer.MAX_VALUE -> Math.toIntExact throws.
        // maxDelay is large enough that the effective maxAttempts (~5) allows a first getDelayMillis().
        ExponentialBackoff backoff = new ExponentialBackoff(2_000_000_000.0, 100_000_000_000.0, 16);

        assertThatExceptionOfType(ArithmeticException.class)
                .isThrownBy(backoff::shouldContinue);
        // The overflow is detected while computing the delay, before Thread.sleep and before
        // hasTimedOut would be set, so the flag remains untouched.
        assertThat(backoff.hasTimedOut).isFalse();
    }

    @Test
    void apiBackoffUsesUnitBaseDelay() {
        // base=1, maxDelay=8 -> floor(log2(8 + 2)) = 3 -> 2 continues. Sleeps are 2ms + 4ms.
        ExponentialBackoff backoff = ExponentialBackoff.apiBackoff(8);

        assertThat(countContinues(backoff)).isEqualTo(2);
        assertThat(backoff.hasTimedOut).isTrue();
    }

    @Test
    void factoryBackoffsConstructInAFreshNotTimedOutState() {
        // Do not drive these to exhaustion: their real per-attempt sleeps grow into seconds.
        ExponentialBackoff defaults = ExponentialBackoff.defaultBackoff();
        ExponentialBackoff api = ExponentialBackoff.apiBackoff();

        assertThat(defaults).isNotNull();
        assertThat(defaults.hasTimedOut).isFalse();
        assertThat(defaults.getElapsedMillisSinceFirstAttempt()).isGreaterThanOrEqualTo(0);
        assertThat(api).isNotNull();
        assertThat(api.hasTimedOut).isFalse();
    }

    @Test
    void elapsedTimeIsMonotonicAcrossAttempts() {
        // Unit base with a small cap: a couple of short real sleeps happen while draining.
        ExponentialBackoff backoff = new ExponentialBackoff(1.0, 8.0, 1000);

        int before = backoff.getElapsedMillisSinceFirstAttempt();
        countContinues(backoff);
        int after = backoff.getElapsedMillisSinceFirstAttempt();

        assertThat(after).isGreaterThanOrEqualTo(before);
    }
}
