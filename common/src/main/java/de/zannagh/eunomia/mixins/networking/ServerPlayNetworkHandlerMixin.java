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
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
//? }

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayNetworkHandlerMixin {

    @Shadow
    public ServerPlayer player;

    //? if < 1.20.5 {
    /*
    @Final
    @Shadow
    private MinecraftServer server;
    *///? }

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void handleCustomPayloadReceivedAsync(ServerboundCustomPayloadPacket packet, CallbackInfo callbackInfo) {
        //? if < 1.20.5 {
        /*Identifier channel = packet.getIdentifier();
        FriendlyByteBuf data = packet.getData();

        var context = new ServerPayloadContext(player, server);

        if (Eunomia.SERVER.handleC2SPacket(channel, data, context)) {
            callbackInfo.cancel();
        }
        *///? }
    }
}
