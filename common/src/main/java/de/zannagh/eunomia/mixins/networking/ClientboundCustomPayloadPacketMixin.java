//? if >= 1.20.5 {
package de.zannagh.eunomia.mixins.networking;

import de.zannagh.eunomia.Eunomia;
import de.zannagh.eunomia.networking.payloads.EunomiaPayloadList;
import de.zannagh.eunomia.networking.payloads.PayloadRegistry;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.List;

/**
 * Injects S2C (server-to-client) payload types into the codec created in
 * {@link ClientboundCustomPayloadPacket}'s static initializer.
 * <p>
 * Targets the <em>caller</em> of {@code CustomPacketPayload.codec()} rather than
 * the method itself, avoiding the "target loaded too early" crash that occurs when
 * another mod (e.g. Vivecraft) classloads {@code CustomPacketPayload} during boot.
 */
@Mixin(ClientboundCustomPayloadPacket.class)
public class ClientboundCustomPayloadPacketMixin {

    @SuppressWarnings({"unchecked", "rawtypes"})
    @ModifyArg(
            method = "<clinit>",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;codec(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$FallbackProvider;Ljava/util/List;)Lnet/minecraft/network/codec/StreamCodec;"),
            index = 1
    )
    private static List<CustomPacketPayload.TypeAndCodec<?, ?>> injectS2CPayloads(
            List<CustomPacketPayload.TypeAndCodec<?, ?>> types) {
        if (types instanceof EunomiaPayloadList<CustomPacketPayload.TypeAndCodec<?,?>>) return types;

        var s2cPackets = PayloadRegistry.getAllS2C();
        if (types.stream().anyMatch(tac -> s2cPackets.containsKey(tac.type().id()))) {
            return types;
        }

        EunomiaPayloadList<CustomPacketPayload.TypeAndCodec<?, ?>> modifiedTypes = new EunomiaPayloadList<>(types);
        Eunomia.LOGGER.info("Injecting S2C payloads into ClientboundCustomPayloadPacket codec. Current types: {}, adding: {}",
                types.size(), s2cPackets.size());
        s2cPackets.forEach((id, entry) -> {
            modifiedTypes.add(new CustomPacketPayload.TypeAndCodec(entry.type(), entry.codec()));
            Eunomia.LOGGER.info("Injected S2C payload: {}", id);
        });
        return modifiedTypes;
    }
}
//?}
