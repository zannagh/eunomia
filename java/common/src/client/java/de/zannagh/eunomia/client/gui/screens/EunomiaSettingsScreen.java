package de.zannagh.eunomia.client.gui.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The player-facing settings screen for eunomia's external ("Cloud Sync") transport. Its upper half is
 * three rows of the player's own overrides over the four-level precedence chain, each pairing its
 * control with the reset button that gives the setting back to the chain (see
 * {@link EunomiaSettingsOptions}). Its lower half, present only while there is a server to ask, is that
 * server's own settings (see {@link ServerSettingsSection}).
 *
 * <p>It extends {@code OptionsSubScreen} so the vanilla escape/back semantics, the title bar and the
 * "Done" footer come for free and keep working as vanilla changes them. That base class is however
 * two rather different classes across the supported range: from 1.21 it is abstract and drives a
 * {@code HeaderAndFooterLayout} plus an {@code OptionsList} through {@code addOptions()}, while on
 * 1.20.1 it is a bare concrete class with neither, so the pre-1.21 branch builds the scrollable body
 * and the footer button itself on top of {@code WidgetList}. Both branches feed on the exact same
 * widget list.
 *
 * <p><strong>{@code init()} runs at most once per instance, and nothing here ever asks for a second
 * run.</strong> {@code OptionsSubScreen} accumulates into a layout it built in its constructor, so a
 * {@code rebuildWidgets} would leave a second title, a second option list and a second Done button
 * behind, all still hit-testable. Provenance changes are therefore reflected by mutating the existing
 * widgets ({@link EunomiaSettingsOptions#refresh()}), never by rebuilding the screen.
 *
 * <p>Overrides are flushed to disk in {@link #removed()} rather than on every keystroke: the two
 * toggles write through to the config object immediately (so the labels can be rebuilt from it), but
 * a settings file rewrite per click is exactly the synchronous disk I/O on the render thread that
 * {@code OptionElementFactory} warns about for sliders.
 */
public class EunomiaSettingsScreen extends OptionsSubScreen {

    /** Translation key of the screen title, also reused as the default entry-button label. */
    public static final String TITLE_KEY = "eunomia.settings.title";

    private final EunomiaSettingsOptions rows;

    private final @Nullable ServerSettingsSection serverSection;

    public EunomiaSettingsScreen(Screen lastScreen, Options gameOptions) {
        super(lastScreen, gameOptions, Component.translatable(TITLE_KEY));
        this.rows = new EunomiaSettingsOptions(Minecraft.getInstance().font);
        this.serverSection = ServerSettingsSection.isAvailable()
                ? new ServerSettingsSection(Minecraft.getInstance().font)
                : null;
    }

    @Override
    public void removed() {
        rows.commit();
        if (serverSection != null) {
            serverSection.close();
        }
        super.removed();
    }

    /**
     * The player's own rows, then - when there is a server - the server's. Built in one list because
     * both branches below want the same flat, even-length sequence: consecutive pairs are what becomes
     * one row on 1.21+.
     */
    private List<AbstractWidget> buildAll() {
        List<AbstractWidget> widgets = new ArrayList<>(rows.build());
        if (serverSection != null) {
            widgets.addAll(serverSection.build());
            // Asked for after the widgets exist, so the answer - which may already be cached, and so may
            // arrive on this very thread - always finds something to write into.
            serverSection.requestFromServer();
        }
        return widgets;
    }

    //? if >= 1.21 {
    @Override
    protected void addOptions() {
        // addSmall pairs consecutive widgets two per row, which is exactly the control/reset and
        // heading/status pairing the two builders emit.
        list.addSmall(buildAll());
    }
    //?}

    //? if < 1.21 {
    /*private de.zannagh.eunomia.client.gui.WidgetList body;

    @Override
    protected void init() {
        body = new de.zannagh.eunomia.client.gui.WidgetList(
                Minecraft.getInstance(), this.width, this.height - 64, 32, 25);
        for (AbstractWidget widget : buildAll()) {
            body.addWidget(widget);
        }
        this.addRenderableWidget(body);
        this.addRenderableWidget(net.minecraft.client.gui.components.Button
                .builder(net.minecraft.network.chat.CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(this.width / 2 - 100, this.height - 27, 200, 20)
                .build());
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.getTitle(), this.width / 2, 20, 0xFFFFFF);
    }
    *///?}
}
