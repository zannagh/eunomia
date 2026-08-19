package de.zannagh.eunomia.networking.packets;

import de.zannagh.eunomia.networking.comms.Side;

import java.util.UUID;

/**
 * Context for a serverbound packet: who sent it, and a way to reply to just them. Implemented per
 * platform (the loader wraps {@code ServerPlayer}/{@code MinecraftServer}; Paper wraps a Bukkit
 * {@code Player}). Handlers that only need the authenticated sender id and a reply channel can stay
 * fully platform-agnostic; those that need more cast to the concrete type.
 */
public interface ServerContext extends PacketContext {

    @Override
    default Side side() {
        return Side.SERVER;
    }

    /** The authenticated id of the player the packet came from. */
    UUID senderId();

    /** The name of the player the packet came from, best-effort. */
    String senderName();

    /** Replies to the sending player on the given channel. */
    <T> void reply(PacketType<T> type, T data);
}
