package de.zannagh.eunomia.server;

import de.zannagh.eunomia.Eunomia;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Server lifecycle events.
 * Replaces Fabric API's ServerLifecycleEvents.
 */
public final class ServerLifecycleEvents {

    private static final List<ServerConnectionEventConsumer> HANDLERS = new ArrayList<>();

    public static void register(ServerConnectionEventConsumer handler) {
        HANDLERS.add(handler);
    }

    public static void onServerStarting(MinecraftServer server) {
        for (var handler : HANDLERS) {
            try {
                handler.acceptStarting(server);
            } catch (ServerConnectionEventConsumer.EventConsumptionException e) {
                Eunomia.LOGGER.error("Error in server starting handler", e);
            }
        }
    }

    public static void onServerStopping(MinecraftServer server) {
        for (var handler : HANDLERS) {
            try {
                handler.acceptStopping(server);
            } catch (ServerConnectionEventConsumer.EventConsumptionException e) {
                Eunomia.LOGGER.error("Error in server stopping handler", e);
            }
        }
    }
}
