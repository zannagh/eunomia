package de.zannagh.eunomia.client.examples;

import de.zannagh.eunomia.Eunomia;
import de.zannagh.eunomia.client.networking.ClientConnectionEvents;
import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.examples.ExamplePackets;
import de.zannagh.eunomia.networking.examples.ExampleReplication;
import de.zannagh.eunomia.networking.examples.PingPayload;

/**
 * The client half of the example wiring. Registers handlers for the two clientbound packets and
 * fires a PING at the server on join. The last received values are kept in {@code static} fields so
 * a gametest can assert the round-trip completed.
 */
public final class ExampleClientHandlers {

    /** Last PONG received from the server (null until the round-trip completes). */
    public static volatile String lastPongMessage;
    public static volatile long lastPongServerTime;
    /** Last permission level pushed by the server on join (null until received). */
    public static volatile Integer lastPermissionLevel;

    private ExampleClientHandlers() {
    }

    public static void register() {
        // Mirror the replicated example store: snapshot on join + per-entry relays keep it in sync.
        ExampleReplication.enableClient();

        CommunicationManager.onClientReceive(ExamplePackets.PONG, (pong, context) -> {
            lastPongMessage = pong.message;
            lastPongServerTime = pong.serverTimeMillis;
            Eunomia.LOGGER.info("[eunomia-example] PONG '{}' (server time {})", pong.message, pong.serverTimeMillis);
        });

        CommunicationManager.onClientReceive(ExamplePackets.PERMISSION, (permission, context) -> {
            lastPermissionLevel = permission.permissionLevel;
            Eunomia.LOGGER.info("[eunomia-example] permission level {}", permission.permissionLevel);
        });

        ClientConnectionEvents.registerJoin((handler, client) ->
                CommunicationManager.sendToServer(ExamplePackets.PING,
                        new PingPayload("hello from client", System.currentTimeMillis())));
    }
}
