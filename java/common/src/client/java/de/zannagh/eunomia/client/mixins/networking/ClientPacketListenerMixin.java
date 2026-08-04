//? if >= 1.20.5 {
package de.zannagh.eunomia.client.mixins.networking;

import de.zannagh.eunomia.client.networking.ClientConnectionEvents;
import de.zannagh.eunomia.client.networking.McClientContext;
import de.zannagh.eunomia.networking.CommunicationManager;
import de.zannagh.eunomia.networking.payloads.EunomiaPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Routes inbound Eunomia payloads to the {@link CommunicationManager} on the client (1.20.5+), and
 * fires the client-join event once login finishes. Client custom-payload handling already runs on
 * the client thread, so the handler is invoked inline.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin extends ClientCommonPacketListenerImpl {

    protected ClientPacketListenerMixin(Minecraft minecraft, Connection connection, CommonListenerCookie commonListenerCookie) {
        super(minecraft, connection, commonListenerCookie);
    }

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void eunomia$onHandleCustomPayload(CustomPacketPayload payload, CallbackInfo ci) {
        if (!(payload instanceof EunomiaPayload eunomiaPayload)) {
            return;
        }
        ci.cancel();
        String channelKey = eunomiaPayload.type().id().toString();
        McClientContext context = new McClientContext((ClientPacketListener) (Object) this, minecraft);
        CommunicationManager.dispatchClientbound(channelKey, eunomiaPayload.data(), context);
    }

    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void eunomia$onHandleLogin(CallbackInfo ci) {
        ClientConnectionEvents.onClientJoin((ClientPacketListener) (Object) this, minecraft);
    }
}
//?}
