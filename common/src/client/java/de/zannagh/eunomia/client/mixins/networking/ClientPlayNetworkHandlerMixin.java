//? if < 1.20.5 {
/*package de.zannagh.eunomia.client.mixins.networking;

import de.zannagh.eunomia.client.networking.ClientConnectionEvents;
import de.zannagh.eunomia.client.networking.McClientContext;
import de.zannagh.eunomia.networking.loader.LoaderNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Client-side dispatch on 1.20.x (pre-CustomPacketPayload): decode the raw FriendlyByteBuf for the
// channel through the loader network and route it. On 1.20.5+ the modern ClientPacketListenerMixin
// does the work and this class is not compiled at all.
@Mixin(ClientPacketListener.class)
public abstract class ClientPlayNetworkHandlerMixin {

    @Final
    @Shadow
    private Minecraft minecraft;

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void eunomia$onHandleCustomPayload(ClientboundCustomPayloadPacket packet, CallbackInfo ci) {
        Identifier channel = packet.getIdentifier();
        FriendlyByteBuf data = packet.getData();
        McClientContext context = new McClientContext((ClientPacketListener) (Object) this, minecraft);
        if (LoaderNetwork.dispatchLegacyClientbound(channel, data, context)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void eunomia$onHandleLogin(CallbackInfo ci) {
        ClientConnectionEvents.onClientJoin((ClientPacketListener) (Object) this, minecraft);
    }
}
*///?}
