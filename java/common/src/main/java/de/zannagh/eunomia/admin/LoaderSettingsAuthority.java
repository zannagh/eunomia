package de.zannagh.eunomia.admin;

import de.zannagh.eunomia.networking.admin.ServerSettingsAuthority;
import de.zannagh.eunomia.networking.loader.McServerContext;
import de.zannagh.eunomia.networking.packets.ServerContext;
import de.zannagh.eunomia.utils.ServerUtil;

/**
 * The loader-side permission answer for the admin settings exchange: vanilla op level 4 first (which
 * LuckPerms can never revoke), then the {@code eunomia.admin} node where LuckPerms is installed - i.e.
 * exactly {@link ServerUtil#isAdmin}, not a second ladder that could disagree with it.
 *
 * <p>The player is taken from the concrete {@link McServerContext}, which is to say from the authenticated
 * connection the packet arrived on. A context that is not a loader context at all (a relayed or synthetic
 * one) is not an administrator: there is no player to resolve, so the only safe answer is no.</p>
 *
 * @since 0.3.0
 */
final class LoaderSettingsAuthority implements ServerSettingsAuthority {

    @Override
    public boolean isAdministrator(ServerContext context) {
        if (!(context instanceof McServerContext loaderContext)) {
            return false;
        }
        return ServerUtil.isAdmin(loaderContext.player(), loaderContext.server());
    }
}
