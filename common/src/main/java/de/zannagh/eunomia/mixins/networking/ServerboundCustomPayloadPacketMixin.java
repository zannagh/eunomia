//? if >= 1.20.5 {
package de.zannagh.eunomia.mixins.networking;

import de.zannagh.eunomia.Eunomia;
import de.zannagh.eunomia.networking.payloads.EunomiaPayloadList;
import de.zannagh.eunomia.networking.payloads.PayloadRegistry;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.List;

/**
 * Injects C2S (client-to-server) payload types into the codec created in
 * {@link ServerboundCustomPayloadPacket}'s static initializer.
 * <p>
 * Targets the <em>caller</em> of {@code CustomPacketPayload.codec()} rather than
 * the method itself, avoiding the "target loaded too early" crash that occurs when
 * another mod (e.g. Vivecraft) classloads {@code CustomPacketPayload} during boot.
 */
@Mixin(ServerboundCustomPayloadPacket.class)
public class ServerboundCustomPayloadPacketMixin {

    @SuppressWarnings({"unchecked", "rawtypes"})
    @ModifyArg(
            method = "<clinit>",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;codec(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$FallbackProvider;Ljava/util/List;)Lnet/minecraft/network/codec/StreamCodec;"),
            index = 1
    )
    private static List<CustomPacketPayload.TypeAndCodec<?, ?>> injectC2SPayloads(
            List<CustomPacketPayload.TypeAndCodec<?, ?>> types) {
        if (types instanceof EunomiaPayloadList<CustomPacketPayload.TypeAndCodec<?,?>>) {
            return types;
        }

        var c2sPackets = PayloadRegistry.getAllC2S();
        if (types.stream().anyMatch(tac -> c2sPackets.containsKey(tac.type().id()))) {
            return types;
        }

        EunomiaPayloadList<CustomPacketPayload.TypeAndCodec<?, ?>> modifiedTypes = new EunomiaPayloadList<>(types);
        Eunomia.LOGGER.info("Injecting C2S payloads into ServerboundCustomPayloadPacket codec. Current types: {}, adding: {}",
                types.size(), c2sPackets.size());
        c2sPackets.forEach((id, entry) -> {
            modifiedTypes.add(new CustomPacketPayload.TypeAndCodec(entry.type(), entry.codec()));
            Eunomia.LOGGER.info("Injected C2S payload: {}", id);
        });
        return modifiedTypes;
    }
}
//?}
