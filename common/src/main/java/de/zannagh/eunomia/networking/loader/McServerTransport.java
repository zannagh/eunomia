package de.zannagh.eunomia.networking.loader;

import de.zannagh.eunomia.networking.PacketType;
import de.zannagh.eunomia.networking.ServerTransport;
import de.zannagh.eunomia.server.ServerHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

//? if >= 1.20.5 {
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
//?}
//? if < 1.20.5 {
/*import de.zannagh.eunomia.networking.serialization.PayloadCodec;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
*///?}

/**
 * Server → client send path over vanilla custom payloads, resolving players through the
 * {@link ServerHolder}. On 1.20.5+ the POJO is wrapped in an {@link de.zannagh.eunomia.networking.payloads.EunomiaPayload};
 * on 1.20.x it is encoded straight into a {@code FriendlyByteBuf} keyed by channel id.
 */
public final class McServerTransport implements ServerTransport {

    @Override
    public <T> void sendToPlayer(UUID playerId, PacketType<T> type, T data) {
        MinecraftServer server = ServerHolder.get();
        if (server == null) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            send(player, type, data);
        }
    }

    @Override
    public <T> void broadcast(PacketType<T> type, T data) {
        MinecraftServer server = ServerHolder.get();
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            send(player, type, data);
        }
    }

    @Override
    public <T> void broadcastExcept(UUID excludedPlayerId, PacketType<T> type, T data) {
        MinecraftServer server = ServerHolder.get();
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.getUUID().equals(excludedPlayerId)) {
                send(player, type, data);
            }
        }
    }

    static <T> void send(ServerPlayer player, PacketType<T> type, T data) {
        //? if >= 1.20.5 {
        player.connection.send(new ClientboundCustomPayloadPacket(LoaderNetwork.wrap(type, data)));
        //?}
        //? if < 1.20.5 {
        /*Identifier channel = new Identifier(type.namespace(), type.path());
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBytes(PayloadCodec.encode(data, false));
        player.connection.send(new ClientboundCustomPayloadPacket(channel, buf));
        *///?}
    }
}
