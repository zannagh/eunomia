package de.zannagh.eunomia.paper;

import de.zannagh.eunomia.networking.comms.CommunicationManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Drops a leaving player's clientbound capability state - Paper's half of the cleanup the loader does from
 * its play-listener teardown mixin.
 *
 * <p>{@code PlayerQuitEvent} is the right hook because Bukkit fires it for every way a player can leave
 * (quit, kick, timeout, and the shutdown sweep), which is what makes it the counterpart of the one entry
 * {@code PlayerJoinEvent} creates. Without it the gate's per-player map would gain an entry per join and
 * lose none for as long as the server runs.</p>
 *
 * <p><b>Known limitation, and why nothing here tries to paper over it.</b> A {@code /reload} re-enables this
 * plugin on a fresh classloader while existing connections stay up, so every already-connected player's
 * recorded capability is gone and no {@code PlayerJoinEvent} (and no fresh HELLO) will re-establish it. Those
 * players are then treated as unresolved and, once the probe window closes, withheld from until they
 * reconnect. Deliberately not worked around: the alternative - assuming an online player is capable - is the
 * assumption that disconnects pre-Eunomia clients, which is the entire thing this gate exists to prevent.</p>
 */
public final class PaperQuitListener implements Listener {

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        CommunicationManager.onPlayerDisconnect(event.getPlayer().getUniqueId());
    }
}
