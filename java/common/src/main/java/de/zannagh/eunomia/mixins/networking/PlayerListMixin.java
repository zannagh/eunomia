package de.zannagh.eunomia.mixins.networking;

import de.zannagh.eunomia.server.ServerConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fires the server-side player-disconnect event. Before this the loader side had no such hook at all,
 * and everything keyed by player on the server - the clientbound capability gate's map, the join
 * de-duplication map in {@link ServerConnectionEvents} - gained an entry per join and never lost one.
 *
 * <p><b>Why {@code PlayerList.remove} and not the packet listener.</b> The obvious target,
 * {@code ServerGamePacketListenerImpl.onDisconnect}, does exist on every supported version - but its
 * descriptor does not: it takes a {@code Component} on 1.20.1 and a {@code DisconnectionDetails} from
 * 1.21.1 on, so it can only be named without one. {@code PlayerList.remove(ServerPlayer)} carries the
 * identical name <em>and</em> descriptor on all eleven (verified by {@code javap} on the mapped
 * {@code minecraft-common} jar for each), so the target can be pinned exactly instead of matched by a
 * bare name that a future overload would make ambiguous.
 *
 * <p>Be warned if you go looking: the intermediary mappings are a <em>false negative</em> here. They
 * list no {@code onDisconnect} on {@code class_3244} for 1.20.1-1.21.11, because an override that
 * inherits the {@code PacketListener} interface method's name gets no entry of its own - the method is
 * there in the bytecode regardless. Only the compiled class is evidence for a mixin target; the mixin
 * annotation processor is no help either, since this build emits no refmap and validates no targets.</p>
 *
 * <p>It is also the better event on its merits. It is the single funnel every way of leaving converges
 * on - {@code onDisconnect} itself calls straight into it (directly on 1.20.1, via
 * {@code removePlayerFromWorld} on 1.21.11+, both confirmed in the bytecode), and the shutdown sweep
 * kicks each connection down that same path rather than bypassing it. And it is deliberately
 * <em>not</em> called on respawn or dimension change, both of which go through
 * {@code ServerLevel.removePlayerImmediately} instead. That last part matters: forgetting a live
 * player's recorded capability would leave them parked, then timed out, then silently withheld from
 * for the rest of their session.</p>
 *
 * <p>Injected at HEAD, because all this needs is the id, and the id is valid for the whole method.</p>
 */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {

    @Inject(method = "remove(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("HEAD"))
    private void eunomia$onPlayerDisconnect(ServerPlayer player, CallbackInfo callbackInfo) {
        ServerConnectionEvents.onPlayerDisconnect(player.getUUID());
    }
}
