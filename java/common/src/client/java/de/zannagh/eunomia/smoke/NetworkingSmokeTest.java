//? if fcgt {
/*package de.zannagh.eunomia.smoke;

import de.zannagh.eunomia.Eunomia;
import de.zannagh.eunomia.client.examples.ExampleClientHandlers;
import de.zannagh.eunomia.networking.CommunicationManager;
import de.zannagh.eunomia.networking.examples.ExamplePackets;
import de.zannagh.eunomia.networking.handshake.ServerCapabilities;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.TitleScreen;

// End-to-end networking smoke driven by the Fabric Client Gametest API. Boots a real client into a
// singleplayer world (an in-process integrated server), then asserts the whole packet path worked
// over the actual Minecraft wire - the mixin-injected codecs, the dispatch mixins, both transports:
//   - S2C on join: the PERMISSION packet the server pushes was received.
//   - C2S -> S2C round trip: the client's PING was answered with a PONG.
//   - Capability handshake: the client detected the server runs Eunomia and reports it receives PING.
// The example packets are themselves "additional packets defined with minimal code", so a green run
// proves the public add-a-packet API works across the real network, not just in the loopback unit test.
public final class NetworkingSmokeTest implements FabricClientGameTest {

    // ~15 s at 20 TPS. The integrated server pushes on join, but world gen can stall early ticks.
    private static final int TIMEOUT_TICKS = 300;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitForScreen(TitleScreen.class);
        try (var singleplayer = context.worldBuilder().create()) {
            try {
                context.waitFor(client ->
                        ExampleClientHandlers.lastPermissionLevel != null
                                && ExampleClientHandlers.lastPongMessage != null
                                && CommunicationManager.serverCapabilities().isResolved(),
                        TIMEOUT_TICKS);
            } catch (AssertionError | RuntimeException e) {
                Eunomia.LOGGER.warn("[smoke/fcgt] exchange did not settle in {} ticks; asserting anyway",
                        TIMEOUT_TICKS);
            }

            if (ExampleClientHandlers.lastPermissionLevel == null) {
                throw new AssertionError("No PERMISSION packet received from the integrated server (S2C-on-join failed)");
            }
            if (ExampleClientHandlers.lastPongMessage == null) {
                throw new AssertionError("No PONG received - the client PING did not round-trip (C2S->S2C failed)");
            }
            ServerCapabilities caps = CommunicationManager.serverCapabilities();
            if (!caps.isPresent()) {
                throw new AssertionError("Capability handshake did not detect a Eunomia server");
            }
            if (!caps.supports(ExamplePackets.PING)) {
                throw new AssertionError("Server did not report a receiver for the PING channel; had: " + caps.receiverChannels());
            }
            Eunomia.LOGGER.info("[smoke/fcgt] networking smoke PASSED: permission={}, pong='{}', receivers={}",
                    ExampleClientHandlers.lastPermissionLevel,
                    ExampleClientHandlers.lastPongMessage,
                    caps.receiverChannels());
        }
    }
}
*///?}
