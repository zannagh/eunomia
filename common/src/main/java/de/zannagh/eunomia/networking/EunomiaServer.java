package de.zannagh.eunomia.networking;

import de.zannagh.eunomia.networking.packets.EunomiaPacket;
import de.zannagh.eunomia.server.ServerPayloadContext;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public class EunomiaServer {

    public void sendToPlayer(ServerPlayer serverPlayer, EunomiaPacket packet) {

    }

    public static boolean handleC2SPacket(Identifier channel, FriendlyByteBuf data, ServerPayloadContext context) {
        // TODO: legacy function
        return true;
    }
}
