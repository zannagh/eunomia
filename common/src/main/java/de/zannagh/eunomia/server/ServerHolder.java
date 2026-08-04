package de.zannagh.eunomia.server;

import net.minecraft.server.MinecraftServer;

/**
 * Holds the currently running {@link MinecraftServer} so the server transport can resolve a player
 * by UUID and broadcast without every caller threading the server through. Set/cleared from the
 * server lifecycle events.
 */
public final class ServerHolder {

    private static volatile MinecraftServer server;

    private ServerHolder() {
    }

    public static void set(MinecraftServer current) {
        server = current;
    }

    public static void clear() {
        server = null;
    }

    public static MinecraftServer get() {
        return server;
    }
}
