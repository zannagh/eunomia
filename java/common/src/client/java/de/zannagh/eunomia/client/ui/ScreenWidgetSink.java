package de.zannagh.eunomia.client.ui;

import net.minecraft.client.gui.components.AbstractWidget;

/**
 * Duck interface implemented by {@code Screen} (via {@code ScreenInitMixin}) that lets a
 * {@link ScreenInitializer} push a widget into a screen it does not own.
 *
 * <p>This exists next to {@link ScreenAccessor} rather than as part of it on purpose. The accessor
 * hands out the raw {@code children}/{@code renderables} lists, which is enough to <em>read</em> a
 * screen but not enough to add a widget correctly: vanilla's {@code addRenderableWidget} also files
 * the widget in the private {@code narratables} list, and a widget missing from that list is invisible
 * to the narrator and to tab-order search. The sink performs the same three-list registration vanilla
 * does, so an injected widget behaves exactly like one the screen added itself.
 */
public interface ScreenWidgetSink {

    /**
     * Registers a widget with the screen for rendering, input and narration - the equivalent of
     * vanilla's protected {@code Screen#addRenderableWidget}.
     * @param widget the widget to add.
     */
    void eunomia$addWidget(AbstractWidget widget);
}
