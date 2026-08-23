package de.zannagh.eunomia.client.gui.screens;

import de.zannagh.eunomia.Eunomia;
import de.zannagh.eunomia.client.gui.factories.OptionElementFactory;
import de.zannagh.eunomia.client.settings.SyncSettingSource;
import de.zannagh.eunomia.configuration.EunomiaConfig;
import de.zannagh.eunomia.configuration.EunomiaSyncSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Builds the three option rows of {@link EunomiaSettingsScreen}, kept apart from the screen so the
 * screen file stays about layout and lifecycle and this one about the settings themselves.
 *
 * <p>Every row is a pair: the control, plus a <em>reset</em> button that clears the player's override
 * and hands the setting back to the precedence chain. That pairing is the whole point. The three
 * settings are not booleans and a string, they are <em>overrides</em> that may be absent, and a plain
 * on/off toggle can only ever express two of the three states. The reset button makes "inherit"
 * reachable, and its enabled/disabled state makes it legible: it is only clickable while an override
 * exists, so a greyed-out reset button <em>is</em> the "you are following someone else" indicator.
 * The value text on each control names the source outright ("ON - from server"), and the address
 * field shows the inherited address as a greyed-out hint whenever it is left empty.
 *
 * <p><strong>Why nothing here rebuilds the screen.</strong> Changing one override moves that row's
 * provenance, so labels, tooltips and reset states all have to be recomputed - and the obvious way to
 * do that, {@code Screen#rebuildWidgets}, is wrong on an {@code OptionsSubScreen}. That base class
 * builds its {@code HeaderAndFooterLayout} once, in its constructor, and <em>adds</em> to it from
 * {@code init()}; {@code rebuildWidgets} clears only the screen's own widget lists before re-running
 * {@code init} and re-publishing the whole, by then doubled, layout. One toggle produced two titles,
 * two option lists and two Done buttons, with the stale generation still winning hit tests and still
 * rendering the tooltip of the value the player had just changed. Vanilla never notices because
 * vanilla never calls {@code rebuildWidgets} on an options screen. So this class keeps hold of the six
 * widgets it built and {@link #refresh()}es their captions, tooltips and enabled states in place. No
 * widget is ever created, moved or removed after {@code build()}, which makes accumulation
 * structurally impossible rather than merely absent - and it preserves scroll position, keyboard focus
 * and half-typed text across a toggle, which even a correct rebuild would have thrown away.
 */
public final class EunomiaSettingsOptions {

    /** Half a vanilla options row, so a control and its reset button sit side by side. */
    public static final int CONTROL_WIDTH = 150;

    static final int CONTROL_HEIGHT = 20;

    /**
     * The colour of legible text in an {@link EditBox}, <strong>alpha byte included</strong>.
     *
     * <p>The leading {@code FF} is not decoration. Up to 1.21.4 an {@code EditBox}'s text colour was a
     * bare RGB value and {@code Font} forced it opaque on the way out ("if the top alpha bits are zero,
     * OR in full alpha"), so {@code 0xE0E0E0} rendered as light grey. From 1.21.5 the field became a
     * full ARGB value that is passed straight through, and the very same {@code 0xE0E0E0} is alpha
     * {@code 00} - completely invisible. Vanilla's own default moved from {@code 14737632} to
     * {@code -2039584} in exactly that release for exactly this reason.
     *
     * <p>Written with explicit alpha rather than behind a stonecutter gate because the qualified form is
     * correct on <em>both</em> paths: the old one keeps an alpha it was given, and only substitutes one
     * when none is there.
     */
    static final int VALID_TEXT_COLOUR = 0xFFE0E0E0;

    /** The "this is not a dialable address" colour; alpha byte mandatory, see {@link #VALID_TEXT_COLOUR}. */
    static final int INVALID_TEXT_COLOUR = 0xFFFF5555;

    private final Font font;

    private @Nullable EditBox addressBox;

    private @Nullable AbstractWidget cloudSyncControl;

    private @Nullable AbstractWidget preferControl;

    private @Nullable Button cloudSyncReset;

    private @Nullable Button addressReset;

    private @Nullable Button preferReset;

    public EunomiaSettingsOptions(Font font) {
        this.font = font;
    }

    /**
     * Builds the widgets, in row order: control, reset, control, reset, control, reset. Called exactly
     * once per screen instance; every later change is a {@link #refresh()} of these same objects.
     * @return the six widgets making up the three rows.
     */
    public List<AbstractWidget> build() {
        List<AbstractWidget> widgets = new ArrayList<>();
        OptionElementFactory factory = new OptionElementFactory(
                widgets::add, Minecraft.getInstance().options, CONTROL_WIDTH);
        addCloudSyncRow(factory, widgets);
        addAddressRow(widgets);
        addPreferRow(factory, widgets);
        refresh();
        return widgets;
    }

    /**
     * Writes the pending relay address and flushes every override to disk. Invoked when the screen goes
     * away; the two toggles already wrote themselves through, this is what makes them durable.
     */
    public void commit() {
        if (addressBox != null) {
            String typed = addressBox.getValue().trim();
            if (typed.isEmpty()) {
                Eunomia.getConfig().setExternalServerAddress(null);
            } else if (RelayAddress.isValid(typed)) {
                // Stored normalised, exactly as the server stores it and the transport dials it, so the
                // value read back out of this field later is the value that gets used.
                Eunomia.getConfig().setExternalServerAddress(RelayAddress.normalize(typed));
            }
            // An unparseable address is simply not committed: the previous override (or "inherit")
            // survives, which is friendlier than persisting a value the transport can never dial.
        }
        Eunomia.CONFIG_PROVIDER.saveCurrent();
    }

    /**
     * Re-derives every caption, tooltip and enabled state from the current config. Cheap enough to run
     * on any change and deliberately blind to which setting moved, because the four-level chain means
     * one write can in principle change how any row reads.
     */
    public void refresh() {
        SyncSettingSource fallback = SyncSettingSource.forExternalFallback();
        boolean fallbackValue = EunomiaSyncSettings.externalFallbackEnabled();
        setToggle(cloudSyncControl, fallbackValue,
                "eunomia.settings.cloudSync", describe(onOff(fallbackValue), fallback));
        updateReset(cloudSyncReset, fallback, "eunomia.settings.cloudSync");

        SyncSettingSource address = SyncSettingSource.forExternalServerAddress();
        refreshAddress(address);
        updateReset(addressReset, address, "eunomia.settings.cloudSyncServer");

        SyncSettingSource prefer = SyncSettingSource.forPreferExternalTransport();
        boolean preferValue = EunomiaSyncSettings.preferExternalTransport();
        setToggle(preferControl, preferValue,
                "eunomia.settings.preferCloudSync", describe(onOff(preferValue), prefer));
        updateReset(preferReset, prefer, "eunomia.settings.preferCloudSync");
    }

    private void addCloudSyncRow(OptionElementFactory factory, List<AbstractWidget> widgets) {
        factory.addSimpleOptionAsWidget(factory.buildBooleanOption(
                Component.translatable("eunomia.settings.cloudSync"),
                tooltipFor("eunomia.settings.cloudSync", Component.empty()),
                Component.translatable("eunomia.settings.cloudSync.narration"),
                this::onOff,
                EunomiaSyncSettings.externalFallbackEnabled(),
                value -> apply(config -> config.setEnableExternalFallback(value))));
        cloudSyncControl = widgets.get(widgets.size() - 1);
        cloudSyncReset = resetButton(config -> config.setEnableExternalFallback(null));
        widgets.add(cloudSyncReset);
    }

    private void addPreferRow(OptionElementFactory factory, List<AbstractWidget> widgets) {
        factory.addSimpleOptionAsWidget(factory.buildBooleanOption(
                Component.translatable("eunomia.settings.preferCloudSync"),
                tooltipFor("eunomia.settings.preferCloudSync", Component.empty()),
                Component.translatable("eunomia.settings.preferCloudSync.narration"),
                this::onOff,
                EunomiaSyncSettings.preferExternalTransport(),
                value -> apply(config -> config.setPreferExternalTransport(value))));
        preferControl = widgets.get(widgets.size() - 1);
        preferReset = resetButton(config -> config.setPreferExternalTransport(null));
        widgets.add(preferReset);
    }

    private void addAddressRow(List<AbstractWidget> widgets) {
        String override = Eunomia.getConfig().externalServerAddressOverride();
        EditBox box = new EditBox(font, 0, 0, CONTROL_WIDTH, CONTROL_HEIGHT,
                Component.translatable("eunomia.settings.cloudSyncServer"));
        box.setMaxLength(RelayAddress.MAX_LENGTH);
        box.setValue(override == null ? "" : override);
        box.setResponder(typed -> {
            box.setTextColor(typed.trim().isEmpty() || RelayAddress.isValid(typed.trim())
                    ? VALID_TEXT_COLOUR : INVALID_TEXT_COLOUR);
            refreshAddress(SyncSettingSource.forExternalServerAddress());
        });
        addressBox = box;
        widgets.add(box);
        // Clearing the override alone would leave the player's text sitting in the box, which commit()
        // would then write straight back on close - so this reset clears both halves of the state.
        addressReset = resetButton(config -> {
            config.setExternalServerAddress(null);
            box.setValue("");
        });
        widgets.add(addressReset);
    }

    /**
     * The second half of a row. It doubles as the provenance readout: while the setting is inherited it
     * is a disabled label naming the rung it comes from ("From server"), and the moment the player
     * overrides the setting it turns into an enabled "Reset" that hands it back. One widget, both
     * states, no room for the player to wonder which of the two a greyed toggle means. Its caption and
     * tooltip are filled in by {@link #refresh()}, which runs before the screen is ever drawn.
     */
    private Button resetButton(Consumer<EunomiaConfig> clear) {
        return Button.builder(Component.empty(), press -> apply(clear))
                .bounds(0, 0, CONTROL_WIDTH, CONTROL_HEIGHT)
                .build();
    }

    private void apply(Consumer<EunomiaConfig> mutation) {
        mutation.accept(Eunomia.getConfig());
        // Synchronous, unlike the deferred rebuild this replaced. A press is dispatched while the
        // screen is walking its widget lists, so anything that adds or removes a widget has to wait for
        // the next tick - but refresh() only writes captions, tooltips and flags on widgets that are
        // already there, which is safe mid-walk and, being immediate, leaves no frame in which a
        // tooltip still describes the value the player just changed.
        refresh();
    }

    private void refreshAddress(SyncSettingSource source) {
        if (addressBox == null) {
            return;
        }
        String effective = EunomiaSyncSettings.externalServerAddress();
        // Empty field + greyed inherited address as the hint: that is what "inherit" looks like here.
        addressBox.setHint(Component.literal(effective).withStyle(ChatFormatting.DARK_GRAY));
        addressBox.setTooltip(Tooltip.create(
                addressTooltip(addressBox.getValue(), effective, source),
                Component.translatable("eunomia.settings.cloudSyncServer.narration")));
    }

    /**
     * The address tooltip, plus - while the typed text would be stored as something other than itself -
     * a line naming the exact string that will be saved. That line is the screen's answer to "a bare
     * host becomes what, exactly": it shows the {@code http://} that {@code RelayAddresses} will add,
     * rather than leaving the player to assume a scheme.
     */
    private static MutableComponent addressTooltip(String typed, String effective, SyncSettingSource source) {
        MutableComponent tooltip = tooltipFor("eunomia.settings.cloudSyncServer",
                describe(Component.literal(effective), source));
        String trimmed = typed.trim();
        if (!trimmed.isEmpty() && RelayAddress.isValid(trimmed)) {
            String normalized = RelayAddress.normalize(trimmed);
            if (!normalized.equals(trimmed)) {
                tooltip.append("\n\n").append(Component
                        .translatable("eunomia.settings.cloudSyncServer.normalized", normalized)
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        return tooltip;
    }

    private static void setToggle(@Nullable AbstractWidget control, boolean value,
                                  String settingKey, Component effective) {
        if (control == null) {
            return;
        }
        // Order matters: setValue re-runs the option's own tooltip supplier, which still holds the
        // placeholder handed to it at build time, so the real tooltip has to be written afterwards.
        OptionElementFactory.setBooleanValue(control, value);
        control.setTooltip(Tooltip.create(tooltipFor(settingKey, effective),
                Component.translatable(settingKey + ".narration")));
    }

    private static void updateReset(@Nullable Button button, SyncSettingSource source, String settingKey) {
        if (button == null) {
            return;
        }
        boolean overridden = source.isPlayerOverride();
        button.setMessage(overridden ? Component.translatable("eunomia.settings.reset") : source.label());
        button.active = overridden;
        button.setTooltip(Tooltip.create(
                Component.translatable(overridden
                        ? "eunomia.settings.reset.tooltip"
                        : "eunomia.settings.reset.tooltip.inherited", Component.translatable(settingKey)),
                Component.translatable("eunomia.settings.reset.narration")));
    }

    private Component onOff(boolean value) {
        return Component.translatable(value ? "eunomia.settings.on" : "eunomia.settings.off");
    }

    static MutableComponent tooltipFor(String settingKey, Component effective) {
        return Component.translatable(settingKey + ".tooltip").append("\n\n").append(effective);
    }

    static MutableComponent describe(Component value, SyncSettingSource source) {
        return Component.translatable("eunomia.settings.effective", value, source.label())
                .withStyle(ChatFormatting.GRAY);
    }
}
