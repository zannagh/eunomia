package de.zannagh.eunomia.paper.admin;

import de.zannagh.eunomia.networking.admin.ServerSettingsAuthority;
import de.zannagh.eunomia.networking.packets.ServerContext;
import de.zannagh.eunomia.paper.net.PaperServerContext;
import de.zannagh.eunomia.paper.perm.PermissionResolver;

import java.util.Objects;

/**
 * The Paper-side permission answer for the admin settings exchange: op, then the {@code eunomia.admin}
 * superperms node, then the LuckPerms API - i.e. exactly {@link PermissionResolver#isAdmin}, reusing the
 * resolver the plugin already built at enable rather than a second ladder that could disagree with the one
 * driving the join-time permission packet.
 *
 * <p>The player comes from the concrete {@link PaperServerContext}, which is to say from the authenticated
 * plugin-message sender. Any other context has no Bukkit player to resolve and is therefore not an
 * administrator.</p>
 *
 * @since 0.3.0
 */
final class PaperSettingsAuthority implements ServerSettingsAuthority {

    private final PermissionResolver permissions;

    PaperSettingsAuthority(PermissionResolver permissions) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
    }

    @Override
    public boolean isAdministrator(ServerContext context) {
        if (!(context instanceof PaperServerContext paperContext)) {
            return false;
        }
        return permissions.isAdmin(paperContext.player());
    }
}
