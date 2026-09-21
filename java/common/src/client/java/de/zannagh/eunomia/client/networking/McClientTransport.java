package de.zannagh.eunomia.client.networking;

import de.zannagh.eunomia.Eunomia;
import de.zannagh.eunomia.networking.comms.ClientTransport;
import de.zannagh.eunomia.networking.loader.LoaderNetwork;
import de.zannagh.eunomia.networking.packets.PacketType;
import net.minecraft.client.Minecraft;

//? if >= 1.20.5 {
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
 * Client → server send path over vanilla custom payloads. A send failure (no connection, or the loader
 * refusing an unregistered channel) never propagates - it must not take the client down, since
 * client-authoritative state stays valid regardless - but anything other than "not connected" is logged
 * at WARN, because a silently dropped send is indistinguishable from a working transport.
 */
public final class McClientTransport implements ClientTransport {

    @Override
    public <T> void sendToServer(PacketType<T> type, T data) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            Eunomia.LOGGER.debug("Not connected; dropping serverbound {}", type.channelKey());
            return;
        }
        // The loader veto, before anything touches the wire. See LoaderNetwork.canSend: on NeoForge an
        // unwired channel does not fail softly, it throws inside the netty encoder and drops the connection.
        if (!LoaderNetwork.canSend(type)) {
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
            // NOT "the server does not support this channel" - that is the handshake's business, and this
            // exception never carries that information. It is the LOADER refusing to put a packet on a
            // channel it does not have registered, i.e. a channel declared too late (or not at all) for the
            // loader's registration phase. Silently swallowing it at DEBUG is how a whole dead transport
            // stays invisible, so say what actually happened, at a level someone will see.
            Eunomia.LOGGER.warn(
                    "The mod loader refused to send on channel {} because it is not registered with its "
                            + "networking; the packet was dropped.", type.channelKey(), e);
        } catch (Exception e) {
            Eunomia.LOGGER.warn("Failed to send serverbound {}", type.channelKey(), e);
        }
    }
}
