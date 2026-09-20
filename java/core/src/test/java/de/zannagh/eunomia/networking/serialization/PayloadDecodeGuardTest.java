package de.zannagh.eunomia.networking.serialization;

import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pins the behaviour that keeps a version mismatch from killing a connection: bytes we cannot decode are
 * dropped (null), never thrown, because every caller of {@link PayloadDecodeGuard} decodes inside a
 * network pipeline where a throw becomes a {@code DecoderException} and a disconnect.
 */
class PayloadDecodeGuardTest {

    private static final String CHANNEL = "de.zannagh.armorhider:settings_s2c_packet";

    public static class Sample {
        public String name;

        public Sample() {
        }

        public Sample(String name) {
            this.name = name;
        }
    }

    @BeforeEach
    void resetState() {
        NetworkSerializer.setGson(new Gson());
        PayloadDecodeGuard.reset();
    }

    @Test
    void decodesAWellFormedPayload() {
        byte[] wire = PayloadCodec.encode(new Sample("hello"), false);

        Sample decoded = PayloadDecodeGuard.decodeOrDrop(wire, Sample.class, CHANNEL);

        assertNotNull(decoded);
        assertEquals("hello", decoded.name);
    }

    /**
     * The reported crash: armor-hider before 0.14.0 wrote {@code int32 length + gzip(json)} on these exact
     * channels, so a new client joining an old server reads a payload whose first bytes are a length
     * prefix rather than the gzip magic. That must be dropped, not thrown.
     */
    @Test
    void dropsLegacyLengthPrefixedPayloadInsteadOfThrowing() {
        byte[] legacy = legacyFramed(new Sample("hello"));

        assertNull(PayloadDecodeGuard.decodeOrDrop(legacy, Sample.class, CHANNEL));
    }

    @Test
    void dropsCompletelyUnrelatedBytes() {
        byte[] garbage = "this is not a payload at all".getBytes(StandardCharsets.UTF_8);

        assertNull(PayloadDecodeGuard.decodeOrDrop(garbage, Sample.class, CHANNEL));
    }

    /** Gzip that inflates to something that is not JSON: our framing, unreadable contents. Still dropped. */
    @Test
    void dropsGzippedNonJson() throws IOException {
        byte[] wire = gzip("<<<not json>>>");

        assertNull(PayloadDecodeGuard.decodeOrDrop(wire, Sample.class, CHANNEL));
    }

    /** These arrive per packet, so the repeat path (the suppressed-count branch) must not throw either. */
    @Test
    void repeatedFailuresStayQuietAndKeepDropping() {
        byte[] legacy = legacyFramed(new Sample("hello"));

        for (int i = 0; i < 50; i++) {
            assertNull(PayloadDecodeGuard.decodeOrDrop(legacy, Sample.class, CHANNEL));
        }
        PayloadDecodeGuard.reset();
    }

    /** The guard is the only lenient door: a direct decode still throws, so our own bugs stay loud. */
    @Test
    void plainDecodeStillThrows() {
        byte[] legacy = legacyFramed(new Sample("hello"));

        assertThrows(RuntimeException.class, () -> PayloadCodec.decode(legacy, Sample.class));
    }

    /** Rebuilds the pre-Eunomia armor-hider framing: a big-endian length prefix ahead of the gzip stream. */
    private static byte[] legacyFramed(Object value) {
        try {
            byte[] compressed = gzip(new Gson().toJson(value));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write((compressed.length >>> 24) & 0xFF);
            out.write((compressed.length >>> 16) & 0xFF);
            out.write((compressed.length >>> 8) & 0xFF);
            out.write(compressed.length & 0xFF);
            out.write(compressed);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] gzip(String text) throws IOException {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipStream = new GZIPOutputStream(byteStream);
             OutputStreamWriter writer = new OutputStreamWriter(gzipStream, StandardCharsets.UTF_8)) {
            writer.write(text);
        }
        return byteStream.toByteArray();
    }
}
