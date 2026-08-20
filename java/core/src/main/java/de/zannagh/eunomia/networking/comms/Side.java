package de.zannagh.eunomia.networking.comms;

/**
 * Which end of a connection code is running on. Deliberately transport-neutral: a Minecraft
 * client, a dedicated server, a Bukkit plugin and a future HTTP relay all map onto exactly these
 * two roles, so handlers and contexts can be reasoned about without knowing the platform.
 */
public enum Side {
    /** The side that initiates a connection and sends serverbound packets. */
    CLIENT,
    /** The side that accepts connections and sends clientbound packets. */
    SERVER
}
