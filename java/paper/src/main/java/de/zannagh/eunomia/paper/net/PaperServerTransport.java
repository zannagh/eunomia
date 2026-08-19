package de.zannagh.eunomia.paper.net;

import de.zannagh.eunomia.networking.packets.PacketType;
import de.zannagh.eunomia.networking.comms.ServerTransport;
import de.zannagh.eunomia.networking.serialization.PayloadCodec;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.logging.Level;

/**
 * Server → client send path over Bukkit plugin messaging. Encoding runs through the shared
 * {@link PayloadCodec}, so the bytes are identical to what the loader's StreamCodec produces and the
 * modded client decodes them with no special-casing. A send to a channel the connection is not
 * listening on is skipped (Bukkit would drop it silently anyway), which is why joins force-subscribe.
 */
public final class PaperServerTransport implements ServerTransport {

    private final Plugin plugin;

    public PaperServerTransport(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public <T> void sendToPlayer(UUID playerId, PacketType<T> type, T data) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            send(player, type, data);
        }
    }

    @Override
    public <T> void broadcast(PacketType<T> type, T data) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            send(player, type, data);
        }
    }

    @Override
    public <T> void broadcastExcept(UUID excludedPlayerId, PacketType<T> type, T data) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!player.getUniqueId().equals(excludedPlayerId)) {
                send(player, type, data);
            }
        }
    }

    /** Encodes and sends a clientbound payload to one player. Public so {@code PaperServerContext.reply} reuses it. */
    public void send(Player player, PacketType<?> type, Object data) {
        String channel = type.channelKey();
        if (!player.getListeningPluginChannels().contains(channel)) {
            return;
        }
        byte[] encoded;
        try {
            encoded = PayloadCodec.encode(data, false);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to encode a Eunomia payload on " + channel, e);
            return;
        }
        try {
            player.sendPluginMessage(plugin, channel, encoded);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to send " + channel + " to " + player.getUniqueId(), e);
        }
    }
}
