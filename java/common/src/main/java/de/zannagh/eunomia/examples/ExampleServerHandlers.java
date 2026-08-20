package de.zannagh.eunomia.examples;

import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.examples.ExampleHandlers;
import de.zannagh.eunomia.networking.examples.ExamplePackets;
import de.zannagh.eunomia.networking.examples.ExampleReplication;
import de.zannagh.eunomia.networking.examples.PermissionPayload;
import de.zannagh.eunomia.server.ServerConnectionEventConsumer;
import de.zannagh.eunomia.server.ServerConnectionEvents;
import de.zannagh.eunomia.utils.ServerUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The server half of the example wiring - and the whole point of the framework in one screen: to
 * add a packet and handle it, a mod declares a {@link ExamplePackets#PING type} and registers a
 * handler. No channel registration, no {@code CustomPacketPayload}, no mixin. Adding another packet
 * is one more {@code onServerReceive} call.
 */
public final class ExampleServerHandlers {

    private ExampleServerHandlers() {
    }

    public static void register() {
        // Answer a PING with a PONG - the exact same call the Paper plugin makes.
        ExampleHandlers.registerPingPong();

        // A replicated store keyed by player UUID: stored server-side, relayed to others, dumped to newcomers.
        ExampleReplication.enableServer();

        // On join, tell the client its permission level (mirrors the classic Armor Hider handshake).
        ServerConnectionEvents.registerJoin(new ServerConnectionEventConsumer() {
            @Override
            public void acceptPlayerJoin(MinecraftServer server, ServerPlayer player) {
                int level = ServerUtil.getPermissionLevelForPlayer(player, server);
                CommunicationManager.sendToPlayer(player.getUUID(), ExamplePackets.PERMISSION,
                        new PermissionPayload(level));
            }
        });
    }
}
