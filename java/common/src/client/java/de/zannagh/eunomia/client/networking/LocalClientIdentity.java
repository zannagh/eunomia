package de.zannagh.eunomia.client.networking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.util.UUID;

/**
 * Extracts the two identity facts the external relay needs from the running client: the "scope" (the address of
 * the Minecraft server this client is connected to, which partitions relay data per server) and the local
 * player's UUID (the relay's connection id and packet sender). Kept isolated so version-sensitive client lookups
 * live in one place.
 */
public final class LocalClientIdentity {

    private LocalClientIdentity() {
    }

    /** The connected server's address, or {@code null} in singleplayer/LAN (where no relay scope applies). */
    public static String currentServerScope(Minecraft client) {
        ServerData data = client.getCurrentServer();
        return data == null ? null : data.ip;
    }

    /** The local player's account UUID. */
    public static UUID localPlayerId(Minecraft client) {
        return client.getUser().getProfileId();
    }
}
