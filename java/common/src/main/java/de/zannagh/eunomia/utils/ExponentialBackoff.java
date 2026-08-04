package de.zannagh.eunomia.utils;

/**
 * An exponential backoff helper following IEE802 standards.
 * Increases the delay exponentially with each attempt (shouldContinue() call) from a base of 52.1 microseconds.
 * If the calculated attempt count exceeds 16, it will be capped at 16 (effective delay of ~16.384 seconds).
 */
public class ExponentialBackoff {
    private static final double DEFAULT_DELAY_MILLIS = 0.521; // 52.1 microseconds in ms
    private int attempts = 1;
    private final double baseDelayMillis;
    private final int maxAttempts;
    private final long initialMillis;

    public ExponentialBackoff(double baseDelayMillis, double maxDelayMillis, int maxAttempts) {
        this.baseDelayMillis = baseDelayMillis;
        double maxAttemptsDouble = Math.log((maxDelayMillis / baseDelayMillis) + 2) / Math.log(2);
        int maxAttemptsByDelay = Math.max(1, (int) Math.floor(maxAttemptsDouble));
        this.maxAttempts = Math.min(maxAttemptsByDelay, maxAttempts);
        initialMillis = System.currentTimeMillis();
    }

    public ExponentialBackoff(int maxDelayMillis) {
        this(DEFAULT_DELAY_MILLIS, maxDelayMillis, 16);
    }

    /**
     * Creates an exponential backoff with a default max delay of 30 seconds and an initial delay of 52.1 microseconds.
     */
    public static ExponentialBackoff defaultBackoff() {
        return new ExponentialBackoff(30 * 1000);
    }

    /**
     * Creates an exponential backoff with a default max delay of 30 seconds and an initial delay of 1 millisecond to prevent hammering remote APIs.
     */
    public static ExponentialBackoff apiBackoff(){
        return new ExponentialBackoff(1, 30 * 1000, 16);
    }

    /**
     * Creates an exponential backoff with a default max delay of 30 seconds and an initial delay of 1 millisecond to prevent hammering remote APIs.
     */
    public static ExponentialBackoff apiBackoff(int maxWaitMillis){
        return new ExponentialBackoff(1, maxWaitMillis, 16);
    }

    private int getDelayMillis() {
        return Math.toIntExact(Math.round(baseDelayMillis * Math.pow(2, attempts)));
    }

    public boolean hasTimedOut;

    public int getElapsedMillisSinceFirstAttempt() {
        return (int) (System.currentTimeMillis() - initialMillis);
    }

    public boolean shouldContinue() {
        if (attempts >= maxAttempts) {
            hasTimedOut = true;
            return false;
        }
        var delayMillis = getDelayMillis();
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            hasTimedOut = true;
            return false;
        }
        attempts++;
        return true;
    }
}
