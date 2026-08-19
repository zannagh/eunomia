package de.zannagh.eunomia.networking.comms;

import de.zannagh.eunomia.networking.packets.PacketType;

import java.util.UUID;

/**
 * The server-side send path, provided by whichever platform is hosting: the loader wires a
 * Minecraft {@code ServerPlayer.connection} transport, Paper wires a plugin-messaging transport, a
 * future HTTP relay wires its own. The {@link CommunicationManager} owns the routing and calls into
 * this to actually put bytes on the wire, so the manager itself stays platform-neutral.
 * <p>
 * Implementations own encoding (via {@code PayloadCodec} or the native game codec) - the manager
 * hands over the typed payload, not bytes.
 */
public interface ServerTransport {

    /** Sends {@code data} on {@code type}'s channel to the player with the given id. */
    <T> void sendToPlayer(UUID playerId, PacketType<T> type, T data);

    /** Sends {@code data} to every connected player. */
    <T> void broadcast(PacketType<T> type, T data);

    /** Sends {@code data} to every connected player except {@code excludedPlayerId}. */
    <T> void broadcastExcept(UUID excludedPlayerId, PacketType<T> type, T data);
}
