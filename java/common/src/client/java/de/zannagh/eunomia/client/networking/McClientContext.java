package de.zannagh.eunomia.client.networking;

import de.zannagh.eunomia.networking.ClientContext;
import de.zannagh.eunomia.networking.CommunicationManager;
import de.zannagh.eunomia.networking.PacketType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/**
 * Client-side context handed to a clientbound handler. Exposes the concrete
 * {@code ClientPacketListener}/{@code Minecraft} for handlers that need them.
 */
public final class McClientContext implements ClientContext {

    private final ClientPacketListener handler;
    private final Minecraft client;

    public McClientContext(ClientPacketListener handler, Minecraft client) {
        this.handler = handler;
        this.client = client;
    }

    public ClientPacketListener handler() {
        return handler;
    }

    public Minecraft client() {
        return client;
    }

    @Override
    public <T> void reply(PacketType<T> type, T data) {
        CommunicationManager.sendToServer(type, data);
    }
}
