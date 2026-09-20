package de.zannagh.eunomia.networking.serialization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The wire-facing wrapper around {@link PayloadCodec#decode}: it turns "these bytes are not something
 * we can read" from a thrown exception into a dropped packet.
 * <p>
 * This exists because of where a decode runs. Every inbound path (the loader StreamCodec, the legacy
 * {@code FriendlyByteBuf} dispatch, the Paper plugin-message listener) decodes inside the network
 * pipeline, so anything thrown there is wrapped by Netty into a {@code DecoderException} and <em>kills
 * the connection</em> - the client is disconnected with "Failed to decode packet", or, in the
 * serverbound direction, any client can drop its own connection by sending one malformed payload on one
 * of our channels. Vanilla itself is deliberately forgiving here: an unknown custom payload becomes a
 * {@code DiscardedPayload} rather than an error. We match that: an undecodable payload is logged (see
 * the throttle below) and dropped, and the connection survives.
 * <p>
 * The realistic source of undecodable bytes is a <em>version mismatch</em>: a mod that used to own the
 * same channel names with a different framing (armor-hider before 0.14.0 wrote
 * {@code int32 length + gzip(json)}; Eunomia writes bare {@code gzip(json)}). Those bytes are not a bug
 * and not an attack, so they are logged at WARN. Bytes that <em>are</em> in our format but whose JSON
 * does not parse point at a genuine defect in our own encoding and stay loud at ERROR - they are still
 * dropped, because killing the connection helps nobody, but they are not quietly normalised away.
 * <p>
 * Both are logged at most once per channel per {@value #REPORT_INTERVAL_MINUTES} minutes and otherwise
 * counted, because these arrive per packet - a server broadcasting its config every few seconds would
 * otherwise flood the log. The interval (rather than a strict once) is what keeps a long-lived dedicated
 * server informative: it never calls {@link #reset()}, so a strict once would mean only the very first
 * outdated client of the whole uptime was ever reported. On the client, {@link #reset()} re-arms
 * reporting on each new connection, so joining a different server warns again immediately.
 */
public final class PayloadDecodeGuard {

    private static final Logger LOGGER = LoggerFactory.getLogger("eunomia-net");

    /** How long a channel stays quiet after a report before the next failure is described again. */
    static final int REPORT_INTERVAL_MINUTES = 5;

    private static final long REPORT_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(REPORT_INTERVAL_MINUTES);

    /** Per channel: when it last logged a decode failure in full (0 = never since the last reset). */
    private static final Map<String, AtomicLong> LAST_REPORTED_AT = new ConcurrentHashMap<>();

    /** Per channel: failures dropped without a full log line since that last report. */
    private static final Map<String, AtomicLong> SUPPRESSED = new ConcurrentHashMap<>();

    private PayloadDecodeGuard() {
    }

    /**
     * Decodes {@code data} for {@code channelKey}, or returns {@code null} if it cannot be decoded.
     * Callers on a network thread must use this rather than {@link PayloadCodec#decode} directly, and
     * must treat {@code null} as "drop this packet".
     */
    public static <T> T decodeOrDrop(byte[] data, Class<T> type, String channelKey) {
        try {
            return PayloadCodec.decode(data, type);
        } catch (RuntimeException e) {
            report(channelKey, data, e);
            return null;
        }
    }

    /**
     * Clears the per-channel reporting state, emitting a summary for any channel that dropped more
     * payloads than it reported. Called when a client begins a new connection.
     */
    public static void reset() {
        SUPPRESSED.forEach((channelKey, suppressed) -> {
            long count = suppressed.getAndSet(0);
            if (count > 0) {
                LOGGER.info("Dropped {} further undecodable payload(s) on {} since the last report",
                        count, channelKey);
            }
        });
        SUPPRESSED.clear();
        LAST_REPORTED_AT.clear();
    }

    /**
     * Throttles reporting to one full log line per channel per interval; everything in between is counted
     * and logged at DEBUG. The CAS on the timestamp is what makes concurrent decodes (several connections
     * on a server) agree on a single reporter without a lock.
     */
    private static void report(String channelKey, byte[] data, RuntimeException failure) {
        AtomicLong lastReportedAt = LAST_REPORTED_AT.computeIfAbsent(channelKey, key -> new AtomicLong());
        AtomicLong suppressed = SUPPRESSED.computeIfAbsent(channelKey, key -> new AtomicLong());
        long previous = lastReportedAt.get();
        long now = System.currentTimeMillis();
        boolean due = previous == 0L || now - previous >= REPORT_INTERVAL_MILLIS;
        if (!due || !lastReportedAt.compareAndSet(previous, now)) {
            suppressed.incrementAndGet();
            LOGGER.debug("Dropping another undecodable payload on {} ({} bytes)", channelKey, data.length);
            return;
        }
        describe(channelKey, data, failure, suppressed.getAndSet(0));
    }

    /** Logs one dropped payload in full, classified by what the bytes actually look like. */
    private static void describe(String channelKey, byte[] data, RuntimeException failure, long suppressed) {
        Throwable cause = failure.getCause() != null ? failure.getCause() : failure;
        if (looksLegacyFramed(data)) {
            // A length-prefixed gzip stream: the sender speaks the pre-Eunomia framing of this channel.
            // Nothing is broken here except the version pairing, so say so instead of dumping a stack.
            LOGGER.warn("Dropping a payload on {}: its {} bytes are length-prefixed gzip, the wire format used "
                    + "by pre-Eunomia versions of this mod. The other side is running an older, incompatible "
                    + "version - update both sides to the same version. ({})",
                    channelKey, data.length, cause.toString());
        } else if (!looksGzipped(data)) {
            LOGGER.warn("Dropping a payload on {}: its {} bytes are not Eunomia's gzip(json) wire format, so "
                    + "another mod or an incompatible version owns this channel on the other side. ({})",
                    channelKey, data.length, cause.toString());
        } else {
            // Our framing, unreadable contents: this is the case that is probably our own fault.
            LOGGER.error("Dropping a payload on {}: {} bytes are in Eunomia's wire format but could not be "
                    + "decoded. This is either a genuine encoding defect or a hostile payload.",
                    channelKey, data.length, failure);
        }
        if (suppressed > 0) {
            LOGGER.warn("{} further payload(s) on {} were dropped without a log line since the last report.",
                    suppressed, channelKey);
        }
        LOGGER.warn("Further decode failures on {} are counted, not logged, for the next {} minutes.",
                channelKey, REPORT_INTERVAL_MINUTES);
    }

    /** Whether {@code data} starts with the gzip magic number, i.e. is plausibly our wire format. */
    private static boolean looksGzipped(byte[] data) {
        return looksGzippedAt(data, 0);
    }

    /**
     * Whether {@code data} is {@code int32 length + gzip(...)} with a length that matches the remaining
     * bytes - the framing armor-hider (and any other pre-Eunomia consumer) used on these same channels.
     * Checking the length as well as the magic keeps this from claiming a coincidence.
     */
    private static boolean looksLegacyFramed(byte[] data) {
        if (!looksGzippedAt(data, Integer.BYTES)) {
            return false;
        }
        int declaredLength = ((data[0] & 0xFF) << 24) | ((data[1] & 0xFF) << 16)
                | ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
        return declaredLength == data.length - Integer.BYTES;
    }

    private static boolean looksGzippedAt(byte[] data, int offset) {
        return data.length >= offset + 2
                && (data[offset] & 0xFF) == 0x1F
                && (data[offset + 1] & 0xFF) == 0x8B;
    }
}
