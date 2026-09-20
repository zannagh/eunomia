package de.zannagh.eunomia.server;

import com.mojang.authlib.GameProfile;
import de.zannagh.eunomia.Eunomia;
import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.utils.ExponentialBackoff;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class ServerConnectionEvents {

    private static final List<ServerConnectionEventConsumer> JOIN_HANDLERS = new ArrayList<>();
    private static final Map<UUID, Long> RECENT_JOINS = new ConcurrentHashMap<>();
    private static final int PLAYER_WAIT_TIMEOUT_MS = 5000;
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

    public static void onPlayerJoin(GameProfile profile, MinecraftServer server) {
        //? if >= 1.21.9 {
        UUID playerId = profile.id();
        String playerName = profile.name();
        //?}
        //? if < 1.21.9 {
        /*UUID playerId = profile.getId();
        String playerName = profile.getName();
        *///?}

        long now = System.currentTimeMillis();
        Long lastJoin = RECENT_JOINS.get(playerId);
        if (lastJoin != null && (now - lastJoin) < DEDUPE_WINDOW_MS) {
            return;
        }
        RECENT_JOINS.put(playerId, now);

        CompletableFuture.runAsync(() -> {
            ServerPlayer player;
            var backoff = ExponentialBackoff.apiBackoff(PLAYER_WAIT_TIMEOUT_MS);
            do {
                player = server.getPlayerList().getPlayer(playerId);
                if (player != null) {
                    break;
                }
            }
            while (backoff.shouldContinue());

            if (backoff.hasTimedOut) {
                Eunomia.LOGGER.warn("Timed out waiting for player {} ({}) to appear in player list after {} ms", playerName, playerId, backoff.getElapsedMillisSinceFirstAttempt());
                return;
            }

            final ServerPlayer foundPlayer = player;
            server.execute(() -> invokeHandlers(foundPlayer, server));
        });
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
