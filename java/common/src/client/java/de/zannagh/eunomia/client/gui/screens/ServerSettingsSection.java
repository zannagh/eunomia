package de.zannagh.eunomia.client.gui.screens;

import de.zannagh.eunomia.client.gui.factories.OptionElementFactory;
import de.zannagh.eunomia.client.settings.ServerSettingsClient;
import de.zannagh.eunomia.client.settings.ServerSettingsView;
import de.zannagh.eunomia.networking.admin.ServerSettingsStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The second half of {@link EunomiaSettingsScreen}: the Cloud Sync settings <em>the joined server</em>
 * applies to everybody on it, which an administrator may change from here.
 *
 * <p><strong>These are not the player's overrides and must never look like them.</strong> The rows
 * above are personal: they follow the player from server to server and answer "what do I want". These
 * answer "what does this server want", they are shared with every other player on it, and changing one
 * changes what strangers' clients do. Three things keep them apart on screen: they sit below a coloured
 * section heading naming the server rather than the player; every caption is prefixed so that a label
 * read on its own ("Server: Cloud Sync") still says whose setting it is; and the row of personal
 * settings has a reset button per row while these have a single explicit Save, because nothing here
 * takes effect until it has been sent and the server has agreed to it.
 *
 * <p><strong>{@code editable} is a rendering hint, never a permission.</strong> It decides whether the
 * controls are greyed out, and that is all it decides. The server re-checks on every write and refuses
 * one from a non-administrator regardless of what this client believed, so the worst a wrong hint can
 * do is show a live-looking control whose write comes back {@code DENIED}.
 *
 * <p><strong>Nothing here waits forever.</strong> The section is built showing "asking the server",
 * and {@code ServerSettingsClient} guarantees exactly one callback per exchange within
 * {@link ServerSettingsClient#RESPONSE_TIMEOUT_SECONDS} seconds - so a server that does not run eunomia
 * at all resolves to an {@code UNAVAILABLE} readout and an inert section rather than a spinner. The
 * section is not built at all when there is no server to ask.
 */
public final class ServerSettingsSection {

    private final Font font;

    private final ServerSettingsStatusLine status = new ServerSettingsStatusLine();

    private @Nullable AbstractWidget fallbackControl;

    private @Nullable AbstractWidget preferControl;

    private @Nullable EditBox addressBox;

    private @Nullable Button saveButton;

    private boolean fallbackValue;

    private boolean preferValue;

    private boolean editable;

    private boolean awaitingAnswer;

    private boolean closed;

    public ServerSettingsSection(Font font) {
        this.font = font;
    }

    /**
     * Whether there is a server to ask at all. In the main menu there is not, and a section of dead
     * controls explaining that would be worse than no section: the player is not being denied anything,
     * there is simply nothing on the other end. Single-player is deliberately <em>not</em> excluded -
     * the integrated server runs eunomia and its owner is its administrator, so the section works there
     * exactly as it does on a dedicated server.
     */
    public static boolean isAvailable() {
        return Minecraft.getInstance().getConnection() != null;
    }

    /**
     * Builds the six widgets of the section, in row order: heading + status, the two toggles, the
     * address field + Save.
     * @return the widgets, laid out two per row by the caller.
     */
    public List<AbstractWidget> build() {
        List<AbstractWidget> widgets = new ArrayList<>();
        widgets.add(new StringWidget(EunomiaSettingsOptions.CONTROL_WIDTH,
                EunomiaSettingsOptions.CONTROL_HEIGHT,
                Component.translatable("eunomia.settings.server.header")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                font));
        widgets.add(status.widget());
        OptionElementFactory factory = new OptionElementFactory(
                widgets::add, Minecraft.getInstance().options, EunomiaSettingsOptions.CONTROL_WIDTH);
        fallbackControl = addToggle(factory, widgets, "eunomia.settings.server.cloudSync",
                value -> fallbackValue = value);
        preferControl = addToggle(factory, widgets, "eunomia.settings.server.preferCloudSync",
                value -> preferValue = value);
        addAddressBox(widgets);
        addSaveButton(widgets);
        setControlsEnabled(false);
        status.pending();
        return widgets;
    }

    /**
     * Asks the server for its current values, and for whether this player may change them. Any answer
     * cached from earlier on this same connection is shown straight away - it is only ever a real
     * server answer, never a synthesised timeout - so re-opening the screen shows the values it showed
     * last time instead of three blanks while the round trip runs. The controls stay disabled until the
     * fresh answer lands, because the cache says nothing about whether the player may still write.
     */
    public void requestFromServer() {
        awaitingAnswer = true;
        setControlsEnabled(false);
        status.pending();
        ServerSettingsView cached = ServerSettingsClient.lastKnown();
        if (cached != null) {
            showValues(cached);
        }
        ServerSettingsClient.request(this::onAnswer);
    }

    /**
     * Stops the section touching widgets after the screen has gone away. An exchange in flight still
     * resolves - the callback fires either way - it just finds nothing left to update.
     */
    public void close() {
        closed = true;
    }

    private AbstractWidget addToggle(OptionElementFactory factory, List<AbstractWidget> widgets,
                                     String key, Consumer<Boolean> setter) {
        factory.addSimpleOptionAsWidget(factory.buildBooleanOption(
                Component.translatable(key),
                Component.translatable(key + ".tooltip"),
                Component.translatable(key + ".narration"),
                ServerSettingsSection::onOff,
                false,
                setter));
        return widgets.get(widgets.size() - 1);
    }

    private void addAddressBox(List<AbstractWidget> widgets) {
        EditBox box = new EditBox(font, 0, 0, EunomiaSettingsOptions.CONTROL_WIDTH,
                EunomiaSettingsOptions.CONTROL_HEIGHT,
                Component.translatable("eunomia.settings.server.cloudSyncServer"));
        box.setMaxLength(RelayAddress.MAX_LENGTH);
        // The same syntactic check the personal row uses, so both fields agree on what an address is.
        // It is a courtesy only: the server re-validates and may still answer INVALID_ADDRESS.
        // The personal row's constants, not a second copy of them: these two numbers carry a load-bearing
        // alpha byte (see EunomiaSettingsOptions#VALID_TEXT_COLOUR) and one field silently losing it while
        // the other kept it is precisely the divergence worth designing out.
        box.setResponder(typed -> box.setTextColor(
                typed.trim().isEmpty() || RelayAddress.isValid(typed.trim())
                        ? EunomiaSettingsOptions.VALID_TEXT_COLOUR
                        : EunomiaSettingsOptions.INVALID_TEXT_COLOUR));
        box.setTooltip(Tooltip.create(
                Component.translatable("eunomia.settings.server.cloudSyncServer.tooltip"),
                Component.translatable("eunomia.settings.server.cloudSyncServer.narration")));
        addressBox = box;
        widgets.add(box);
    }

    private void addSaveButton(List<AbstractWidget> widgets) {
        Button button = Button.builder(Component.translatable("eunomia.settings.server.save"), press -> save())
                .bounds(0, 0, EunomiaSettingsOptions.CONTROL_WIDTH, EunomiaSettingsOptions.CONTROL_HEIGHT)
                .build();
        button.setTooltip(Tooltip.create(
                Component.translatable("eunomia.settings.server.save.tooltip"),
                Component.translatable("eunomia.settings.server.save.narration")));
        saveButton = button;
        widgets.add(button);
    }

    private void save() {
        if (addressBox == null || awaitingAnswer) {
            return;
        }
        // Not an authorisation check - the server makes that call on the packet it receives. This only
        // avoids a round trip the client already knows will come back DENIED.
        if (!editable) {
            return;
        }
        awaitingAnswer = true;
        setControlsEnabled(false);
        status.saving();
        ServerSettingsClient.submit(fallbackValue, addressBox.getValue().trim(), preferValue, this::onAnswer);
    }

    /**
     * The hop onto the client thread. Callbacks land on the network thread, or on the thread the
     * five-second timeout is scheduled on, and everything below this line touches widgets.
     */
    private void onAnswer(ServerSettingsView view) {
        Minecraft.getInstance().execute(() -> applyAnswer(view));
    }

    private void applyAnswer(ServerSettingsView view) {
        if (closed) {
            return;
        }
        awaitingAnswer = false;
        editable = view.editable();
        boolean reachable = view.status() != ServerSettingsStatus.UNAVAILABLE;
        if (reachable) {
            // Shown even for a refusal: a DENIED or INVALID_ADDRESS answer still carries the server's
            // real current values, so snapping the controls back to them is what stops the screen
            // claiming a change that never happened.
            showValues(view);
        }
        status.show(view);
        setControlsEnabled(reachable && editable);
    }

    private void showValues(ServerSettingsView view) {
        fallbackValue = view.enableExternalFallback();
        preferValue = view.preferExternalTransport();
        if (fallbackControl != null) {
            OptionElementFactory.setBooleanValue(fallbackControl, fallbackValue);
        }
        if (preferControl != null) {
            OptionElementFactory.setBooleanValue(preferControl, preferValue);
        }
        if (addressBox != null) {
            addressBox.setValue(view.externalServerAddress());
        }
    }

    private void setControlsEnabled(boolean enabled) {
        if (fallbackControl != null) {
            fallbackControl.active = enabled;
        }
        if (preferControl != null) {
            preferControl.active = enabled;
        }
        if (addressBox != null) {
            addressBox.setEditable(enabled);
        }
        if (saveButton != null) {
            saveButton.active = enabled;
        }
    }

    private static Component onOff(boolean value) {
        return Component.translatable(value ? "eunomia.settings.on" : "eunomia.settings.off");
    }
}
