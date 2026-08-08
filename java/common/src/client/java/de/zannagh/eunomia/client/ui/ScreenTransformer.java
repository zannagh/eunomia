package de.zannagh.eunomia.client.ui;

import net.minecraft.client.gui.screens.Screen;

/**
 * A registered transformation applied to a specific {@link Screen} type. Register instances with
 * {@link ScreenTransformationManager#registerTransformer(Class, ScreenTransformer)}; the single
 * game-version-agnostic {@code ScreenMixin} invokes them from the screen's render entrypoint, so a
 * consuming mod adds screen behaviour (extra widgets, overlays, suppressed vanilla rendering) without
 * shipping its own {@code Screen} mixin per game version.
 *
 * <p>{@code GuiGraphicsExtractor} is a Stonecutter alias that resolves to the render-state extractor
 * on modern versions and to {@code GuiGraphics} on versions before the render-state split, so a single
 * source signature works across the whole matrix.
 *
 * @param <T> the screen type this transformer targets.
 */
public interface ScreenTransformer<T extends Screen> {

    /**
     * Whether this transformer should run for the given screen instance. Defaults to {@code true};
     * override to gate on runtime state (a config toggle, the concrete subclass, connection state).
     * @param screen the screen being rendered.
     * @return {@code true} to run {@link #apply}.
     */
    default boolean canTransform(ScreenAccessor screen) {
        return true;
    }

    /**
     * Whether vanilla rendering of the screen should be cancelled for this frame. Defaults to
     * {@code false}. When any applicable transformer returns {@code true}, the mixin cancels the
     * vanilla render pass (the transformer is then responsible for drawing).
     * @param screen the screen being rendered.
     * @return {@code true} to suppress vanilla rendering.
     */
    default boolean suppressVanillaRender(ScreenAccessor screen) {
        return false;
    }

    /**
     * Applies the transformation. Invoked at the head of the screen's render entrypoint.
     * @param screen the screen being rendered (use its accessor to reach children/renderables).
     * @param graphics the render-state extractor (or {@code GuiGraphics} on legacy versions).
     * @param mouseX the mouse x position.
     * @param mouseY the mouse y position.
     * @param partialTick the frame partial tick.
     */
    void apply(ScreenAccessor screen, net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick);
}
