package de.zannagh.eunomia.mixins.networking;

import de.zannagh.eunomia.server.ServerConnectionEvents;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fires both server-side connection events. {@code PlayerList} is the right class for the pair because
 * it is where a player actually enters and leaves the server's view of the world - everything keyed by
 * player on the server (the clientbound capability gate's map, the join de-duplication map in
 * {@link ServerConnectionEvents}) is gained and lost here and nowhere else.
 *
 * <p><b>Why {@code PlayerList.placeNewPlayer} and not the login listener.</b> The join event used to be
 * raised from {@code ServerLoginPacketListenerImpl}, and on 1.20.1 that was correct: {@code
 * handleAcceptedLogin} calls {@code placeNewPlayer} itself, so at its tail the player really was in the
 * list. From 1.20.5 on it is not. {@code finishLoginAndWaitForClient} is two lines - set state to
 * {@code PROTOCOL_SWITCHING}, send {@code ClientboundLoginFinishedPacket} - and the player only reaches
 * {@code playersByUUID} after the whole CONFIGURATION phase has run: registry sync, the resource-pack
 * download, the client-information reply, the code-of-conduct task on 26.x, {@code PrepareSpawnTask}'s
 * radius-3 chunk load, and every mod-loader handshake queued alongside them. That gap is client-driven
 * and unbounded - Mojang's own 30-second login watchdog stops ticking at the handoff and configuration
 * has no timeout at all, only keepalives.
 *
 * <p>The old hook papered over this by polling {@code getPlayerList().getPlayer(id)} on a
 * {@code ForkJoinPool.commonPool} thread behind an exponential backoff. On a memory connection the poll
 * wins in a few hundred milliseconds, which is why every local and CI run was green; on a real server
 * with a large modpack it loses, and a lost poll silently skipped every join handler - no config push,
 * no permission level, nothing, indistinguishable to the player from a server without the mod. See
 * armor-hider issue #375.</p>
 *
 * <p><b>Injected at TAIL</b>, which is the first point where the player is in {@code players}, in
 * {@code playersByUUID}, added to its level and holding an inventory menu, and where the client has
 * already had {@code ClientboundLoginPacket} and the initial teleport - so anything a handler sends
 * lands. {@code ServerGamePacketListenerImpl}'s construction, some fifty lines earlier in the same
 * method, is <em>not</em> a substitute: the list insert has not happened yet there.</p>
 *
 * <p>The target is split by arity because 1.20.1 predates {@code CommonListenerCookie}. Both descriptors
 * were verified by {@code javap} on the mapped {@code minecraft-common} jar for all eleven supported
 * versions; there is exactly one {@code placeNewPlayer} overload on each, and the 3-arg descriptor is
 * byte-identical from 1.21.1 through 26.3.</p>
 *
 * <p>{@code remove} is injected at HEAD, because all that one needs is the id, and the id is valid for
 * the whole method. It is the single funnel every way of leaving converges on - {@code onDisconnect}
 * calls straight into it (directly on 1.20.1, via {@code removePlayerFromWorld} on 1.21.11+, both
 * confirmed in the bytecode), and the shutdown sweep kicks each connection down that same path rather
 * than bypassing it. It is deliberately <em>not</em> called on respawn or dimension change, both of
 * which go through {@code ServerLevel.removePlayerImmediately} instead. That last part matters:
 * forgetting a live player's recorded capability would leave them parked, then timed out, then silently
 * withheld from for the rest of their session.</p>
 *
 * <p>Be warned if you go looking at the alternative disconnect target: the intermediary mappings are a
 * <em>false negative</em> for {@code ServerGamePacketListenerImpl.onDisconnect}. They list no such
 * method on {@code class_3244} for 1.20.1-1.21.11, because an override that inherits the
 * {@code PacketListener} interface method's name gets no entry of its own - the method is there in the
 * bytecode regardless. Only the compiled class is evidence for a mixin target; the mixin annotation
 * processor is no help either, since this build emits no refmap and validates no targets.</p>
 */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {

    @Shadow
    @Final
    private MinecraftServer server;

    //? if >= 1.20.2 {
    @Inject(
            method = "placeNewPlayer(Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/network/CommonListenerCookie;)V",
            at = @At("TAIL"))
    private void eunomia$onPlayerJoin(
            Connection connection,
            ServerPlayer player,
            net.minecraft.server.network.CommonListenerCookie cookie,
            CallbackInfo callbackInfo) {
        ServerConnectionEvents.onPlayerJoin(player, server);
    }
    //?}

    //? if < 1.20.2 {
    /*@Inject(
            method = "placeNewPlayer(Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At("TAIL"))
    private void eunomia$onPlayerJoin(
            Connection connection,
            ServerPlayer player,
            CallbackInfo callbackInfo) {
        ServerConnectionEvents.onPlayerJoin(player, server);
    }
    *///?}

    @Inject(method = "remove(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("HEAD"))
    private void eunomia$onPlayerDisconnect(ServerPlayer player, CallbackInfo callbackInfo) {
        ServerConnectionEvents.onPlayerDisconnect(player.getUUID());
    }
}
