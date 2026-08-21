package de.zannagh.eunomia.clients;

import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.packets.ClientContext;
import de.zannagh.eunomia.networking.packets.PacketType;

/** A reply from a relay-received packet goes back to the server through the installed (relay) transport. */
final class RelayClientContext implements ClientContext {

    @Override
    public <T> void reply(PacketType<T> type, T data) {
        CommunicationManager.sendToServer(type, data);
    }
}
