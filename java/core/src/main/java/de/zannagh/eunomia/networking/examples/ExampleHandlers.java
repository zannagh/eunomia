package de.zannagh.eunomia.networking.examples;

import de.zannagh.eunomia.networking.comms.CommunicationManager;

/**
 * The transport-agnostic half of the example wiring. The PING → PONG handler touches nothing
 * platform-specific (just {@code context.reply}), so it is registered identically by the loaders and
 * by the Paper plugin - the clearest demonstration that a handler written once runs everywhere.
 */
public final class ExampleHandlers {

    private ExampleHandlers() {
    }

    /** Registers the server-side PING handler that echoes a PONG straight back to the sender. */
    public static void registerPingPong() {
        CommunicationManager.onServerReceive(ExamplePackets.PING, (ping, context) ->
                context.reply(ExamplePackets.PONG,
                        new PongPayload(ping.message, ping.sentAtMillis, System.currentTimeMillis())));
    }
}
