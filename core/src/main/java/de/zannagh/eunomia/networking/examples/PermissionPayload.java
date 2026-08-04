package de.zannagh.eunomia.networking.examples;

/**
 * A clientbound payload carrying the receiving player's op/permission level (0-4). The value itself
 * is resolved on whichever server sends it (the loader reads it from {@code MinecraftServer}, Paper
 * from Bukkit) - the packet is just the transport-neutral data.
 */
public class PermissionPayload {

    public int permissionLevel;

    public PermissionPayload() {
    }

    public PermissionPayload(int permissionLevel) {
        this.permissionLevel = permissionLevel;
    }
}
