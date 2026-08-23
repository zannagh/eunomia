//? if < 1.20.5 {
/*package de.zannagh.eunomia.client.mixins.networking;

import de.zannagh.eunomia.client.networking.ClientConnectionEvents;
import de.zannagh.eunomia.client.networking.McClientContext;
import de.zannagh.eunomia.networking.comms.CommunicationManager;
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

    // Fire the client-join event once login finishes, after wiping every scrap of the previous connection's
    // networking state. The wipe has to happen here, ahead of the listener chain, rather than inside the
    // capability probe: the probe is itself just another join listener, so a listener registered before it
    // would have its gated join-time send parked and then thrown away by the probe's reset. See
    // CommunicationManager.beginClientConnection() for the full account.
    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void eunomia$onHandleLogin(CallbackInfo ci) {
        CommunicationManager.beginClientConnection();
        ClientConnectionEvents.onClientJoin((ClientPacketListener) (Object) this, minecraft);
    }

    // Fire the client-disconnect event when the play connection is torn down. This mirrors the 1.20.5+
    // ClientPacketListenerMixin, and its absence here was not a cosmetic gap: with no disconnect on this
    // branch nothing reset the capability view, nothing restored the Minecraft transport, and - worst - the
    // external relay client started for the server you just left kept its socket and went on reconnecting
    // with exponential backoff for the rest of the game session.
    // close() is the right target on 1.20.1 too: it is declared directly on ClientPacketListener there (the
    // ClientCommonPacketListenerImpl split only arrives in 1.20.5) and Minecraft.clearLevel(Screen) calls it
    // on every disconnect path. Verified with javap against the loom-mapped 1.20.1 client jar.
    @Inject(method = "close", at = @At("TAIL"))
    private void eunomia$onClose(CallbackInfo ci) {
        ClientConnectionEvents.onClientDisconnect(minecraft);
    }
}
*///?}
