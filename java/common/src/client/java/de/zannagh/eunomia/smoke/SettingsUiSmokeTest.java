//? if fcgt {
package de.zannagh.eunomia.smoke;

import de.zannagh.eunomia.Eunomia;
import de.zannagh.eunomia.client.EunomiaClient;
import de.zannagh.eunomia.client.gui.screens.EunomiaSettingsScreen;
import de.zannagh.eunomia.configuration.EunomiaConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OnlineOptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

// The regression guard for eunomia's init-time screen hook, which until this test had never been
// executed by a running client at all - only reasoned about from bytecode.
//
// The load-bearing assertion is not the screenshot. It is that the "Eunomia Settings" button exists
// as a genuine child of vanilla's OnlineOptionsScreen after ScreenInitMixin ran, and that a physical
// mouse click at its coordinates opens EunomiaSettingsScreen. A widget that is merely drawn would
// pass a screenshot check and fail that one.
//
// Nothing here touches the network, so none of it depends on the capability handshake (broken at
// HEAD). The three settings rows are read at their inherited defaults, which is the state a player
// sees on a first launch.
public final class SettingsUiSmokeTest implements FabricClientGameTest {

    private static final String SETTINGS_LABEL = "Eunomia Settings";

    private static final String CUSTOM_LABEL = "Sync options (smoke)";

    private static final String TYPED_ADDRESS = "relay.example";

    private static final String NORMALISED_ADDRESS = "http://relay.example";

    private static final List<String> PROVENANCE_LABELS =
            List.of("Reset", "Your choice", "From server", "From mod pack", "Eunomia default");

    private Screen parent;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitForScreen(TitleScreen.class);
        SmokeShots.prepare(context);
        parent = context.computeOnClient(SmokeUi::currentScreen);
        clearOverrides(context);

        navigateToOnlineOptions(context);
        AbstractWidget button = requireEntryButton(context, SETTINGS_LABEL);
        Eunomia.LOGGER.info("[smoke/ui] entry button found on {} at ({},{}) {}x{} - the init hook ran",
                context.computeOnClient(client -> SmokeUi.currentScreen(client).getClass().getName()),
                button.getX(), button.getY(), button.getWidth(), button.getHeight());
        SmokeShots.shoot(context, "online-options-with-button");
        SmokeUi.hover(context, button);
        context.waitTicks(20);
        SmokeShots.shoot(context, "online-options-button-tooltip");

        openSettingsScreen(context, button);
        assertRows(context);
        SmokeShots.shoot(context, "settings-screen");
        overrideRoundTrip(context);

        assertSuppression(context);
        assertCustomLabel(context);
        assertAddressCommitsNormalised(context);
        // Last, because it is the only phase that needs a world; everything above is deliberately
        // connection-free and would only be slowed down by one.
        ServerSectionSmoke.run(context, parent);
        // FCGT asserts that a test hands the client back on the title screen, so a sibling test starts
        // from the same place this one did.
        context.runOnClient(client -> client.setScreenAndShow(parent));
        context.waitForScreen(TitleScreen.class);
        Eunomia.LOGGER.info("[smoke/ui] SETTINGS UI SMOKE PASSED");
    }

    // Title -> Options -> Online by clicking, which is what a player does and therefore what should
    // be tested. setScreen is kept as a fallback because a single version reshuffling the title menu
    // should not cost the whole matrix its coverage; whichever path ran is logged.
    private void navigateToOnlineOptions(ClientGameTestContext context) {
        boolean clicked = context.tryClickScreenButton("menu.options");
        if (clicked) {
            context.waitForScreen(OptionsScreen.class);
            clicked = context.tryClickScreenButton("options.online");
        }
        if (clicked) {
            context.waitForScreen(OnlineOptionsScreen.class);
            Eunomia.LOGGER.info("[smoke/ui] reached OnlineOptionsScreen by clicking through the title menu");
            return;
        }
        Eunomia.LOGGER.warn("[smoke/ui] title-menu navigation failed; falling back to setScreen");
        openOnlineOptions(context);
    }

    // Deliberately not ClientGameTestContext#setScreen: this build carries a global stonecutter
    // replacement that rewrites `.setScreen(` to `.setScreenAndShow(` from 1.21.9 on (the vanilla
    // rename), and it does not know that FCGT's own context method kept the old name. Going through
    // Minecraft is both correct on every version and the same call the entry button itself makes.
    private void openOnlineOptions(ClientGameTestContext context) {
        context.runOnClient(client ->
                client.setScreenAndShow(new OnlineOptionsScreen(parent, client.options)));
        context.waitForScreen(OnlineOptionsScreen.class);
        context.waitTicks(2);
    }

    private Optional<AbstractWidget> entryButton(ClientGameTestContext context, String label) {
        return SmokeUi.find(context, message -> message.equals(label));
    }

    private AbstractWidget requireEntryButton(ClientGameTestContext context, String label) {
        return entryButton(context, label).orElseThrow(() -> new AssertionError(
                "The '" + label + "' button was not added to OnlineOptionsScreen by the init hook; "
                        + "widgets present: " + SmokeUi.labels(context)));
    }

    // Clicking the injected button must open the settings screen. This is the half that proves the
    // widget is wired into the screen's input handling, not merely into its render list.
    private void openSettingsScreen(ClientGameTestContext context, AbstractWidget button) {
        SmokeUi.click(context, button);
        context.waitForScreen(EunomiaSettingsScreen.class);
        Eunomia.LOGGER.info("[smoke/ui] clicking the injected button opened EunomiaSettingsScreen");
    }

    private void assertRows(ClientGameTestContext context) {
        SmokeUi.require(context, "the Cloud Sync toggle", message -> message.startsWith("Cloud Sync:"));
        SmokeUi.require(context, "the Cloud Sync Server field", message -> message.equals("Cloud Sync Server"));
        SmokeUi.require(context, "the Prefer Cloud Sync toggle", message -> message.startsWith("Prefer Cloud Sync:"));
        int provenance = provenanceButtons(context);
        if (provenance != 3) {
            throw new AssertionError("Expected three provenance/reset buttons, found " + provenance
                    + "; labels: " + SmokeUi.labels(context));
        }
        SmokeUi.assertNoRawKeys(context, "the Eunomia settings screen");
        String title = context.computeOnClient(client -> SmokeUi.currentScreen(client).getTitle().getString());
        if (!SETTINGS_LABEL.equals(title)) {
            throw new AssertionError("Settings screen title did not resolve; got '" + title + "'");
        }
        Eunomia.LOGGER.info("[smoke/ui] three rows + three provenance buttons present, all lang keys resolved");
    }

    private int provenanceButtons(ClientGameTestContext context) {
        return SmokeUi.count(context, PROVENANCE_LABELS::contains);
    }

    private int resetButtons(ClientGameTestContext context) {
        return SmokeUi.count(context, "Reset"::equals);
    }

    // Toggle -> the row's second button becomes an enabled "Reset" -> pressing it hands the setting
    // back to the chain. Asserted by counting "Reset" labels rather than by widget order, because the
    // widget graph is walked breadth-first and its order is not part of any contract.
    //
    // Each mutation is verified on a freshly opened screen rather than in place. That is a workaround
    // for a real defect this test found (see checkRebuildDuplication): rebuilding the screen leaves the
    // previous generation of every widget behind, so an in-place read sees both the old and the new
    // state at once.
    private void overrideRoundTrip(ClientGameTestContext context) {
        if (resetButtons(context) != 0) {
            throw new AssertionError("Expected no override before toggling; found "
                    + resetButtons(context) + " Reset button(s)");
        }
        String before = cloudSyncLabel(context);
        SmokeUi.click(context, SmokeUi.require(context, "the Cloud Sync toggle", SettingsUiSmokeTest::isCloudSync));
        checkRebuildDuplication(context);
        // Taken on the screen instance the toggle landed on, NOT on a reopened one - a reopened screen
        // is a fresh instance and could never show the accumulation this shot exists to rule out. Cursor
        // parked first so the title and the Done button are not covered by the toggle's tooltip, since
        // those are the two widgets a reader is counting.
        context.getInput().setCursorPos(2, 2);
        context.waitTicks(3);
        SmokeShots.shoot(context, "settings-screen-after-toggle");
        reopenSettings(context);
        String after = cloudSyncLabel(context);
        if (before.equals(after)) {
            throw new AssertionError("Toggling Cloud Sync did not change its label (still '" + before + "')");
        }
        if (resetButtons(context) != 1) {
            throw new AssertionError("Overriding Cloud Sync did not turn its provenance button into Reset; "
                    + "labels: " + SmokeUi.labels(context));
        }
        SmokeShots.shoot(context, "settings-screen-overridden");
        SmokeUi.click(context, SmokeUi.require(context, "the Reset button", "Reset"::equals));
        reopenSettings(context);
        if (resetButtons(context) != 0) {
            throw new AssertionError("Reset did not clear the override; labels: " + SmokeUi.labels(context));
        }
        if (!before.equals(cloudSyncLabel(context))) {
            throw new AssertionError("Reset did not restore the inherited value; expected '" + before
                    + "', got '" + cloudSyncLabel(context) + "'");
        }
        Eunomia.LOGGER.info("[smoke/ui] override/reset round trip: '{}' -> '{}' -> '{}'", before, after, before);
    }

    // Now a hard failure, because the screen no longer accumulates: EunomiaSettingsOptions mutates the
    // widgets it already built instead of asking for a rebuild, so init() runs exactly once per screen
    // instance and there is exactly one generation of every widget.
    //
    // What used to happen: the options rebuilt the screen through Screen#rebuildWidgets after every
    // override, but OptionsSubScreen builds its HeaderAndFooterLayout once in the constructor and adds
    // to it from init(). rebuildWidgets clears the screen's widget lists, re-runs init, and then
    // re-publishes the whole (now doubled) layout - so the title, the options list and the Done button
    // all existed twice, and the stale copies were still hit-testable. It was visible to a player as a
    // tooltip that describes the value the setting had before they changed it.
    private void checkRebuildDuplication(ClientGameTestContext context) {
        int titles = 0;
        int done = 0;
        for (String label : SmokeUi.labels(context)) {
            if (SETTINGS_LABEL.equals(label)) {
                titles++;
            }
            if ("Done".equals(label)) {
                done++;
            }
        }
        if (titles > 1 || done > 1) {
            throw new AssertionError("DEFECT: one override left " + titles + " screen titles and " + done
                    + " Done buttons on EunomiaSettingsScreen - OptionsSubScreen's layout accumulates "
                    + "across rebuilds, so every override doubles the screen and stale widgets keep "
                    + "winning the hit test");
        }
        Eunomia.LOGGER.info("[smoke/ui] rebuildWidgets left the screen with a single widget generation");
    }

    private static boolean isCloudSync(String message) {
        return message.startsWith("Cloud Sync:");
    }

    private String cloudSyncLabel(ClientGameTestContext context) {
        return SmokeUi.require(context, "the Cloud Sync toggle", SettingsUiSmokeTest::isCloudSync)
                .getMessage().getString();
    }

    // A fresh screen instance, which is the only way to observe one clean widget generation while the
    // rebuild defect above stands. Parks the cursor in the empty top-left corner first so a screenshot
    // taken straight afterwards is not covered by whatever tooltip the last click left hovered.
    private void reopenSettings(ClientGameTestContext context) {
        context.runOnClient(client ->
                client.setScreenAndShow(new EunomiaSettingsScreen(parent, client.options)));
        context.waitForScreen(EunomiaSettingsScreen.class);
        context.getInput().setCursorPos(2, 2);
        context.waitTicks(3);
    }

    // The consumer-facing suppression guarantee, which had no runtime coverage before this.
    private void assertSuppression(ClientGameTestContext context) {
        EunomiaClient.configure().suppressSettingsButton().apply();
        openOnlineOptions(context);
        if (entryButton(context, SETTINGS_LABEL).isPresent()) {
            throw new AssertionError("suppressSettingsButton() left the button on OnlineOptionsScreen");
        }
        EunomiaClient.configure().settingsButton(true).apply();
        openOnlineOptions(context);
        requireEntryButton(context, SETTINGS_LABEL);
        Eunomia.LOGGER.info("[smoke/ui] suppressSettingsButton() removed the button, re-enabling restored it");
    }

    private void assertCustomLabel(ClientGameTestContext context) {
        EunomiaClient.configure().settingsButtonLabel(Component.literal(CUSTOM_LABEL)).apply();
        openOnlineOptions(context);
        requireEntryButton(context, CUSTOM_LABEL);
        EunomiaClient.configure()
                .settingsButtonLabel(Component.translatable(EunomiaSettingsScreen.TITLE_KEY))
                .apply();
        openOnlineOptions(context);
        requireEntryButton(context, SETTINGS_LABEL);
        Eunomia.LOGGER.info("[smoke/ui] settingsButtonLabel() relabelled the button and restored cleanly");
    }

    // What lands in the config is the normalised address, not the keystrokes. `relay.example` is the
    // interesting input because it is the one the client and :core used to disagree about: the screen
    // assumed https:// while RelayEndpoints - the thing that actually dials - assumes http://. The
    // field is filled through setValue rather than through synthetic typing because keyboard injection
    // is not reliable on every version in the matrix, and setValue runs the same responder a keystroke
    // does. Leaving the screen is what commits, so the config is only read after the screen is gone.
    private void assertAddressCommitsNormalised(ClientGameTestContext context) {
        reopenSettings(context);
        AbstractWidget field = SmokeUi.require(context, "the Cloud Sync Server field",
                message -> message.equals("Cloud Sync Server"));
        context.runOnClient(client -> ((EditBox) field).setValue(TYPED_ADDRESS));
        context.waitTicks(2);
        // Asserted here rather than anywhere else because setValue has just run the field's responder,
        // which is the only thing that ever sets this colour.
        SmokeUi.assertTextVisible(context, "the Cloud Sync Server field", field);
        context.runOnClient(client -> client.setScreenAndShow(parent));
        context.waitTicks(2);
        String stored = context.computeOnClient(client -> Eunomia.getConfig().externalServerAddressOverride());
        if (!NORMALISED_ADDRESS.equals(stored)) {
            throw new AssertionError("Typing '" + TYPED_ADDRESS + "' into the Cloud Sync Server field "
                    + "should have committed the normalised '" + NORMALISED_ADDRESS + "'; the config "
                    + "holds '" + stored + "'");
        }
        Eunomia.LOGGER.info("[smoke/ui] address field committed '{}' as '{}'", TYPED_ADDRESS, stored);
        clearOverrides(context);
    }

    // A leftover override from an earlier run would make the provenance assertions meaningless.
    private void clearOverrides(ClientGameTestContext context) {
        context.runOnClient(client -> {
            EunomiaConfig config = Eunomia.getConfig();
            config.setEnableExternalFallback(null);
            config.setPreferExternalTransport(null);
            config.setExternalServerAddress(null);
        });
    }
}
//?}
