package de.zannagh.eunomia.networking;

/**
 * Handles a clientbound packet of type {@code T}. Registered via
 * {@link CommunicationManager#onClientReceive(PacketType, ClientPacketHandler)}.
 *
 * @param <T> the payload type
 */
@FunctionalInterface
public interface ClientPacketHandler<T> {
    void handle(T payload, ClientContext context);
}
