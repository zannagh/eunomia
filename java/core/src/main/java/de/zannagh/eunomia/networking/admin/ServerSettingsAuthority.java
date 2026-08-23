package de.zannagh.eunomia.networking.admin;

import de.zannagh.eunomia.networking.packets.ServerContext;

/**
 * The server's answer to "may this player change eunomia's server-wide settings?".
 *
 * <p>This is the only place the question is ever asked, and it is always asked on the server, against the
 * authenticated sender of the packet being handled - never against anything the packet itself said. The
 * loaders implement it with {@code ServerUtil.isAdmin} (vanilla op level 4, then LuckPerms); the Paper
 * plugin with {@code PermissionResolver.isAdmin} (op, then superperms, then the LuckPerms API).</p>
 *
 * <p>Implementations should resolve the player from the concrete platform context ({@code McServerContext} /
 * {@code PaperServerContext}). {@link ServerSettingsExchange} treats a throwing implementation as "not an
 * administrator", so a broken permission backend fails closed.</p>
 *
 * @since 0.3.0
 */
@FunctionalInterface
public interface ServerSettingsAuthority {

    /**
     * @param context the context of the packet being handled, identifying the authenticated sender.
     * @return whether that sender is a eunomia administrator on this server.
     */
    boolean isAdministrator(ServerContext context);
}
