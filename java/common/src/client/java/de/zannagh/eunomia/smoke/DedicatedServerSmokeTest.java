//? if fcgt {
package de.zannagh.eunomia.smoke;

import de.zannagh.eunomia.Eunomia;
import de.zannagh.eunomia.client.examples.ExampleClientHandlers;
import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.handshake.ServerCapabilities;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.TitleScreen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

// The same end-to-end networking assertions as NetworkingSmokeTest, but against a REAL DEDICATED
// SERVER over a real socket instead of an in-process integrated server.
//
// This exists because the integrated-server test is structurally blind to a whole class of bug. The
// server withholds clientbound packets behind ServerSendGate until the client's capability HELLO
// arrives, and drops the queue if it has not arrived within 15 s. On a memory connection that
// round-trip takes microseconds, so the window is never approached and a broken handshake still
// passes. Only a real connection exercises the ordering and the timing that production sees.
//
// The load-bearing assertion is lastPermissionLevel: the server pushes PERMISSION from a join
// handler, and that send goes through the gate. Receiving it proves the gate actually released,
// which is the thing a memory connection cannot tell us.
public final class DedicatedServerSmokeTest implements FabricClientGameTest {

    // 30 s at 20 TPS. Deliberately longer than ServerSendGate's own 15 s window: if the gate times
    // out and drops the queue, this test must still be waiting so it can report that as a failure
    // rather than racing it.
    private static final int TIMEOUT_TICKS = 600;

    /**
     * A dedicated server refuses to boot without an accepted EULA, and FCGT writes only
     * {@code server.properties}. This is the same agreement the project already makes in order to run
     * Minecraft at all; it is written into the throwaway gametest run directory, not anywhere durable.
     */
    private static void acceptEula() {
        try {
            Files.writeString(Path.of("eula.txt"),
                    "#Written by Eunomia's dedicated-server gametest.\neula=true\n");
        } catch (IOException e) {
            throw new AssertionError("could not accept the EULA for the dedicated server", e);
        }
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitForScreen(TitleScreen.class);

        // These are static and a sibling smoke may already have set them against the integrated
        // server. Clearing them first is what stops this test passing on someone else's evidence.
        ExampleClientHandlers.lastPermissionLevel = null;
        ExampleClientHandlers.lastPongMessage = null;

        acceptEula();

        Eunomia.LOGGER.info("[smoke/fcgt] dedicated-server smoke: starting server");
        try (var server = context.worldBuilder().createServer()) {
            Eunomia.LOGGER.info("[smoke/fcgt] dedicated-server smoke: connecting client");
            try (var connection = server.connect()) {
                try {
                    context.waitFor(client ->
                                    ExampleClientHandlers.lastPermissionLevel != null
                                            && ExampleClientHandlers.lastPongMessage != null
                                            && CommunicationManager.serverCapabilities().isResolved(),
                            TIMEOUT_TICKS);
                } catch (AssertionError | RuntimeException e) {
                    Eunomia.LOGGER.warn("[smoke/fcgt] dedicated exchange did not settle in {} ticks; asserting anyway",
                            TIMEOUT_TICKS);
                }

                if (ExampleClientHandlers.lastPermissionLevel == null) {
                    throw new AssertionError(
                            "No PERMISSION packet received over a real connection. The server pushes it from a "
                                    + "join handler through ServerSendGate, so either the client's capability HELLO "
                                    + "never reached the server or the gate dropped the queue before it did.");
                }
                if (ExampleClientHandlers.lastPongMessage == null) {
                    throw new AssertionError("No PONG received over a real connection - the client PING did not round-trip");
                }
                ServerCapabilities caps = CommunicationManager.serverCapabilities();
                if (!caps.isPresent()) {
                    throw new AssertionError("Capability handshake did not detect a Eunomia dedicated server");
                }

                Eunomia.LOGGER.info("[smoke/fcgt] DEDICATED SERVER SMOKE PASSED: permission={}, pong='{}', receivers={}",
                        ExampleClientHandlers.lastPermissionLevel,
                        ExampleClientHandlers.lastPongMessage,
                        caps.receiverChannels());
            }
        }
    }
}
//?}
