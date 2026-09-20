//? if >= 1.20.5 {
package de.zannagh.eunomia.networking.payloads;

import de.zannagh.eunomia.networking.packets.PacketType;
import de.zannagh.eunomia.networking.serialization.PayloadCodec;
import de.zannagh.eunomia.networking.serialization.PayloadDecodeGuard;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * The single {@link CustomPacketPayload} that carries every Eunomia packet on 1.20.5+. A mod's
 * payload is a plain POJO; this wrapper is what actually flows through vanilla's network stack, so
 * mods never see {@code CustomPacketPayload}/{@code StreamCodec} at all. One {@link Type} instance is
 * minted per channel (its id is the packet's {@code namespace:path}); the wrapper carries that type
 * plus the POJO.
 * <p>
 * The {@link #codecFor codec} writes {@code gzip(json)} through the shared {@link PayloadCodec}, so a
 * payload put on the wire by a Fabric/NeoForge client is byte-for-byte what the Paper plugin decodes.
 */
public final class EunomiaPayload implements CustomPacketPayload {

    private final CustomPacketPayload.Type<EunomiaPayload> type;
    private final Object data;

    public EunomiaPayload(CustomPacketPayload.Type<EunomiaPayload> type, Object data) {
        this.type = type;
        this.data = data;
    }

    /**
     * The decoded POJO this wrapper carries, or {@code null} when the inbound bytes could not be decoded
     * (see {@link #codecFor}). A {@code null} here means "drop this packet", never "empty payload".
     */
    public Object data() {
        return data;
    }

    @Override
    public CustomPacketPayload.@NonNull Type<? extends CustomPacketPayload> type() {
        return type;
    }

    /** Mints the per-channel payload type whose id is {@code packetType}'s {@code namespace:path}. */
    public static CustomPacketPayload.Type<EunomiaPayload> typeFor(PacketType<?> packetType) {
        return new CustomPacketPayload.Type<>(
                Identifier.fromNamespaceAndPath(packetType.namespace(), packetType.path()));
    }

    /**
     * Builds the StreamCodec vanilla uses for this channel. Encode/decode delegate straight to the
     * shared {@link PayloadCodec}; the payload packet is self-delimiting, so decode consumes the whole
     * readable buffer rather than a manual length prefix - keeping the format identical to the
     * byte-array path the Paper plugin uses.
     * <p>
     * Decode goes through {@link PayloadDecodeGuard} rather than {@link PayloadCodec} directly: this runs
     * inside Netty's packet decoder, so anything thrown here becomes a {@code DecoderException} and drops
     * the connection. Bytes we cannot read (an older, differently-framed version of the mod on the other
     * end) yield a payload with {@code null} {@link #data()}, which the packet-listener mixins discard.
     */
    public static StreamCodec<ByteBuf, EunomiaPayload> codecFor(
            PacketType<?> packetType, CustomPacketPayload.Type<EunomiaPayload> type) {
        boolean serverbound = packetType.direction().allowsServerbound();
        Class<?> payloadClass = packetType.payloadClass();
        return StreamCodec.of(
                (buf, payload) -> buf.writeBytes(PayloadCodec.encode(payload.data(), serverbound)),
                (buf) -> {
                    byte[] bytes = new byte[buf.readableBytes()];
                    buf.readBytes(bytes);
                    return new EunomiaPayload(
                            type, PayloadDecodeGuard.decodeOrDrop(bytes, payloadClass, type.id().toString()));
                });
    }
}
//?}
