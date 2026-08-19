package de.zannagh.eunomia.paper.net;

import de.zannagh.eunomia.networking.comms.CommunicationManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Feeds inbound plugin messages into the {@link CommunicationManager}. The channel name is the
 * packet's {@code namespace:path} routing key, so the manager decodes the raw bytes through the
 * shared {@link de.zannagh.eunomia.networking.serialization.PayloadCodec} and dispatches to the
 * registered handler. A malformed payload is logged and dropped - never rethrown, since that would
 * drop the sender's connection.
 */
public final class PaperMessageListener implements PluginMessageListener {

    private final Logger logger;
    private final PaperServerTransport transport;

    public PaperMessageListener(Logger logger, PaperServerTransport transport) {
        this.logger = logger;
        this.transport = transport;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        try {
            PaperServerContext context = new PaperServerContext(player, transport);
            CommunicationManager.dispatchServerboundRaw(channel, message, context);
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Dropping a malformed Eunomia payload on " + channel
                    + " from " + player.getUniqueId() + ": " + e.getMessage());
        }
    }
}
