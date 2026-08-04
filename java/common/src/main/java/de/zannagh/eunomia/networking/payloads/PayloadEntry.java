//? if >= 1.20.5 {
package de.zannagh.eunomia.networking.payloads;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A registered channel's vanilla type + codec, ready to be injected into the custom-payload codec
 * lists by the {@code Serverbound}/{@code ClientboundCustomPayloadPacketMixin}s.
 */
public record PayloadEntry(
        CustomPacketPayload.Type<EunomiaPayload> type,
        StreamCodec<? super ByteBuf, EunomiaPayload> codec) {
}
//?}
