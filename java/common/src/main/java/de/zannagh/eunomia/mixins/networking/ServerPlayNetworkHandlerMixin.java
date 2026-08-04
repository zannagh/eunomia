package de.zannagh.eunomia.mixins.networking;

import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if < 1.20.5 {
/*import de.zannagh.eunomia.networking.loader.LoaderNetwork;
import de.zannagh.eunomia.networking.loader.McServerContext;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Final;
*///? }

/**
 * Serverbound dispatch on 1.20.x (pre-{@code CustomPacketPayload}): decodes the raw
 * {@code FriendlyByteBuf} for the channel through the loader network and routes it. On 1.20.5+ the
 * modern {@link ServerGamePacketListenerMixin} does the work and this injection is inert.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayNetworkHandlerMixin {

    @Shadow
    public ServerPlayer player;

    //? if < 1.20.5 {
    /*@Final
    @Shadow
    private MinecraftServer server;
    *///? }

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void eunomia$handleLegacyCustomPayload(ServerboundCustomPayloadPacket packet, CallbackInfo callbackInfo) {
        //? if < 1.20.5 {
        /*Identifier channel = packet.getIdentifier();
        FriendlyByteBuf data = packet.getData();
        if (LoaderNetwork.dispatchLegacyServerbound(channel, data, new McServerContext(player, server))) {
            callbackInfo.cancel();
        }
        *///? }
    }
}
