package de.zannagh.eunomia.paper.perm;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.model.user.User;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Isolated hook into the LuckPerms API.
 *
 * <p><b>Load-bearing invariant:</b> this class must ONLY be class-loaded once LuckPerms is confirmed
 * present, otherwise resolving its {@code net.luckperms.*} references throws
 * {@link NoClassDefFoundError}. {@link PermissionResolver} owns that guard and is the only caller.</p>
 *
 * <p>The dependency is {@code compileOnly} and is never shaded into the plugin jar - on a server
 * running LuckPerms the classes come from the LuckPerms plugin itself.</p>
 */
public final class LuckPermsHook {

    private LuckPermsHook() {
    }

    /**
     * Maps the {@link PermissionResolver#ADMIN_PERMISSION} node to a vanilla-style permission level.
     *
     * <p>Only ever <em>grants</em>: an unknown player, or a failure to reach LuckPerms at all, yields
     * {@code 0} so the caller's op/superperms answer stays the final word.</p>
     *
     * @param playerUuid the player to resolve
     * @param logger the plugin logger used to report a failed lookup
     * @return 4 when the player holds the admin node, 0 otherwise
     */
    public static int getPermissionLevel(UUID playerUuid, Logger logger) {
        if (playerUuid == null) {
            return 0;
        }
        try {
            LuckPerms api = LuckPermsProvider.get();
            User user = api.getUserManager().getUser(playerUuid);
            if (user == null) {
                return 0;
            }
            CachedPermissionData permissionData = user.getCachedData().getPermissionData();
            if (permissionData.checkPermission(PermissionResolver.ADMIN_PERMISSION).asBoolean()) {
                return PermissionResolver.ADMIN_LEVEL;
            }
            return 0;
        } catch (Exception | LinkageError e) {
            logger.log(Level.WARNING, "Failed to query LuckPerms for player " + playerUuid, e);
            return 0;
        }
    }
}
