package de.zannagh.eunomia.utils;

import de.zannagh.eunomia.Eunomia;
import de.zannagh.eunomia.compatibility.CompatManager;
import de.zannagh.eunomia.compatibility.known.LuckPermsCompat;
import de.zannagh.eunomia.compatibility.known.LuckPermsHook;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;

/**
 * Server-side permission resolution for the loader (Fabric/NeoForge) half of eunomia.
 *
 * <p>Vanilla op level is always resolved first and wins outright at level 4; LuckPerms is only ever
 * consulted afterwards and can only <em>grant</em> {@link #ADMIN_LEVEL}, never revoke what the
 * vanilla permission system already granted.</p>
 */
public final class ServerUtil {

    /** The vanilla permission level eunomia treats as "server administrator". */
    public static final int ADMIN_LEVEL = 4;

    private static boolean luckPermsLogged;

    private ServerUtil() {
    }

    /**
     * Resolves the vanilla-style permission level (0-4) for {@code player}.
     *
     * @param player the player to resolve
     * @param server the server the player is connected to
     * @return the player's permission level
     */
    public static int getPermissionLevelForPlayer(Player player, MinecraftServer server) {
        // Guarded call site: LuckPermsHook - the only class touching net.luckperms.* - is referenced
        // exclusively inside this branch, so it is never class-loaded on a server without LuckPerms.
        if (luckPermsPresent()) {
            logLuckPermsOnce();
            if (getVanillaPermissionLevel(player, server) >= ADMIN_LEVEL) {
                return ADMIN_LEVEL;
            }
            return LuckPermsHook.getPermissionLevel(player.getUUID());
        }
        return getVanillaPermissionLevel(player, server);
    }

    /**
     * The admin-only view of {@link #getPermissionLevelForPlayer}: {@link #ADMIN_LEVEL} for an admin,
     * {@code 0} for everyone else.
     *
     * <p>Kept as an {@code int} rather than a boolean because it is what goes on the wire - clients
     * compare the permission level numerically - so a packet handler can hand this straight to a
     * payload without re-deriving a level from a boolean.</p>
     *
     * @param player the player to resolve
     * @param server the server the player is connected to
     * @return {@link #ADMIN_LEVEL} when the player may change server-wide settings, otherwise 0
     */
    public static int getAdminPermissionLevel(Player player, MinecraftServer server) {
        return getPermissionLevelForPlayer(player, server) >= ADMIN_LEVEL ? ADMIN_LEVEL : 0;
    }

    /**
     * Boolean convenience over {@link #getAdminPermissionLevel}: whether {@code player} may change
     * eunomia's server-wide settings (vanilla op level 4, or the
     * {@link LuckPermsCompat#ADMIN_PERMISSION} node where LuckPerms is installed).
     *
     * @param player the player to resolve
     * @param server the server the player is connected to
     * @return whether the player is an eunomia administrator
     */
    public static boolean isAdmin(Player player, MinecraftServer server) {
        return getAdminPermissionLevel(player, server) >= ADMIN_LEVEL;
    }

    /**
     * {@link #isAdmin(Player, MinecraftServer)} for callers that only hold the player - a serverbound
     * packet handler, typically. Resolves the server off the player's level.
     *
     * @param player the player to resolve; a client-side or level-less player is never an admin
     * @return whether the player is an eunomia administrator
     */
    public static boolean isAdmin(Player player) {
        if (player == null) {
            return false;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return false;
        }
        return isAdmin(player, server);
    }

    /**
     * Registers eunomia's built-in {@link LuckPermsCompat} flag and runs the detection pass. Called once
     * from {@code Eunomia#init()}; it used to bootstrap itself lazily on the first permission query, back
     * when the entry point was not available to write to.
     *
     * <p>Detection is deliberately the <em>resource</em> probe and never {@code Class.forName}: this runs
     * on the loader's init thread, before (and on NeoForge alongside) other mods' construction, and a
     * speculative class load of another mod's entrypoint there is exactly the kind of thing that breaks
     * startup. Idempotent - {@code registerCompatFlag} is a set insert and re-probing only re-affirms
     * flags - and a consumer that registered its own {@code LuckPermsCompat} subclass is unaffected,
     * since the query below matches any instance of the type.</p>
     */
    public static void initPermissionCompat() {
        CompatManager.registerCompatFlag(LuckPermsCompat.INSTANCE);
        CompatManager.setCompatFlagByResourceProbing();
    }

    /**
     * Whether LuckPerms was detected by {@link #initPermissionCompat()} (or by a consuming mod's own
     * compat registration). A pure query - no probing, no class loading.
     */
    private static boolean luckPermsPresent() {
        return CompatManager.requiresCompatTo(LuckPermsCompat.class);
    }

    private static void logLuckPermsOnce() {
        if (luckPermsLogged) {
            return;
        }
        luckPermsLogged = true;
        Eunomia.LOGGER.info("LuckPerms detected - using it for permission checks instead of default permission handling.");
        Eunomia.LOGGER.info("Note: Add permission to users with the following key to let them change eunomia's server-wide settings: {}",
                LuckPermsCompat.ADMIN_PERMISSION);
    }

    private static int getVanillaPermissionLevel(Player player, MinecraftServer server) {
        //? if >= 1.21.9 {
        if (server.isSingleplayerOwner(player.nameAndId())) {
            return ADMIN_LEVEL;
        }
        //?} else {
        /*if (server.isSingleplayerOwner(player.getGameProfile())) {
            return ADMIN_LEVEL;
        }
        *///?}
        //? if >= 1.21.11
        return server.getProfilePermissions(player.nameAndId()).level().id();
        //? if >= 1.21.9 && < 1.21.11
        //return server.getProfilePermissions(player.nameAndId());
        //? if < 1.21.9
        //return server.getProfilePermissions(player.getGameProfile());
    }
}
