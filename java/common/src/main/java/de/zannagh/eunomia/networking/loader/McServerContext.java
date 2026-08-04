package de.zannagh.eunomia.networking.loader;

import de.zannagh.eunomia.networking.PacketType;
import de.zannagh.eunomia.networking.ServerContext;
import de.zannagh.eunomia.utils.PlayerNameUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * The server-side context handed to a serverbound handler. Implements the platform-neutral
 * {@link ServerContext} and additionally exposes the concrete {@code ServerPlayer}/{@code
 * MinecraftServer} for handlers that need them - cast to this type to reach them.
 */
public final class McServerContext implements ServerContext {

    private final ServerPlayer player;
    private final MinecraftServer server;

    public McServerContext(ServerPlayer player, MinecraftServer server) {
        this.player = player;
        this.server = server;
    }

    public ServerPlayer player() {
        return player;
    }

    public MinecraftServer server() {
        return server;
    }

    @Override
    public UUID senderId() {
        return player.getUUID();
    }

    @Override
    public String senderName() {
        return PlayerNameUtil.getPlayerName(player);
    }

    @Override
    public <T> void reply(PacketType<T> type, T data) {
        McServerTransport.send(player, type, data);
    }
}
