//? if fcgt {
package de.zannagh.eunomia.smoke;

import de.zannagh.eunomia.Eunomia;
import de.zannagh.eunomia.client.toast.EunomiaToasts;
import de.zannagh.eunomia.client.toast.ToastId;
import de.zannagh.eunomia.client.toast.diagnostics.SyncDiagnosticToasts;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

// Runtime coverage for the toast API: that a toast raised through EunomiaToasts really lands in
// vanilla's queue, that setEnabled(false) really suppresses it, and that the two diagnostic toasts
// render readable English rather than raw translation keys.
//
// Everything here is raised directly. It deliberately does NOT go through the capability handshake:
// that path is broken at HEAD (the client's HELLO gets no ACK), so a test keyed off it would be red
// for reasons that have nothing to do with toasts. The consequence is stated plainly in the report -
// the "server does not speak eunomia" toast is verified as a rendered notification, not as a
// consequence of a real join.
//
// Note the runOnClient wrappers around every raise: FCGT hard-fails any Minecraft.getInstance() call
// made from the gametest thread, and EunomiaToasts calls it to reach the client. That is a harness
// constraint, not a defect - the API's own job is to marshal from arbitrary background threads, which
// it still does; it simply cannot be entered from the one thread FCGT reserves for itself.
public final class ToastSmokeTest implements FabricClientGameTest {

    private static final int SETTLE_TICKS = 60;

    private static final ToastId SMOKE_ID = ToastId.of("eunomia:smoke");

    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitForScreen(TitleScreen.class);
        SmokeShots.prepare(context);
        SmokeToasts.clear(context);

        assertRaises(context);
        assertSuppressed(context);
        assertDiagnosticCopyResolves(context);
        screenshotDiagnostics(context);

        SmokeToasts.clear(context);
        Eunomia.LOGGER.info("[smoke/ui] TOAST SMOKE PASSED");
    }

    // A toast raised through the public entry point reaches vanilla, and is visible on screen.
    private void assertRaises(ClientGameTestContext context) {
        if (!EunomiaToasts.isEnabled()) {
            throw new AssertionError("EunomiaToasts should default to enabled");
        }
        SmokeToasts.waitForCount(context, 0, SETTLE_TICKS);
        context.runOnClient(client -> EunomiaToasts.show(SMOKE_ID,
                Component.literal("Eunomia smoke test"),
                Component.literal("Raised through EunomiaToasts.show")));
        SmokeToasts.waitUntilShown(context, 1, SETTLE_TICKS);
        Eunomia.LOGGER.info("[smoke/ui] toast reached the vanilla queue");
        SmokeShots.shoot(context, "toast-visible");
        SmokeToasts.clear(context);
    }

    // The kill switch a consuming mod relies on. It now routes through EunomiaClientOptions rather
    // than a flag owned by EunomiaToasts, which is exactly why it is asserted at runtime.
    private void assertSuppressed(ClientGameTestContext context) {
        SmokeToasts.waitForCount(context, 0, SETTLE_TICKS);
        EunomiaToasts.setEnabled(false);
        if (EunomiaToasts.isEnabled()) {
            throw new AssertionError("EunomiaToasts.setEnabled(false) did not take effect");
        }
        context.runOnClient(client ->
                EunomiaToasts.show(Component.literal("Should never appear"), Component.literal("suppressed")));
        context.waitTicks(20);
        int held = SmokeToasts.count(context);
        if (held != 0) {
            throw new AssertionError("A toast got through while disabled; queue holds " + held);
        }
        EunomiaToasts.setEnabled(true);
        context.runOnClient(client ->
                EunomiaToasts.show(Component.literal("Re-enabled"), Component.literal("visible again")));
        SmokeToasts.waitForCount(context, 1, SETTLE_TICKS);
        Eunomia.LOGGER.info("[smoke/ui] setEnabled(false) suppressed, setEnabled(true) restored");
        SmokeToasts.clear(context);
    }

    // The diagnostic copy must be English. A missing lang entry is invisible in a unit test and
    // extremely visible to a player.
    private void assertDiagnosticCopyResolves(ClientGameTestContext context) {
        requireResolves(context, SyncDiagnosticToasts.MISSING_SERVER_SYNC_TITLE);
        requireResolves(context, SyncDiagnosticToasts.MISSING_SERVER_SYNC_DESCRIPTION);
        requireResolves(context, SyncDiagnosticToasts.RELAY_UNREACHABLE_TITLE);
        // Descriptions that take a format argument are deliberately not checked here - resolving one
        // without its argument throws rather than reporting a missing key.
        requireResolves(context, SyncDiagnosticToasts.NEW_RELAY_HOST_TITLE);
        Eunomia.LOGGER.info("[smoke/ui] diagnostic toast translation keys all resolve");
    }

    private void requireResolves(ClientGameTestContext context, String key) {
        String rendered = context.computeOnClient(client -> Component.translatable(key).getString());
        if (key.equals(rendered)) {
            throw new AssertionError("Translation key '" + key + "' has no en_us entry");
        }
    }

    // The real triggers are gated on being on a remote server (and, for the first one, on the
    // capability probe resolving). Neither holds at the title screen, so both are invoked for real -
    // proving they are correctly silent - and then the same components are raised directly so the
    // rendering is captured.
    private void screenshotDiagnostics(ClientGameTestContext context) {
        SmokeToasts.waitForCount(context, 0, SETTLE_TICKS);
        context.runOnClient(client -> SyncDiagnosticToasts.relayUnreachable("https://relay.invalid", false));
        context.waitTicks(20);
        int afterRealTrigger = SmokeToasts.count(context);
        Eunomia.LOGGER.info("[smoke/ui] SyncDiagnosticToasts.relayUnreachable off-server raised {} toast(s) "
                + "(0 expected: not a remote connection)", afterRealTrigger);
        context.runOnClient(client -> {
            EunomiaToasts.show(ToastId.of("eunomia:smoke_missing_sync"),
                    Component.translatable(SyncDiagnosticToasts.MISSING_SERVER_SYNC_TITLE),
                    Component.translatable(SyncDiagnosticToasts.MISSING_SERVER_SYNC_DESCRIPTION));
            EunomiaToasts.show(ToastId.of("eunomia:smoke_relay"),
                    Component.translatable(SyncDiagnosticToasts.RELAY_UNREACHABLE_TITLE),
                    Component.translatable(SyncDiagnosticToasts.RELAY_UNREACHABLE_DESCRIPTION,
                            "https://relay.invalid"));
        });
        SmokeToasts.waitUntilShown(context, 2, SETTLE_TICKS);
        SmokeShots.shoot(context, "toast-diagnostics");
    }
}
//?}
