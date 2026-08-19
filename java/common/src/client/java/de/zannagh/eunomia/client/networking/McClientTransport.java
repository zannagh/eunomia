package de.zannagh.eunomia.client.networking;

import de.zannagh.eunomia.Eunomia;
import de.zannagh.eunomia.networking.comms.ClientTransport;
import de.zannagh.eunomia.networking.packets.PacketType;
import net.minecraft.client.Minecraft;

//? if >= 1.20.5 {
import de.zannagh.eunomia.networking.loader.LoaderNetwork;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
//?}
//? if < 1.20.5 {
/*import de.zannagh.eunomia.networking.serialization.PayloadCodec;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
*///?}

/**
 * Client → server send path over vanilla custom payloads. A send failure (the server does not know
 * the channel, or the connection dropped) is swallowed with a debug log: it must never take the
 * client down, since client-authoritative state stays valid regardless.
 */
public final class McClientTransport implements ClientTransport {

    @Override
    public <T> void sendToServer(PacketType<T> type, T data) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            Eunomia.LOGGER.debug("Not connected; dropping serverbound {}", type.channelKey());
            return;
        }
        try {
            //? if >= 1.20.5 {
            connection.send(new ServerboundCustomPayloadPacket(LoaderNetwork.wrap(type, data)));
            //?}
            //? if < 1.20.5 {
            /*Identifier channel = new Identifier(type.namespace(), type.path());
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeBytes(PayloadCodec.encode(data, true));
            connection.send(new ServerboundCustomPayloadPacket(channel, buf));
            *///?}
        } catch (UnsupportedOperationException e) {
            Eunomia.LOGGER.debug("Server does not support channel {}, skipping.", type.channelKey());
        } catch (Exception e) {
            Eunomia.LOGGER.warn("Failed to send serverbound {}", type.channelKey(), e);
        }
    }
}
