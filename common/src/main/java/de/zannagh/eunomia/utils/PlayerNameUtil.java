package de.zannagh.eunomia.utils;

import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

/**
 * Centralised player-name resolution. Every piece of code that extracts a
 * human-readable name from a {@link Player} (or something that might be one)
 * must go through this class so that the naming strategy is consistent
 * across config look-ups, identity comparisons, and rendering.
 *
 * @since 0.1.0
 */
public final class PlayerNameUtil {

    private PlayerNameUtil() {}

    /**
     * Returns the display name of a player entity, or an empty string if
     * {@code entity} is not a {@link Player} or yields an empty name.
     *
     * @since 0.1.0
     */
    public static @NonNull String getPlayerName(@Nullable Object entity) {
        if (!(entity instanceof Player player)) {
            return "";
        }
        String name = player.getDisplayName().getString();
        if (name.isEmpty()) {
            //? if >= 1.21.9 {
            name = player.getGameProfile().name();
            //?}
            //? if < 1.21.9 {
            /*name = player.getGameProfile().getName();
            *///?}
        }
        return name.isEmpty() ? "" : name;
    }
}
