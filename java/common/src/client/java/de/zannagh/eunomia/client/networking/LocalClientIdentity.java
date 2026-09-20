package de.zannagh.eunomia.client.networking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.util.Locale;
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

    /** Minecraft's default port, elided from a scope so ":25565" and the bare host agree. */
    private static final String DEFAULT_PORT_SUFFIX = ":25565";

    /**
     * The connected server's address, normalised, or {@code null} in singleplayer/LAN (where no relay scope
     * applies).
     *
     * <p><b>Why normalise.</b> {@code ServerData.ip} is whatever the player typed, so the same server reaches
     * us under several spellings: a server-list entry saved as {@code mc.hypixel.net} and a direct connect to
     * {@code mc.hypixel.net:25565} are one server but two different strings. The relay partitions every
     * player's data <em>by this scope</em>, so the two spellings silently become two separate stores - the
     * player's synced config is present on one and missing on the other, with nothing logged. Observed in
     * production on 2026-09-20: one account produced both {@code mc.hypixel.net} and
     * {@code mc.hypixel.net:25565} scopes within three minutes.
     *
     * <p>Normalisation is deliberately conservative - trim, lower-case, drop one trailing dot from an FQDN and
     * elide an explicit default port. It does NOT resolve DNS or follow SRV records: two hostnames pointing at
     * one server stay distinct, because collapsing them would need a network round trip on the client thread
     * and would merge stores the player may well consider separate.
     */
    public static String currentServerScope(Minecraft client) {
        ServerData data = client.getCurrentServer();
        if (data == null) {
            return null;
        }
        return normaliseScope(data.ip);
    }

    /** Package-visible for tests: the scope spelling rule described on {@link #currentServerScope}. */
    static String normaliseScope(String rawAddress) {
        if (rawAddress == null) {
            return null;
        }
        String scope = rawAddress.trim().toLowerCase(Locale.ROOT);
        if (scope.isEmpty()) {
            return null;
        }
        if (scope.endsWith(DEFAULT_PORT_SUFFIX)) {
            scope = scope.substring(0, scope.length() - DEFAULT_PORT_SUFFIX.length());
        }
        // A trailing dot is a legal absolute FQDN ("example.com.") and resolves identically.
        while (scope.endsWith(".")) {
            scope = scope.substring(0, scope.length() - 1);
        }
        return scope.isEmpty() ? null : scope;
    }

    /**
     * The player's human-readable label for the connected server (the name in the server list entry), or
     * {@code null} in singleplayer/LAN. Sent to the relay for display only; the scope still partitions the data.
     */
    public static String currentServerName(Minecraft client) {
        ServerData data = client.getCurrentServer();
        return data == null ? null : data.name;
    }

    /** The local player's account UUID. */
    public static UUID localPlayerId(Minecraft client) {
        return client.getUser().getProfileId();
    }
}
