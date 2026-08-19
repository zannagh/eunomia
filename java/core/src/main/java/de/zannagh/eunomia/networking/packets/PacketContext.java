package de.zannagh.eunomia.networking.packets;

import de.zannagh.eunomia.networking.comms.Side;

/**
 * The minimal, platform-neutral context handed to a packet handler. Concrete platforms extend
 * {@link ServerContext} / {@link ClientContext} with their own richer types (carrying e.g. a
 * {@code ServerPlayer} or a Bukkit {@code Player}); a handler that needs those casts to the
 * platform type, exactly as it would with a hand-rolled context object.
 */
public interface PacketContext {

    /** Which side this handler is executing on. */
    Side side();
}
