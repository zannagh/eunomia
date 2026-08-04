package de.zannagh.eunomia.networking.packets;

import net.minecraft.resources.Identifier;

/**
 * Game version agnostic marker interface for packets that can be sent and handled.
 */
public interface EunomiaPacket {
    /**
     * Gets the channel identifier for this packet to use it on older game versions.
     * @return
     */
    Identifier getChannel();
}
