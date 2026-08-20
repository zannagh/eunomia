package de.zannagh.eunomia.networking;

import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.packets.ClientContext;
import de.zannagh.eunomia.networking.packets.PacketType;

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
