package de.zannagh.eunomia.client;

import de.zannagh.eunomia.Eunomia;
import de.zannagh.eunomia.client.examples.ExampleClientHandlers;
import de.zannagh.eunomia.client.networking.ClientConnectionEvents;
import de.zannagh.eunomia.client.networking.McClientTransport;
import de.zannagh.eunomia.networking.CommunicationManager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Shared client-side entry point for the Eunomia library. The per-loader client initializers
 * delegate to {@link #init()}.
 */
public final class EunomiaClient {

    /** How long to wait for a HELLO_ACK before concluding the server does not run Eunomia. */
    private static final long HANDSHAKE_TIMEOUT_SECONDS = 5;

    private EunomiaClient() {
    }

    public static void init() {
        // Install the client send path, then the example client handlers (which PING on join).
        CommunicationManager.setClientTransport(new McClientTransport());
        ExampleClientHandlers.register();

        // Capability handshake: probe the server on join, and if no ACK arrives, conclude it does not
        // run Eunomia - the point where a consuming mod would offer a custom communications server.
        CommunicationManager.enableClientHandshake();
        ClientConnectionEvents.registerJoin((handler, client) -> {
            CommunicationManager.beginServerProbe();
            CompletableFuture.delayedExecutor(HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .execute(CommunicationManager::markServerProbeTimedOut);
        });
        ClientConnectionEvents.registerDisconnect(client ->
                CommunicationManager.onClientDisconnect());

        Eunomia.LOGGER.info("Eunomia client initialized");
    }
}
