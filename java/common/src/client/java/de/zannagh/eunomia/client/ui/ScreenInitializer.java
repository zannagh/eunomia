package de.zannagh.eunomia.client.ui;

import net.minecraft.client.gui.screens.Screen;

/**
 * A registered contribution to a specific {@link Screen} type, applied at the screen's <em>init</em>
 * lifecycle point rather than at render time.
 *
 * <p>This is the sibling of {@link ScreenTransformer}, and the two are not interchangeable. A
 * transformer runs from the screen's render entrypoint, which is the right place to draw or to
 * suppress drawing, and the wrong place to add a widget: the widget lists are rebuilt by
 * {@code Screen#init}, a render-time add either duplicates on every frame or has to be de-duplicated
 * by hand, and a click that arrives before the first frame after {@code init} finds nothing to hit.
 * An initializer runs right after vanilla finished populating the screen, so a widget it adds is a
 * genuine, persistent, clickable, narratable child of the screen.
 *
 * <p>Like the transformer registry, dispatch is on the <strong>exact runtime class</strong> of the
 * screen - a subclass does not inherit a registration.
 */
public interface ScreenInitializer {

    /**
     * Whether this initializer should run for the given screen instance. Defaults to {@code true};
     * override to gate on runtime state.
     * @param screen the screen being initialised.
     * @return {@code true} to run {@link #initialize}.
     */
    default boolean canInitialize(Screen screen) {
        return true;
    }

    /**
     * Contributes to the freshly initialised screen.
     *
     * <p>Implementations must be idempotent: vanilla re-runs {@code init} on every resize and on every
     * {@code rebuildWidgets}, and both paths call back in here.
     *
     * @param screen the screen being initialised (its {@code width}/{@code height} are already final).
     * @param sink the sink that registers a widget with the screen.
     */
    void initialize(Screen screen, ScreenWidgetSink sink);
}
