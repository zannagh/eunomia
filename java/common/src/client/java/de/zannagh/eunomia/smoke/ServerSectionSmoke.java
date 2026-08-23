//? if fcgt {
package de.zannagh.eunomia.smoke;

import de.zannagh.eunomia.Eunomia;
import de.zannagh.eunomia.client.gui.screens.EunomiaSettingsScreen;
import de.zannagh.eunomia.client.settings.ServerSettingsClient;
import de.zannagh.eunomia.client.settings.ServerSettingsView;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

import java.util.List;
import java.util.Optional;
import java.util.Set;

// The runtime guard for the "This Server" half of EunomiaSettingsScreen - the admin block that shows
// the joined server's own Cloud Sync policy and, for an administrator, lets it be changed.
//
// It lives in its own file, and joins its own world, because everything SettingsUiSmokeTest asserts is
// deliberately connection-free: the personal rows are readable from the main menu, and keeping them
// there is what makes them fast and what makes their failures unambiguous. This section cannot be
// asserted from there at all - ServerSettingsSection#isAvailable() is false with no connection, and a
// screen built in the main menu correctly has no server block. So the only honest place to test it is
// inside a world.
//
// Singleplayer is the interesting case rather than a compromise for one: the integrated server runs
// eunomia, and MinecraftServer#isSingleplayerOwner makes the player op level 4, so ServerUtil#isAdmin
// answers yes and the section must come back EDITABLE. A read-only readout here would mean either the
// admin exchange or the permission resolution is broken, which is exactly what this asserts.
final class ServerSectionSmoke {

    private static final String HEADER = "This Server";

    private static final String FALLBACK_CAPTION = "Server: Cloud Sync";

    private static final String PREFER_CAPTION = "Server: Prefer Relay";

    private static final String ADDRESS_CAPTION = "Server: Relay Address";

    // The four captions a provenance/reset button can carry, and the one an untouched server must
    // produce. See assertPlayerRowsFallThrough.
    private static final String FRAMEWORK_SOURCE = "Eunomia default";

    private static final String SERVER_SOURCE = "From server";

    private static final List<String> PROVENANCE_LABELS =
            List.of("Reset", "Your choice", SERVER_SOURCE, "From mod pack", FRAMEWORK_SOURCE);

    private static final String SAVE_CAPTION = "Save to Server";

    private static final String STATUS_PENDING = "Asking the server...";

    private static final String STATUS_SAVING = "Sending...";

    private static final String STATUS_ADMIN = "You can change these";

    private static final String STATUS_UNAVAILABLE = "No answer";

    // Every string ServerSettingsStatusLine can put on screen. Matching against the set is what turns
    // "the status line resolved" into an assertion rather than an eyeball: a missing lang entry leaves
    // the raw `eunomia.settings.server.status.*` key there, which is in no sense a member of this set.
    private static final Set<String> STATUS_LABELS = Set.of(
            STATUS_PENDING, STATUS_SAVING, STATUS_ADMIN, "Read-only",
            "Saved on the server", "Server refused", "Address rejected", STATUS_UNAVAILABLE);

    // ServerSettingsClient gives up after 5 s and synthesises an UNAVAILABLE answer, so a settled
    // status is guaranteed well inside this. The budget is generous because world gen can eat the
    // early ticks on a cold run.
    private static final int STATUS_ATTEMPTS = 60;

    private static final int STATUS_INTERVAL_TICKS = 5;

    private ServerSectionSmoke() {
    }

    // Creates a world, opens the settings screen inside it and asserts the whole section. Hands the
    // client back on the title screen, which is where it was found.
    static void run(ClientGameTestContext context, Screen parent) {
        try (var singleplayer = context.worldBuilder().create()) {
            openSettings(context, parent);
            requireSection(context);
            String status = awaitSettledStatus(context);
            assertStatus(context, status);
            assertControlsEnabled(context);
            assertPlayerRowsFallThrough(context);
            SmokeUi.assertNoRawKeys(context, "the This Server section");
            SmokeShots.shoot(context, "settings-screen-server-section");
            // The answer itself, not just the status it produced. Logged rather than asserted because the
            // values belong to whatever config the run directory happens to hold - but without them a
            // blank-looking control in the screenshot is indistinguishable from a control that failed to
            // be filled in, which is a distinction worth having in the log.
            ServerSettingsView answer = context.computeOnClient(client -> ServerSettingsClient.lastKnown());
            Eunomia.LOGGER.info("[smoke/ui] This Server section present and editable in singleplayer; "
                    + "status line reads '{}'; server answered {}; the address field holds '{}'",
                    status, answer, addressFieldValue(context));
            // Leaving the screen before the world goes away keeps removed() - and the config flush it
            // performs - on a live client rather than on one already tearing the level down.
            context.runOnClient(client -> client.setScreenAndShow(null));
            context.waitTicks(2);
        }
        context.waitForScreen(TitleScreen.class);
    }

    // What the server relay field is actually holding, as opposed to what the server said. The two
    // disagreeing is the difference between "this run's config has no relay" and "the answer never
    // reached the widget".
    private static String addressFieldValue(ClientGameTestContext context) {
        AbstractWidget field = SmokeUi.require(context, "the server relay address field",
                ADDRESS_CAPTION::equals);
        return context.computeOnClient(client -> ((EditBox) field).getValue());
    }

    private static void openSettings(ClientGameTestContext context, Screen parent) {
        context.runOnClient(client ->
                client.setScreenAndShow(new EunomiaSettingsScreen(parent, client.options)));
        context.waitForScreen(EunomiaSettingsScreen.class);
        // Nothing hovered, so the shot taken below is not covered by a tooltip.
        context.getInput().setCursorPos(2, 2);
        context.waitTicks(3);
    }

    // The section is six widgets; all six have to be reachable from the open screen, because a widget
    // that is built but never published is exactly the defect a screenshot cannot see.
    private static void requireSection(ClientGameTestContext context) {
        List<String> labels = SmokeUi.labels(context);
        if (!labels.contains(HEADER)) {
            throw new AssertionError("The '" + HEADER + "' section is missing from EunomiaSettingsScreen "
                    + "while in singleplayer, where the integrated server runs eunomia and the world "
                    + "owner is its administrator; labels present: " + labels);
        }
        SmokeUi.require(context, "the server Cloud Sync toggle", ServerSectionSmoke::isFallback);
        SmokeUi.require(context, "the server Prefer Relay toggle", ServerSectionSmoke::isPrefer);
        SmokeUi.require(context, "the server relay address field", ADDRESS_CAPTION::equals);
        SmokeUi.require(context, "the Save to Server button", SAVE_CAPTION::equals);
        requireStatusWidget(context);
    }

    // Rung 3 of the precedence chain, asserted end to end and from inside a world - the only place it
    // can be, because the server rung only exists on a connection.
    //
    // The integrated server here runs eunomia with an untouched EunomiaServerConfig. At schema 1.1.0
    // every one of its three "opinion" fields is a boxed nullable that starts null, so the policy it
    // advertises is ServerSyncPolicy.UNKNOWN - it said nothing. Nothing said is not the same as "said
    // the default", so the player's own three rows must fall past the server rung entirely and land on
    // the framework rung, reading "Eunomia default".
    //
    // Before the fix an untouched config advertised a fully-populated policy, so these same three rows
    // read "From server" against a server that had never been configured at all - which is what made a
    // player's Reset button hand their setting to an opinion nobody had ever expressed. Asserting the
    // absence of "From server" is therefore the load-bearing half: the count alone would still pass.
    private static void assertPlayerRowsFallThrough(ClientGameTestContext context) {
        List<String> labels = SmokeUi.labels(context);
        List<String> provenance = labels.stream().filter(PROVENANCE_LABELS::contains).toList();
        if (provenance.size() != 3) {
            throw new AssertionError("Expected the player's three provenance buttons on the settings "
                    + "screen while in a world; found " + provenance.size() + " " + provenance
                    + "; labels: " + labels);
        }
        long fromServer = provenance.stream().filter(SERVER_SOURCE::equals).count();
        if (fromServer > 0) {
            throw new AssertionError("DEFECT: " + fromServer + " of the player's three provenance buttons "
                    + "read '" + SERVER_SOURCE + "' against an untouched server config. An untouched "
                    + "EunomiaServerConfig holds no opinion and must advertise ServerSyncPolicy.UNKNOWN, "
                    + "so every row should fall through to '" + FRAMEWORK_SOURCE + "'; they read "
                    + provenance);
        }
        for (String label : provenance) {
            if (!FRAMEWORK_SOURCE.equals(label)) {
                throw new AssertionError("The player's provenance buttons should all read '"
                        + FRAMEWORK_SOURCE + "' here - no override was set, the consuming mod ships no "
                        + "defaults, and the server holds no opinion - but one reads '" + label
                        + "'; they read " + provenance);
            }
        }
        Eunomia.LOGGER.info("[smoke/ui] server-section: the player's three provenance buttons all read "
                + "'{}' against an untouched server config - rung 3 falls through as UNKNOWN", FRAMEWORK_SOURCE);
    }

    private static void assertStatus(ClientGameTestContext context, String status) {
        if (STATUS_ADMIN.equals(status)) {
            return;
        }
        if (STATUS_UNAVAILABLE.equals(status)) {
            throw new AssertionError("The This Server section got no answer from the integrated server "
                    + "within " + STATUS_ATTEMPTS * STATUS_INTERVAL_TICKS + " ticks - the admin settings "
                    + "exchange did not round-trip in singleplayer; labels: " + SmokeUi.labels(context));
        }
        throw new AssertionError("The world owner is the singleplayer owner and therefore an eunomia "
                + "administrator, so the status line should read '" + STATUS_ADMIN + "'; it reads '"
                + status + "'");
    }

    // active/editable, asserted on the widgets rather than inferred from the status text: the status
    // line and the enabled state are set from the same answer but by two different code paths, and it
    // is the second of them a player actually bumps into.
    private static void assertControlsEnabled(ClientGameTestContext context) {
        requireActive(context, "the server Cloud Sync toggle",
                SmokeUi.require(context, "the server Cloud Sync toggle", ServerSectionSmoke::isFallback));
        requireActive(context, "the server Prefer Relay toggle",
                SmokeUi.require(context, "the server Prefer Relay toggle", ServerSectionSmoke::isPrefer));
        requireActive(context, "the Save to Server button",
                SmokeUi.require(context, "the Save to Server button", SAVE_CAPTION::equals));
        AbstractWidget address = SmokeUi.require(context, "the server relay address field",
                ADDRESS_CAPTION::equals);
        SmokeUi.assertTextVisible(context, "the server relay address field", address);
        Optional<Boolean> editable = SmokeUi.editableFlag(address);
        if (editable.isEmpty()) {
            // Not a failure: EditBox's editable flag is a private field whose name is not part of any
            // contract, and losing the whole variant over a rename would be worse than saying so.
            Eunomia.LOGGER.warn("[smoke/ui] could not read the editable flag off {} on this version; "
                    + "the address field's enabled state is unasserted here",
                    address.getClass().getName());
        } else if (!editable.get()) {
            throw new AssertionError("The server relay address field is not editable for an administrator");
        }
    }

    private static void requireActive(ClientGameTestContext context, String what, AbstractWidget widget) {
        boolean active = context.computeOnClient(client -> widget.active);
        if (!active) {
            throw new AssertionError(what + " is disabled, but the world owner is an administrator and "
                    + "the status line says so; labels: " + SmokeUi.labels(context));
        }
    }

    // Polls rather than waitFor: the predicate has to read widget labels, and those are read through
    // computeOnClient, which cannot be nested inside a waitFor predicate already running on the client.
    private static String awaitSettledStatus(ClientGameTestContext context) {
        String status = statusLabel(context);
        for (int attempt = 0; attempt < STATUS_ATTEMPTS && isWaiting(status); attempt++) {
            context.waitTicks(STATUS_INTERVAL_TICKS);
            status = statusLabel(context);
        }
        return status;
    }

    private static boolean isWaiting(String status) {
        return STATUS_PENDING.equals(status) || STATUS_SAVING.equals(status);
    }

    private static String statusLabel(ClientGameTestContext context) {
        for (String label : SmokeUi.labels(context)) {
            if (STATUS_LABELS.contains(label)) {
                return label;
            }
        }
        return null;
    }

    private static void requireStatusWidget(ClientGameTestContext context) {
        if (statusLabel(context) == null) {
            throw new AssertionError("The This Server status line did not resolve to any known state; "
                    + "labels present: " + SmokeUi.labels(context));
        }
    }

    private static boolean isFallback(String message) {
        return message.startsWith(FALLBACK_CAPTION + ":");
    }

    private static boolean isPrefer(String message) {
        return message.startsWith(PREFER_CAPTION + ":");
    }
}
//?}
