package de.zannagh.eunomia.server;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public interface ServerConnectionEventConsumer {

    /**
     * Accepts the starting event of a server.</br></br>
     *
     * Must not throw ANY other exception than {@link EventConsumptionException}.
     * @param server The server that is starting.
     * @throws EventConsumptionException If an error occurs while consuming the event.
     */
    default void acceptStarting(MinecraftServer server) throws EventConsumptionException {
        throw new EventConsumptionException("Handlers must override the start accept method");
    }

    /**
     * Accepts the stopping event of a server.</br></br>
     *
     * Must not throw ANY other exception than {@link EventConsumptionException}.
     * @param server The server that is stopping.
     * @throws EventConsumptionException If an error occurs while consuming the event.
     */
    default void acceptStopping(MinecraftServer server) throws EventConsumptionException {
        throw new EventConsumptionException("Handlers must override the stop accept method");
    }

    /**
     * Accepts the player join event of a server.</br></br>
     *
     * Must not throw ANY other exception than {@link EventConsumptionException}.
     * @param server The server that the player joined.
     * @param player The player that joined the server.
     * @throws EventConsumptionException If an error occurs while consuming the event.
     */
    default void acceptPlayerJoin(MinecraftServer server, ServerPlayer player) throws EventConsumptionException {
        throw new EventConsumptionException("Handlers must override the player join accept method");
    }

    /**
     * An exception that is thrown when an error occurs while consuming a server connection event.
     */
    public class EventConsumptionException extends Exception {
        public EventConsumptionException(String message) {
            super(message);
        }
    }
}
