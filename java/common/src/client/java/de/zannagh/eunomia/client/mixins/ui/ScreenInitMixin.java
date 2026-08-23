package de.zannagh.eunomia.client.mixins.ui;

import de.zannagh.eunomia.client.ui.ScreenInitializationManager;
import de.zannagh.eunomia.client.ui.ScreenWidgetSink;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * The single, game-version-agnostic {@code Screen} <em>init</em> hook - the counterpart to
 * {@code ScreenMixin}, which hooks the render entrypoint instead.
 *
 * <p>Widgets have to be added here, not from a render hook. Vanilla owns the three widget lists and
 * clears them in {@code rebuildWidgets}, so a widget added while rendering is re-added every single
 * frame unless hand-de-duplicated, is lost the moment the screen rebuilds, and does not exist yet when
 * the first click after {@code init} is dispatched. Both entrypoints vanilla uses to (re)populate a
 * screen are covered:
 * <ul>
 *   <li>the {@code final} {@code Screen#init(...)}, which runs on first open <em>and</em> on every
 *       resize - hence the idempotence requirement on {@code ScreenInitializer};</li>
 *   <li>{@code Screen#rebuildWidgets()}, which clears the lists and re-runs the subclass {@code init()}
 *       (a screen option toggling itself, a language change, ...).</li>
 * </ul>
 * The protected no-argument {@code Screen#init()} is deliberately <em>not</em> targeted: subclasses
 * override it, so an injection into {@code Screen}'s own copy would never run for the screens that
 * matter.
 *
 * <p>{@link #eunomia$addWidget} reproduces vanilla's {@code addRenderableWidget} by filing the widget
 * in all three lists (including the private {@code narratables}), rather than only the two the
 * {@code ScreenAccessor} exposes, so an injected widget is narrated like a vanilla one.
 */
@Mixin(Screen.class)
public abstract class ScreenInitMixin implements ScreenWidgetSink {

    @Shadow
    @Final
    private List<GuiEventListener> children;

    @Shadow
    @Final
    private List<Renderable> renderables;

    @Shadow
    @Final
    private List<NarratableEntry> narratables;

    @Override
    public void eunomia$addWidget(AbstractWidget widget) {
        renderables.add(widget);
        children.add(widget);
        narratables.add(widget);
    }

    //? if >= 1.21.11 {
    @Inject(method = "init(II)V", at = @At("TAIL"))
    private void eunomia$afterInit(int width, int height, CallbackInfo ci) {
        eunomia$runScreenInitializers();
    }
    //?}
    //? if < 1.21.11 {
    /*@Inject(method = "init(Lnet/minecraft/client/Minecraft;II)V", at = @At("TAIL"))
    private void eunomia$afterInit(net.minecraft.client.Minecraft minecraft, int width, int height, CallbackInfo ci) {
        eunomia$runScreenInitializers();
    }
    *///?}

    @Inject(method = "rebuildWidgets", at = @At("TAIL"))
    private void eunomia$afterRebuildWidgets(CallbackInfo ci) {
        eunomia$runScreenInitializers();
    }

    private void eunomia$runScreenInitializers() {
        // Purely a registry lookup: eunomia's own settings button is registered once from
        // EunomiaClient#init(), not from here, so this hot path carries no bootstrap of its own.
        ScreenInitializationManager.INSTANCE.applyTo((Screen) (Object) this, this);
    }
}
