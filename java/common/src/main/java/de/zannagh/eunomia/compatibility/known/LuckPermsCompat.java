package de.zannagh.eunomia.compatibility.known;

import de.zannagh.eunomia.compatibility.CompatFlag;

import java.util.List;
import java.util.UUID;

public abstract class LuckPermsCompat implements CompatFlag {

    public static int getPermissionLevel(UUID playerUuid){
        /*
        // TODO: Add compile source to project to use LuckPerms API.
        try {
            LuckPerms api = LuckPermsProvider.get();
            User user = api.getUserManager().getUser(playerUuid);
            if (user == null) {
                return 0;
            }

            CachedPermissionData permData = user.getCachedData().getPermissionData();
            if (permData.checkPermission(ADMIN_PERMISSION).asBoolean()) {
                return 4;
            }
            return 0;
        } catch (Exception | LinkageError e) {
            Eunomia.LOGGER.warn("Failed to query LuckPerms for player {}: {}", playerUuid, e.getMessage());
            return 0;
        }*/
        return 0;
    }

    public static final String ADMIN_PERMISSION = "eunomia.admin";

    @Override
    public List<String> classNames() {
        return List.of("net.luckperms.api.LuckPerms");
    }

    @Override
    public boolean needsInitialization() {
        return false;
    }

    @Override
    public List<CompatFlag> dependencies() {
        return List.of();
    }
}
