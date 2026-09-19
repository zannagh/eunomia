//? if fcgt {
package de.zannagh.eunomia.smoke;

import de.zannagh.eunomia.Eunomia;
import de.zannagh.eunomia.client.gui.screens.EunomiaSettingsScreen;
import de.zannagh.eunomia.configuration.EunomiaSyncSettings;
import de.zannagh.eunomia.configuration.SyncSetting;
import de.zannagh.eunomia.networking.handshake.ServerSyncPolicy;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;

import java.util.EnumSet;

// The player-facing half of server-enforced settings: while the joined server declares a setting
// non-negotiable, that row's control must go dead and its reset button must stay inert.
//
// This is worth a runtime guard rather than a unit test because the failure it catches is a screen that
// still offers the control. A player who changes it writes an override the resolver then ignores, which
// from the other side of the screen is indistinguishable from eunomia dropping their setting - and no
// assertion about resolved values can see it, because the resolved value is correct either way.
//
// The server policy is injected rather than joined. What is under test is the screen, not the handshake,
// and EunomiaSyncSettings' policy source is bindable for exactly this reason. It lives outside
// SettingsUiSmokeTest so that file stays about the entry button and the override round trip.
final class EnforcedRowsSmoke {

    // The caption of a provenance button whose row the server has locked.
    private static final String LOCKED_SOURCE = "Locked by server";

    // An enforcing server: a value for all three settings AND all three declared non-negotiable. Both
    // halves are needed - enforcement over a setting the server has no value for locks nothing at all.
    private static final ServerSyncPolicy ENFORCING_POLICY = new ServerSyncPolicy(
            Boolean.TRUE, "http://relay.example", Boolean.TRUE, EnumSet.allOf(SyncSetting.class));

    private EnforcedRowsSmoke() {
    }

    // Asserts the three rows unlocked, then locked, then unlocked again. Asserting both states is what
    // makes the locked half mean something: a control that is dead for an unrelated reason fails the
    // unlocked assertion instead of quietly satisfying the locked one.
    static void run(ClientGameTestContext context, Screen parent) {
        try {
            openSettings(context, parent);
            assertRows(context, false);
            EunomiaSyncSettings.bindAdvertisedPolicySource(() -> ENFORCING_POLICY);
            openSettings(context, parent);
            assertRows(context, true);
            SmokeShots.shoot(context, "settings-screen-locked-by-server");
        } finally {
            // Restores the live capability view. Left bound, it would follow the client into the world
            // ServerSectionSmoke creates next and make its rung-3 assertions read an invented server.
            EunomiaSyncSettings.bindAdvertisedPolicySource(null);
        }
        openSettings(context, parent);
        assertRows(context, false);
        Eunomia.LOGGER.info("[smoke/ui] the player's three rows lock and unlock with the server's "
                + "enforcement set");
    }

    private static void assertRows(ClientGameTestContext context, boolean locked) {
        requireActive(context, "the Cloud Sync toggle",
                SmokeUi.require(context, "the Cloud Sync toggle", EnforcedRowsSmoke::isCloudSync), !locked);
        requireActive(context, "the Prefer Cloud Sync toggle",
                SmokeUi.require(context, "the Prefer Cloud Sync toggle", EnforcedRowsSmoke::isPrefer),
                !locked);
        assertAddress(context, locked);
        assertResetButtons(context, locked);
    }

    // The address field's enabled state read through canConsumeInput() - the predicate charTyped()
    // itself consults - rather than through a reflective look at a private field, which answers "could
    // not tell" on a rename and turns the assertion resting on it into one that cannot fail.
    private static void assertAddress(ClientGameTestContext context, boolean locked) {
        AbstractWidget address = SmokeUi.require(context, "the Cloud Sync Server field",
                "Cloud Sync Server"::equals);
        boolean editable = SmokeUi.acceptsTyping(context, address);
        if (editable == locked) {
            throw new AssertionError("The Cloud Sync Server field " + (editable ? "accepts" : "refuses")
                    + " typing while the server does " + (locked ? "" : "not ")
                    + "enforce the relay address; it should do the opposite");
        }
    }

    // Every locked row's second widget must read "Locked by server" and be unclickable. Counting the
    // captions covers the "it says the right thing" half; reading `active` off each of them covers the
    // half a player bumps into, and the two are set by different lines of code.
    private static void assertResetButtons(ClientGameTestContext context, boolean locked) {
        int expected = locked ? 3 : 0;
        int found = SmokeUi.count(context, LOCKED_SOURCE::equals);
        if (found != expected) {
            throw new AssertionError("Expected " + expected + " provenance button(s) reading '"
                    + LOCKED_SOURCE + "' while the server " + (locked ? "enforces all three settings"
                    : "enforces nothing") + "; found " + found + "; labels: " + SmokeUi.labels(context));
        }
        for (AbstractWidget widget : SmokeUi.widgets(context)) {
            if (LOCKED_SOURCE.equals(widget.getMessage().getString())) {
                requireActive(context, "a '" + LOCKED_SOURCE + "' reset button", widget, false);
            }
        }
    }

    private static void requireActive(ClientGameTestContext context, String what, AbstractWidget widget,
                                      boolean expected) {
        boolean active = context.computeOnClient(client -> widget.active);
        if (active != expected) {
            throw new AssertionError(what + " is " + (active ? "enabled" : "disabled") + " but should be "
                    + (expected ? "enabled" : "disabled") + "; labels: " + SmokeUi.labels(context));
        }
    }

    // A fresh screen instance every time: the rows derive their locked state in refresh(), which runs
    // on build and on every override, so a screen opened after the policy changed is the honest one to
    // look at. The cursor is parked in the corner so a screenshot is not covered by a tooltip.
    private static void openSettings(ClientGameTestContext context, Screen parent) {
        context.runOnClient(client ->
                client.setScreenAndShow(new EunomiaSettingsScreen(parent, client.options)));
        context.waitForScreen(EunomiaSettingsScreen.class);
        context.getInput().setCursorPos(2, 2);
        context.waitTicks(3);
    }

    private static boolean isCloudSync(String message) {
        return message.startsWith("Cloud Sync:");
    }

    private static boolean isPrefer(String message) {
        return message.startsWith("Prefer Cloud Sync:");
    }
}
//?}
