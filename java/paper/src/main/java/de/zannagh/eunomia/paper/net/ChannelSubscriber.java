package de.zannagh.eunomia.paper.net;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Force-subscribes a connection to our channels, server-side.
 *
 * <p>{@code CraftPlayer#sendPluginMessage} silently drops anything sent on a channel the client has
 * not announced via {@code minecraft:register}. Eunomia clients bypass Fabric API and write raw
 * custom payloads from mixins, so they never send {@code minecraft:register}: without this the
 * plugin would look healthy and send nothing. {@code CraftPlayer#addChannel(String)} is public on
 * every version, so reflection finds it and sidesteps the CraftBukkit package-relocation boundary.</p>
 */
public final class ChannelSubscriber {

    private static final String ADD_CHANNEL = "addChannel";

    private final Logger logger;
    private final Collection<String> channels;
    private volatile Method addChannel;
    private volatile boolean unavailableLogged;

    public ChannelSubscriber(Logger logger, Collection<String> channels) {
        this.logger = logger;
        this.channels = channels;
    }

    /** Adds every clientbound channel to {@code player}'s connection so S2C payloads are not dropped. */
    public void subscribe(Player player) {
        Method method = resolve(player);
        if (method == null) {
            return;
        }
        for (String channel : channels) {
            try {
                method.invoke(player, channel);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                logOnce("Failed to force-subscribe " + player.getUniqueId() + " to " + channel
                        + " - clientbound Eunomia payloads will be dropped for this player", e);
                return;
            }
        }
    }

    private Method resolve(Player player) {
        Method cached = addChannel;
        if (cached != null) {
            return cached;
        }
        try {
            Method resolved = player.getClass().getMethod(ADD_CHANNEL, String.class);
            resolved.setAccessible(true);
            addChannel = resolved;
            return resolved;
        } catch (NoSuchMethodException | RuntimeException | LinkageError e) {
            logOnce("Could not resolve CraftPlayer#" + ADD_CHANNEL + "(String); clients that do not "
                    + "send minecraft:register will receive no Eunomia payloads at all", e);
            return null;
        }
    }

    private void logOnce(String message, Throwable error) {
        if (unavailableLogged) {
            return;
        }
        unavailableLogged = true;
        logger.log(Level.WARNING, message, error);
    }
}
