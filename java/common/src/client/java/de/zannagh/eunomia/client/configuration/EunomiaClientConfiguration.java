package de.zannagh.eunomia.client.configuration;

import de.zannagh.eunomia.client.gui.screens.EunomiaSettingsEntryPoint;
import de.zannagh.eunomia.client.toast.SyncUnavailableNotice;
import de.zannagh.eunomia.client.toast.SyncUnavailableNotices;
import de.zannagh.eunomia.configuration.EunomiaConfiguration;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The client half of eunomia's fluent configuration surface, obtained from {@code EunomiaClient.configure()}.
 * It is a superset of {@code Eunomia.configure()}: everything the common builder can express is chainable here
 * too (it delegates to a wrapped {@link EunomiaConfiguration}), plus the settings that need a client type -
 * nominating the {@link Screen} the entry button attaches to, and relabelling it with a {@link Component}.
 *
 * <pre>{@code
 * EunomiaClient.configure()
 *         .settingsButton(MyModOptionsScreen.class)
 *         .settingsButtonLabel(Component.translatable("mymod.options.sync"))
 *         .toasts(false)
 *         .apply();
 * }</pre>
 *
 * <p><b>Why this is a separate class and not just more methods on {@code Eunomia}.</b> {@code Eunomia} lives
 * in the common source set, which a dedicated server loads. Naming {@link Screen} or {@link Component} in a
 * method signature there would put a {@code net.minecraft.client.*} entry in {@code Eunomia}'s constant pool
 * and risk a {@code NoClassDefFoundError} the moment anything touched it server-side. Splitting the builder
 * along the source-set boundary makes that structurally impossible rather than merely unlikely.</p>
 *
 * <p><b>When to call it.</b> Before or after {@code EunomiaClient.init()}; both work. The entry button
 * reconciles itself against whatever this builder last said - moving it after it was installed removes it from
 * the old screen and adds it to the new one, and suppressing it after installation unregisters it.</p>
 *
 * <p>As with the common builder, <b>nothing happens until {@link #apply()}</b>, and a setter called after
 * {@code apply()} throws.</p>
 *
 * @since 0.3.0
 */
public final class EunomiaClientConfiguration {

    private final EunomiaConfiguration common = new EunomiaConfiguration();

    private @Nullable Class<? extends Screen> targetScreen;

    private @Nullable Component label;

    private final List<SyncUnavailableNotice> syncUnavailableNotices = new ArrayList<>();

    private boolean applied;

    /** Use {@code EunomiaClient.configure()}. */
    public EunomiaClientConfiguration() {
    }

    /**
     * Attaches eunomia's entry button to a screen of your choosing instead of vanilla's online options
     * screen, and enables the button if a previous call had suppressed it.
     *
     * <p>Screen matching is on the <em>exact runtime class</em>, so pass the concrete class the player
     * actually opens - a base class does not pick up its subclasses.</p>
     *
     * @param screenClass the concrete screen class to attach to.
     * @return this builder.
     */
    public EunomiaClientConfiguration settingsButton(Class<? extends Screen> screenClass) {
        checkNotApplied();
        targetScreen = Objects.requireNonNull(screenClass, "screenClass");
        common.settingsButton(true);
        return this;
    }

    /**
     * Turns eunomia's built-in entry button on or off without moving it. Pass {@code false} when your mod
     * opens {@code EunomiaSettingsScreen} from its own entry point and does not want a second button.
     * @param enabled {@code false} to suppress the built-in button.
     * @return this builder.
     */
    public EunomiaClientConfiguration settingsButton(boolean enabled) {
        checkNotApplied();
        common.settingsButton(enabled);
        return this;
    }

    /**
     * Reads better than {@code settingsButton(false)} at a call site that only suppresses.
     * @return this builder.
     */
    public EunomiaClientConfiguration suppressSettingsButton() {
        return settingsButton(false);
    }

    /**
     * Overrides the button's label. Defaults to eunomia's own translatable "Eunomia Settings"; eunomia ships
     * no lang files for consumers, so pass a component your own namespace resolves.
     * @param newLabel the label to draw.
     * @return this builder.
     */
    public EunomiaClientConfiguration settingsButtonLabel(Component newLabel) {
        checkNotApplied();
        label = Objects.requireNonNull(newLabel, "newLabel");
        return this;
    }

    /**
     * Whether eunomia may raise toasts, its own included.
     * @param enabled {@code false} to suppress eunomia's toasts wholesale.
     * @return this builder.
     */
    public EunomiaClientConfiguration toasts(boolean enabled) {
        checkNotApplied();
        common.toasts(enabled);
        return this;
    }

    /**
     * Supplies your mod's own wording for the "this server offers no eunomia sync" notification, replacing
     * eunomia's generic copy with something that names your mod and its setting - for example
     * {@code "Armor Hider can synchronise via Cloud. Go to Settings to enable it."}
     *
     * <p>Every mod that registers gets its own card; see {@link SyncUnavailableNotices} for why that is a
     * registry and not a single overridable slot. Keyed by your mod id, so calling this twice in the same
     * chain, or across re-inits, replaces rather than accumulates.</p>
     *
     * <p>Eunomia ships no lang files for consumer text, so resolve the component in your own namespace.</p>
     *
     * @param consumerId  your mod id.
     * @param description the line the player reads.
     * @return this builder.
     */
    public EunomiaClientConfiguration syncUnavailableNotice(String consumerId, Component description) {
        return syncUnavailableNotice(consumerId, null, description);
    }

    /**
     * As {@link #syncUnavailableNotice(String, Component)}, but overriding the headline too. Most consumers
     * want the default: the situation is the same whoever reports it, and only the advice is mod-specific.
     *
     * @param consumerId  your mod id.
     * @param title       the bold first line, or {@code null} for eunomia's generic one.
     * @param description the line the player reads.
     * @return this builder.
     */
    public EunomiaClientConfiguration syncUnavailableNotice(
            String consumerId,
            @Nullable Component title,
            Component description) {
        checkNotApplied();
        Objects.requireNonNull(consumerId, "consumerId");
        Objects.requireNonNull(description, "description");
        syncUnavailableNotices.add(new SyncUnavailableNotice(consumerId, title, description));
        return this;
    }

    /**
     * Your mod's default for the external relay opt-in. See {@link EunomiaConfiguration#externalFallback}.
     * @param value the default to ship, or {@code null} to clear a previously expressed opinion.
     * @return this builder.
     */
    public EunomiaClientConfiguration externalFallback(@Nullable Boolean value) {
        checkNotApplied();
        common.externalFallback(value);
        return this;
    }

    /**
     * Your mod's default relay address. See {@link EunomiaConfiguration#externalServerAddress}.
     * @param value the relay base address, or {@code null}/blank to clear a previously expressed opinion.
     * @return this builder.
     */
    public EunomiaClientConfiguration externalServerAddress(@Nullable String value) {
        checkNotApplied();
        common.externalServerAddress(value);
        return this;
    }

    /**
     * Your mod's default for preferring the relay. See {@link EunomiaConfiguration#preferExternalTransport}.
     * @param value the default to ship, or {@code null} to clear a previously expressed opinion.
     * @return this builder.
     */
    public EunomiaClientConfiguration preferExternalTransport(@Nullable Boolean value) {
        checkNotApplied();
        common.preferExternalTransport(value);
        return this;
    }

    /**
     * Drops every mod-level sync opinion before this chain's own values are written.
     * @return this builder.
     */
    public EunomiaClientConfiguration resetSyncDefaults() {
        checkNotApplied();
        common.resetSyncDefaults();
        return this;
    }

    /**
     * Commits every staged value. The button target and label are written first, so the single reconcile that
     * the enable flag triggers already sees the final target screen. Single-shot: this builder is spent
     * afterwards and any further setter call throws.
     */
    public void apply() {
        checkNotApplied();
        applied = true;
        if (targetScreen != null) {
            EunomiaSettingsEntryPoint.setTargetScreen(targetScreen);
        }
        if (label != null) {
            EunomiaSettingsEntryPoint.setLabel(label);
        }
        for (SyncUnavailableNotice notice : syncUnavailableNotices) {
            SyncUnavailableNotices.register(notice.consumerId(), notice.title(), notice.description());
        }
        common.apply();
    }

    private void checkNotApplied() {
        if (applied) {
            throw new IllegalStateException(
                    "This Eunomia client configuration was already applied. "
                            + "Start a new chain with EunomiaClient.configure().");
        }
    }
}
