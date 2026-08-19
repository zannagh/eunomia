package de.zannagh.eunomia.clients;

import de.zannagh.eunomia.networking.comms.ClientTransport;
import de.zannagh.eunomia.networking.packets.PacketType;

/**
 * A {@link ClientTransport} that routes serverbound sends to the external relay instead of the Minecraft server.
 * Installed by the client transport selector (via {@code CommunicationManager.setClientTransport}) when the joined
 * MC server does not run Eunomia and the fallback is enabled, opted in and reachable.
 */
public final class ExternalClientTransport implements ClientTransport {

    private final ExternalServerClient client;

    public ExternalClientTransport(ExternalServerClient client) {
        this.client = client;
    }

    @Override
    public <T> void sendToServer(PacketType<T> type, T data) {
        client.send(type, data);
    }
}
