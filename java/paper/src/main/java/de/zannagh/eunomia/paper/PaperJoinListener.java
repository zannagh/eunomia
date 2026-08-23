package de.zannagh.eunomia.paper;

import de.zannagh.eunomia.keyed.ReplicatedStores;
import de.zannagh.eunomia.networking.examples.ExamplePackets;
import de.zannagh.eunomia.networking.examples.PermissionPayload;
import de.zannagh.eunomia.paper.net.ChannelSubscriber;
import de.zannagh.eunomia.paper.net.PaperServerTransport;
import de.zannagh.eunomia.paper.perm.PermissionResolver;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * On join: force-subscribe the connection to our clientbound channels (Eunomia clients never send
 * {@code minecraft:register}), then push the example PERMISSION packet - the Paper-side equivalent of
 * the loader's join handshake, resolving the permission level through {@link PermissionResolver}
 * (op -> superperms -> LuckPerms) rather than from {@code MinecraftServer}.
 */
public final class PaperJoinListener implements Listener {

    private final ChannelSubscriber subscriber;
    private final PaperServerTransport transport;
    private final PermissionResolver permissions;

    public PaperJoinListener(ChannelSubscriber subscriber, PaperServerTransport transport, PermissionResolver permissions) {
        this.subscriber = subscriber;
        this.transport = transport;
        this.permissions = permissions;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        subscriber.subscribe(player);
        int level = permissions.getPermissionLevel(player);
        transport.send(player, ExamplePackets.PERMISSION, new PermissionPayload(level));
        // Dump every replicated store to the newcomer (subscribed above, so the sends land).
        ReplicatedStores.pushAllTo(player.getUniqueId());
    }
}
