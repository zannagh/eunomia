//? if >= 1.20.5 {
package de.zannagh.eunomia.mixins.networking;

import de.zannagh.eunomia.Eunomia;
import de.zannagh.eunomia.networking.loader.LoaderNetwork;
import de.zannagh.eunomia.networking.payloads.EunomiaPayloadList;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.List;

/**
 * Injects the serverbound (C2S) payload types into the codec created in
 * {@link ServerboundCustomPayloadPacket}'s static initializer.
 * <p>
 * Targets the <em>caller</em> of {@code CustomPacketPayload.codec()} rather than the method itself,
 * avoiding the "target loaded too early" crash that occurs when another mod (e.g. Vivecraft)
 * classloads {@code CustomPacketPayload} during boot.
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
        if (types instanceof EunomiaPayloadList<CustomPacketPayload.TypeAndCodec<?, ?>>) {
            return types;
        }
        if (types.stream().anyMatch(tac -> LoaderNetwork.isServerboundId(tac.type().id()))) {
            return types;
        }

        EunomiaPayloadList<CustomPacketPayload.TypeAndCodec<?, ?>> modifiedTypes = new EunomiaPayloadList<>(types);
        LoaderNetwork.serverboundEntries().forEach(entry -> {
            modifiedTypes.add(new CustomPacketPayload.TypeAndCodec(entry.type(), entry.codec()));
            Eunomia.LOGGER.info("Injected C2S payload: {}", entry.type().id());
        });
        return modifiedTypes;
    }
}
//?}
