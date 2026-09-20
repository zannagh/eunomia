package de.zannagh.eunomia.server;

import de.zannagh.eunomia.Eunomia;
import de.zannagh.eunomia.networking.comms.CommunicationManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class ServerConnectionEvents {

    private static final List<ServerConnectionEventConsumer> JOIN_HANDLERS = new ArrayList<>();
    private static final Map<UUID, Long> RECENT_JOINS = new ConcurrentHashMap<>();
    private static final long DEDUPE_WINDOW_MS = 2000;

    private static final List<Consumer<UUID>> DISCONNECT_HANDLERS = new ArrayList<>();

    public static void registerJoin(ServerConnectionEventConsumer handler) {
        JOIN_HANDLERS.add(handler);
    }

    /**
     * Registers a callback for "this player's play connection ended". Takes the id rather than the
     * {@code ServerPlayer} on purpose: by the time a connection tears down the player object is on its way
     * out, and everything that needs cleaning here is keyed by id anyway.
     */
    public static void registerDisconnect(Consumer<UUID> handler) {
        DISCONNECT_HANDLERS.add(handler);
    }

    /**
     * Fires the disconnect event for {@code playerId}. Called from the play-listener teardown mixin, which
     * is the single funnel every leave path goes through.
     *
     * <p>The two cleanups below are done here rather than through {@link #registerDisconnect} because they
     * are this library's own per-player state and must not depend on anything having registered: the
     * clientbound capability gate's map, and the join de-duplication map in this very class. Both are keyed
     * by player and both would otherwise grow by one entry per join, forever.</p>
     */
    public static void onPlayerDisconnect(UUID playerId) {
        if (playerId == null) {
            return;
        }
        RECENT_JOINS.remove(playerId);
        CommunicationManager.onPlayerDisconnect(playerId);
        for (Consumer<UUID> handler : DISCONNECT_HANDLERS) {
            try {
                handler.accept(playerId);
            } catch (RuntimeException e) {
                Eunomia.LOGGER.error("Error in player disconnect handler", e);
            }
        }
    }

    /**
     * Fires the join event for a player that is <b>already in the player list</b>. Called from the
     * {@code PlayerList.placeNewPlayer} tail mixin, which is the first moment that is true.
     *
     * <p>There is deliberately no waiting here any more. This used to be raised from the login listener,
     * which fires before the configuration phase has even started, and bridged the gap by polling the
     * player list on a pooled thread until an exponential backoff ran out at ~4.3 s. That window is not
     * ours to bound - it is however long the client takes to get through registry sync, the resource
     * pack and the rest - so on a real server the poll could simply lose, and losing it silently skipped
     * every handler. Hooking the moment itself removes the race rather than widening it.</p>
     *
     * <p>The de-duplication below is kept as cheap insurance: {@code placeNewPlayer} is called once per
     * join by vanilla, but it is a public method and nothing stops another mod from routing a respawn or
     * a transfer back through it.</p>
     */
    public static void onPlayerJoin(ServerPlayer player, MinecraftServer server) {
        UUID playerId = player.getUUID();

        long now = System.currentTimeMillis();
        Long lastJoin = RECENT_JOINS.get(playerId);
        if (lastJoin != null && (now - lastJoin) < DEDUPE_WINDOW_MS) {
            return;
        }
        RECENT_JOINS.put(playerId, now);

        invokeHandlers(player, server);
    }

    private static void invokeHandlers(ServerPlayer player, MinecraftServer server) {
        for (var handler : JOIN_HANDLERS) {
            try {
                handler.acceptPlayerJoin(server, player);
            } catch (ServerConnectionEventConsumer.EventConsumptionException e) {
                Eunomia.LOGGER.error("Error in player join handler", e);
            }
        }
    }
}
