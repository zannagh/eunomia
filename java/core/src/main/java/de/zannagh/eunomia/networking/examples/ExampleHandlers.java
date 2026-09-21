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
        // PONG is only ever SENT from here, never received, so nothing else would declare it server-side.
        // Declaring it explicitly is not decoration: a channel that is first seen at send time is registered
        // too late for NeoForge's PayloadRegistrar (which closes when mod loading ends), and the send is then
        // dropped. EunomiaPaperPlugin registers the same three channels up front for the same class of reason
        // - Bukkit will not send on an unregistered outgoing channel either.
        CommunicationManager.register(ExamplePackets.PONG);
        CommunicationManager.onServerReceive(ExamplePackets.PING, (ping, context) ->
                context.reply(ExamplePackets.PONG,
                        new PongPayload(ping.message, ping.sentAtMillis, System.currentTimeMillis())));
    }
}
