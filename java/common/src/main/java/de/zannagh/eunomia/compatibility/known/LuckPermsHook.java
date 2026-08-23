package de.zannagh.eunomia.compatibility.known;

import de.zannagh.eunomia.Eunomia;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.model.user.User;

import java.util.UUID;

/**
 * Isolated hook into the LuckPerms API.
 *
 * <p><b>Load-bearing invariant:</b> this class must ONLY be class-loaded once LuckPerms is confirmed
 * present, otherwise resolving its {@code net.luckperms.*} references throws
 * {@link NoClassDefFoundError}. That is why it is a separate class from the {@link LuckPermsCompat}
 * flag - the flag is loaded unconditionally by the presence probe, this one only from inside a
 * {@code CompatManager.requiresCompatTo(LuckPermsCompat.class)} branch.</p>
 *
 * <p>The dependency is declared {@code compileOnly} and is never bundled or shaded: on a server that
 * runs LuckPerms the API classes come from LuckPerms itself, and on one that does not, nothing here
 * is ever reached.</p>
 */
public final class LuckPermsHook {

    private LuckPermsHook() {
    }

    /**
     * Maps the {@link LuckPermsCompat#ADMIN_PERMISSION} node to a vanilla-style permission level.
     *
     * <p>Only ever <em>grants</em>: a player LuckPerms says nothing about (or an outright failure to
     * query it) yields {@code 0}, leaving the caller's vanilla op level as the final word.</p>
     *
     * @param playerUuid the player to resolve, may be {@code null}
     * @return 4 when the player holds the admin node, 0 otherwise
     */
    public static int getPermissionLevel(UUID playerUuid) {
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
            if (permissionData.checkPermission(LuckPermsCompat.ADMIN_PERMISSION).asBoolean()) {
                return 4;
            }
            return 0;
        } catch (Exception | LinkageError e) {
            Eunomia.LOGGER.warn("Failed to query LuckPerms for player {}: {}", playerUuid, e.getMessage());
            return 0;
        }
    }
}
