package de.zannagh.eunomia.networking;

/**
 * The client-side send path. On the game this is a {@code Minecraft.getConnection()} transport; a
 * future non-game client (e.g. one talking to an HTTP relay because the server it joined does not
 * run the mod) registers its own. The {@link CommunicationManager} calls this from
 * {@link CommunicationManager#sendToServer(PacketType, Object)}.
 */
@FunctionalInterface
public interface ClientTransport {

    /** Sends {@code data} on {@code type}'s channel up to the server. */
    <T> void sendToServer(PacketType<T> type, T data);
}
