package de.zannagh.eunomia.networking.comms;

import de.zannagh.eunomia.networking.packets.PacketType;

import java.util.Collection;
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

    /**
     * The ids of everyone currently connected, in no particular order. Empty when the platform is not up
     * yet (no server instance, plugin still enabling); never {@code null}.
     *
     * <p>The {@link CommunicationManager} needs this to expand a broadcast into per-player sends, because
     * the clientbound capability gate's answer is per player - see
     * {@link CommunicationManager#sendToPlayer(PacketType, Object)}. It is intentionally not a default
     * method: a transport that answered "nobody" by default would make every broadcast through it vanish
     * in silence, which is worse than a compile error for whoever implements one.</p>
     */
    Collection<UUID> connectedPlayerIds();
}
