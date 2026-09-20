//? if >= 1.20.5 {
package de.zannagh.eunomia.networking.payloads;

import de.zannagh.eunomia.networking.packets.PacketType;
import de.zannagh.eunomia.networking.serialization.PayloadCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The end of the reported crash chain: this codec runs inside Netty's packet decoder, so a throw here
 * becomes {@code DecoderException: Failed to decode packet 'clientbound/minecraft:custom_payload'} and
 * the connection is dropped. A payload we cannot read must therefore come back as a payload carrying
 * {@code null} data (which the packet-listener mixins discard) rather than as an exception.
 */
class EunomiaPayloadCodecTest {

    public static class Sample {
        public String name;

        public Sample() {
        }

        public Sample(String name) {
            this.name = name;
        }
    }

    private static final PacketType<Sample> TYPE =
            PacketType.clientbound("de.zannagh.armorhider", "settings_s2c_packet", Sample.class);

    @Test
    void roundTripsThroughTheWireFormat() {
        StreamCodec<ByteBuf, EunomiaPayload> codec = codec();
        ByteBuf buf = Unpooled.buffer();

        codec.encode(buf, new EunomiaPayload(EunomiaPayload.typeFor(TYPE), new Sample("hello")));
        EunomiaPayload decoded = codec.decode(buf);

        assertThat(decoded.data()).isInstanceOf(Sample.class);
        assertThat(((Sample) decoded.data()).name).isEqualTo("hello");
    }

    /**
     * Pre-0.14.0 armor-hider wrote {@code int32 length + gzip(json)} on this very channel, so an older
     * server's broadcast reaches a current client as bytes whose first four are a length prefix.
     */
    @Test
    void dropsAPreEunomiaLengthPrefixedPayloadWithoutThrowing() {
        StreamCodec<ByteBuf, EunomiaPayload> codec = codec();
        ByteBuf buf = Unpooled.wrappedBuffer(legacyFramed(new Sample("hello")));

        assertThatCode(() -> assertThat(codec.decode(buf).data()).isNull()).doesNotThrowAnyException();
    }

    @Test
    void dropsUnrelatedBytesWithoutThrowing() {
        StreamCodec<ByteBuf, EunomiaPayload> codec = codec();
        ByteBuf buf = Unpooled.wrappedBuffer("not a payload".getBytes(StandardCharsets.UTF_8));

        assertThatCode(() -> assertThat(codec.decode(buf).data()).isNull()).doesNotThrowAnyException();
    }

    private static StreamCodec<ByteBuf, EunomiaPayload> codec() {
        CustomPacketPayload.Type<EunomiaPayload> type = EunomiaPayload.typeFor(TYPE);
        return EunomiaPayload.codecFor(TYPE, type);
    }

    private static byte[] legacyFramed(Object value) {
        byte[] compressed = gzipJson(value);
        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(compressed.length);
        buf.writeBytes(compressed);
        byte[] framed = new byte[buf.readableBytes()];
        buf.readBytes(framed);
        return framed;
    }

    /** Reuses our own encoder for the gzip half - only the length prefix differs from the new format. */
    private static byte[] gzipJson(Object value) {
        return PayloadCodec.encode(value, false);
    }

}
//?}
