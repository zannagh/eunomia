package de.zannagh.eunomia.client.mixins.ui;

import de.zannagh.eunomia.client.ui.ScreenAccessor;
import de.zannagh.eunomia.client.ui.ScreenTransformationManager;
import de.zannagh.eunomia.client.ui.ScreenTransformer;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * The single, game-version-agnostic {@code Screen} hook. It shadows the protected child/renderable
 * lists to satisfy {@link ScreenAccessor}, and runs every {@link ScreenTransformer} registered for
 * the concrete screen at the head of its render entrypoint. A consuming mod registers transformations
 * against {@link ScreenTransformationManager} instead of writing its own {@code Screen} mixin per
 * game version.
 *
 * <p>The render entrypoint changed name at 26.1-1.pre.1 (the render-state split): modern versions
 * extract render state in {@code extractRenderState}, older ones draw directly in {@code render}.
 * Stonecutter selects the matching target method below, while {@code GuiGraphicsExtractor} is a source
 * alias that resolves to {@code GuiGraphics} before the split, so one source covers both.
 */
@Mixin(Screen.class)
public abstract class ScreenMixin extends AbstractContainerEventHandler implements Renderable, ScreenAccessor {

    @Shadow
    @Final
    private List<GuiEventListener> children;

    @Shadow
    @Final
    private List<Renderable> renderables;

    @Override
    public List<GuiEventListener> eunomia$getChildren() {
        return children;
    }

    @Override
    public List<Renderable> eunomia$getRenderables() {
        return renderables;
    }

    @Override
    public Screen eunomia$getScreen() {
        return (Screen) (Object) this;
    }

    @Inject(
            //? if >= 26.1-1.pre.1 {
            method = "extractRenderState",
            //?}
            //? if < 26.1-1.pre.1 {
            /*method = "render",*/
            //?}
            at = @At("HEAD"),
            cancellable = true
    )
    private void eunomia$applyScreenTransformers(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        List<ScreenTransformer<? extends Screen>> transformers = ScreenTransformationManager.INSTANCE.getTransformers(this);
        if (transformers.isEmpty()) {
            return;
        }
        for (ScreenTransformer<? extends Screen> transformer : transformers) {
            if (!transformer.canTransform(this)) {
                continue;
            }
            if (transformer.suppressVanillaRender(this)) {
                ci.cancel();
            }
            transformer.apply(this, graphics, mouseX, mouseY, partialTick);
        }
    }
}
