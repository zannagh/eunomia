package de.zannagh.eunomia.networking;

/**
 * Handles a serverbound packet of type {@code T}. Registered via
 * {@link CommunicationManager#onServerReceive(PacketType, ServerPacketHandler)}.
 *
 * @param <T> the payload type
 */
@FunctionalInterface
public interface ServerPacketHandler<T> {
    void handle(T payload, ServerContext context);
}
