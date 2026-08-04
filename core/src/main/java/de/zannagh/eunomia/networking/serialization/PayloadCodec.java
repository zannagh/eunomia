package de.zannagh.eunomia.networking.serialization;

import de.zannagh.eunomia.networking.PacketDirection;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * The one wire format shared by every platform: a payload is {@code gzip(utf8(json(value)))} and
 * nothing else. There is no framing here - the transport (a Minecraft custom-payload packet, a
 * Bukkit plugin message, an HTTP body) already delimits the byte array, so both the loader's
 * StreamCodec adapter and the Paper plugin call straight into these two methods and interoperate.
 * <p>
 * All the size and decompression-bomb guards live here so no platform can forget them.
 */
public final class PayloadCodec {

    private PayloadCodec() {
    }

    /**
     * Vanilla's clientbound custom-payload ceiling ({@code ClientboundCustomPayloadPacket.MAX_PAYLOAD_SIZE},
     * 1 MiB) - the upper sanity bound for any payload we encode or decode.
     */
    public static final int MAX_PAYLOAD_BYTES = 1048576;

    /**
     * Vanilla's <em>serverbound</em> ceiling ({@code ServerboundCustomPayloadPacket.MAX_PAYLOAD_SIZE}) is
     * far tighter at 32767, and a vanilla server decodes unknown payloads via {@code DiscardedPayload},
     * which throws and disconnects the client for anything larger. Serverbound packets are held to this
     * limit; a little headroom is kept for the length prefix and framing.
     */
    public static final int MAX_SERVERBOUND_PAYLOAD_BYTES = 32767 - 256;

    /**
     * Ceiling on the <em>inflated</em> size of a decoded payload. gzip routinely hits ~38x on this kind
     * of data and a crafted stream reaches ~1000x, so a 1 MiB payload could otherwise inflate to
     * hundreds of megabytes during parsing. 64 MiB bounds memory use while leaving comfortable margin
     * for legitimately huge server broadcasts.
     */
    public static final int MAX_DECOMPRESSED_BYTES = 64 * 1024 * 1024;

    /**
     * Encodes {@code value} to {@code gzip(json)}. When {@code serverbound} the tighter 32 KiB ceiling
     * is enforced so an oversized payload is refused here rather than kicking the client on a vanilla
     * server that cannot decode it.
     */
    public static byte[] encode(Object value, boolean serverbound) {
        try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            try (GZIPOutputStream gzipStream = new GZIPOutputStream(byteStream);
                 OutputStreamWriter writer = new OutputStreamWriter(gzipStream, StandardCharsets.UTF_8)) {
                NetworkSerializer.gson().toJson(value, writer);
            }

            byte[] compressed = byteStream.toByteArray();
            int limit = serverbound ? MAX_SERVERBOUND_PAYLOAD_BYTES : MAX_PAYLOAD_BYTES;
            if (compressed.length > limit) {
                throw new IllegalStateException("Refusing to encode an oversized payload: "
                        + compressed.length + " bytes exceeds the " + limit + " byte limit");
            }
            return compressed;
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode compressed JSON payload", e);
        }
    }

    /** Convenience overload that derives the size ceiling from the packet direction. */
    public static byte[] encode(Object value, PacketDirection direction) {
        return encode(value, direction.allowsServerbound());
    }

    /**
     * Decodes {@code gzip(json)} bytes into {@code type}, bounding the compressed length up front and
     * the inflated length during parsing. If the decoded object is {@link NetworkHealable} it is healed
     * before being returned, so data off the wire gets the same repair pass as data read from disk.
     */
    public static <T> T decode(byte[] data, Class<T> type) {
        if (data.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Rejecting a payload of " + data.length
                    + " bytes (exceeds the " + MAX_PAYLOAD_BYTES + " byte ceiling)");
        }
        try (ByteArrayInputStream byteStream = new ByteArrayInputStream(data);
             GZIPInputStream gzipStream = new GZIPInputStream(byteStream);
             InputStream boundedStream = new SizeLimitedInputStream(gzipStream, MAX_DECOMPRESSED_BYTES);
             Reader reader = new InputStreamReader(boundedStream, StandardCharsets.UTF_8)) {
            T decoded = NetworkSerializer.gson().fromJson(reader, type);
            if (decoded instanceof NetworkHealable healable) {
                healable.heal();
            }
            return decoded;
        } catch (IOException e) {
            throw new RuntimeException("Failed to decode compressed JSON payload", e);
        }
    }
}
