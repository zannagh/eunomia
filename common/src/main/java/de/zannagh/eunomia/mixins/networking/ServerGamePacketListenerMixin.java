//? if >= 1.20.5 {
package de.zannagh.eunomia.mixins.networking;

import de.zannagh.eunomia.networking.CommunicationManager;
import de.zannagh.eunomia.networking.loader.McServerContext;
import de.zannagh.eunomia.networking.payloads.EunomiaPayload;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Routes inbound Eunomia custom payloads to the {@link CommunicationManager} on the server. The
 * payload has already been decoded into an {@link EunomiaPayload} by the injected StreamCodec, so
 * this only unwraps it and hands the POJO to the handler - on the server thread, and only after
 * cancelling vanilla's own (unknown-payload) handling.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMixin extends ServerCommonPacketListenerImpl {

    public ServerGamePacketListenerMixin(MinecraftServer minecraftServer, Connection connection, CommonListenerCookie commonListenerCookie) {
        super(minecraftServer, connection, commonListenerCookie);
    }

    @Shadow
    public abstract ServerPlayer getPlayer();

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void eunomia$handleCustomPayload(ServerboundCustomPayloadPacket packet, CallbackInfo callbackInfo) {
        CustomPacketPayload payload = packet.payload();
        if (!(payload instanceof EunomiaPayload eunomiaPayload)) {
            return;
        }
        // It is ours: stop vanilla from treating it as an unknown payload, then run the handler on
        // the server thread so it can safely touch world/server state.
        callbackInfo.cancel();
        String channelKey = eunomiaPayload.type().id().toString();
        ServerPlayer sender = getPlayer();
        server.execute(() -> CommunicationManager.dispatchServerbound(
                channelKey, eunomiaPayload.data(), new McServerContext(sender, server)));
    }
}
//?}
