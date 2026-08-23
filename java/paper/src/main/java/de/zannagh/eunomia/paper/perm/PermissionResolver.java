package de.zannagh.eunomia.paper.perm;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

/**
 * Resolves the vanilla-style permission level the Eunomia wire protocol carries, on the Bukkit side.
 *
 * <p>Clients compare {@code PermissionPayload.level()} numerically, so this keeps emitting integers
 * (0 / {@link #ADMIN_LEVEL}) rather than a boolean. Resolution order mirrors the loader half:</p>
 * <ol>
 *   <li>{@link Player#isOp()} - the Bukkit equivalent of vanilla op level 4, short-circuits;</li>
 *   <li>{@link Player#hasPermission(String)} - superperms, which every permissions plugin
 *       (LuckPerms included) answers, so no API-specific code is needed in the common case;</li>
 *   <li>the LuckPerms API directly, for setups where the node is not exposed through superperms.</li>
 * </ol>
 *
 * <p>LuckPerms presence is detected once at plugin enable ({@code PluginManager} lookup first, then a
 * {@code Class.forName} on the API entry point) and cached, so the per-join path costs nothing and
 * {@link LuckPermsHook} - the only class referencing {@code net.luckperms.*} - is never loaded on a
 * server without LuckPerms.</p>
 */
public final class PermissionResolver {

    /** The permission node that grants server-wide Eunomia administration. */
    public static final String ADMIN_PERMISSION = "eunomia.admin";

    /** The vanilla permission level eunomia treats as "server administrator". */
    public static final int ADMIN_LEVEL = 4;

    /** The Bukkit plugin name LuckPerms registers under. */
    public static final String LUCKPERMS_PLUGIN = "LuckPerms";

    private static final String LUCKPERMS_CLASS = "net.luckperms.api.LuckPermsProvider";

    private final Logger logger;
    private final boolean luckPermsPresent;
    private boolean luckPermsLogged;

    /**
     * Detects LuckPerms through Bukkit. Construct once, at plugin enable, after all plugins loaded.
     *
     * @param logger the plugin logger
     */
    public PermissionResolver(Logger logger) {
        this(logger, PermissionResolver::detectLuckPerms);
    }

    /**
     * Detection-injecting constructor, so the resolution ladder can be exercised without a running server -
     * and so a plugin embedding eunomia can decide for itself whether LuckPerms is consulted at all.
     * Production code inside this plugin uses {@link #PermissionResolver(Logger)}.
     *
     * <p>Public rather than package-private because the tests that matter most for this class are the ones
     * asserting what it lets people <em>do</em>, and those live next to the features being guarded rather
     * than next to the ladder itself.</p>
     *
     * @param logger the plugin logger
     * @param detector supplies whether the LuckPerms API is usable
     */
    public PermissionResolver(Logger logger, BooleanSupplier detector) {
        this.logger = logger;
        this.luckPermsPresent = detector.getAsBoolean();
    }

    /** Whether LuckPerms was detected at construction time. */
    public boolean isLuckPermsPresent() {
        return luckPermsPresent;
    }

    /**
     * Resolves the permission level for {@code player}.
     *
     * @param player the player to resolve, may be {@code null}
     * @return {@link #ADMIN_LEVEL} for an administrator, otherwise 0
     */
    public int getPermissionLevel(Player player) {
        if (player == null) {
            return 0;
        }
        if (player.isOp()) {
            return ADMIN_LEVEL;
        }
        if (player.hasPermission(ADMIN_PERMISSION)) {
            return ADMIN_LEVEL;
        }
        if (!luckPermsPresent) {
            return 0;
        }
        logLuckPermsOnce();
        // Guarded call site: the ONLY reference to LuckPermsHook, reached only once the presence
        // check above passed - so the net.luckperms.* classes are never resolved without LuckPerms.
        return LuckPermsHook.getPermissionLevel(player.getUniqueId(), logger);
    }

    /**
     * Boolean convenience over {@link #getPermissionLevel}: whether {@code player} may change
     * eunomia's server-wide settings.
     *
     * @param player the player to resolve, may be {@code null}
     * @return whether the player is an eunomia administrator
     */
    public boolean isAdmin(Player player) {
        return getPermissionLevel(player) >= ADMIN_LEVEL;
    }

    private void logLuckPermsOnce() {
        if (luckPermsLogged) {
            return;
        }
        luckPermsLogged = true;
        logger.info("LuckPerms detected - using it for permission checks instead of default permission handling.");
        logger.info("Note: Add permission to users with the following key to let them change eunomia's "
                + "server-wide settings: " + ADMIN_PERMISSION);
    }

    private static boolean detectLuckPerms() {
        if (Bukkit.getPluginManager().getPlugin(LUCKPERMS_PLUGIN) == null) {
            return false;
        }
        try {
            Class.forName(LUCKPERMS_CLASS);
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
