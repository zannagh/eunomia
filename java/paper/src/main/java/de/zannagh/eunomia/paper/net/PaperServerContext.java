package de.zannagh.eunomia.paper.net;

import de.zannagh.eunomia.networking.PacketType;
import de.zannagh.eunomia.networking.ServerContext;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * The server-side context handed to a serverbound handler on Paper. Exposes the Bukkit {@code Player}
 * for handlers that need it; {@link #reply} sends straight back over plugin messaging.
 */
public final class PaperServerContext implements ServerContext {

    private final Player player;
    private final PaperServerTransport transport;

    public PaperServerContext(Player player, PaperServerTransport transport) {
        this.player = player;
        this.transport = transport;
    }

    public Player player() {
        return player;
    }

    @Override
    public UUID senderId() {
        return player.getUniqueId();
    }

    @Override
    public String senderName() {
        return player.getName();
    }

    @Override
    public <T> void reply(PacketType<T> type, T data) {
        transport.send(player, type, data);
    }
}
