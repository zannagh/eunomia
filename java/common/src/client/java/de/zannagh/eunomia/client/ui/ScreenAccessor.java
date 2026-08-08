package de.zannagh.eunomia.client.ui;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;

/**
 * Duck interface implemented by {@code Screen} (via {@code ScreenMixin}) to expose the protected
 * child/renderable lists and a self-reference to registered {@link ScreenTransformer}s. Keeping the
 * accessor game-version-agnostic means a consuming mod registers transformers against it without
 * writing its own {@code Screen} mixin per game version.
 */
public interface ScreenAccessor {

    /**
     * The screen this accessor belongs to.
     * @return the screen instance.
     */
    Screen eunomia$getScreen();

    /**
     * The screen's renderable widgets (the same list vanilla draws from).
     * @return the mutable renderable list.
     */
    List<Renderable> eunomia$getRenderables();

    /**
     * The screen's event-handling children (widgets that receive input).
     * @return the mutable child list.
     */
    List<GuiEventListener> eunomia$getChildren();
}
