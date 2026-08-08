package de.zannagh.eunomia.client.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

//? if >= 1.21.6
import net.minecraft.client.renderer.RenderPipelines;

/**
 * A {@link LayeredButton} whose foreground is an image sprite supplied by
 * {@link #spriteForeground(boolean)}. The sprite is drawn centered at 15x15. Supply the sprite id
 * with {@link #sprite(String, String)}; an empty-path (or null) sprite draws nothing.
 */
public abstract class LayeredImageButton extends LayeredButton {

    protected abstract @Nullable Identifier spriteForeground(boolean enabled);

    public LayeredImageButton(boolean initial, int width, int height, Component message, OnPress onPress) {
        super(initial, width, height, message, onPress);
    }

    public LayeredImageButton(boolean initial, int width, int height, Component message, OnPress onPress,
                              @Nullable Identifier bgNormal, @Nullable Identifier bgHighlighted) {
        super(initial, width, height, message, onPress, bgNormal, bgHighlighted);
    }

    @Override
    protected void renderForeground(net.minecraft.client.gui.GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float a) {
        //? if >= 1.21.6 {
        if (spriteForeground(isEnabled) instanceof Identifier identifier && !identifier.getPath().isEmpty()) {
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, identifier, this.getX() + (this.width - 15) / 2, this.getY() + (this.height - 15) / 2, 15, 15);
        }
        //?}
        //? if <= 1.21.5 && >= 1.21.2 {
        /*if (spriteForeground(isEnabled) instanceof Identifier identifier && !identifier.getPath().isEmpty()) {
            guiGraphics.blitSprite((t) -> net.minecraft.client.renderer.rendertype.RenderType.guiTextured(t), identifier, this.getX() + (this.width - 15) / 2, this.getY() + (this.height - 15) / 2, 15, 15);
        }
        *///?}
        //? if < 1.21.2 && >= 1.21 {
        /*if (spriteForeground(isEnabled) instanceof Identifier identifier && !identifier.getPath().isEmpty()) {
            guiGraphics.blitSprite(identifier, this.getX() + (this.width - 15) / 2, this.getY() + (this.height - 15) / 2, 15, 15);
        }
        *///?}
        //? if < 1.21 {
        /*var fg = spriteForeground(isEnabled);
        if (fg != null) {
            var texture = new Identifier(fg.getNamespace(), "textures/gui/sprites/" + fg.getPath() + ".png");
            guiGraphics.blit(texture, this.getX() + (this.width - 15) / 2, this.getY() + (this.height - 15) / 2, 15, 15, 0, 0, 16, 16, 16, 16);
        }
        *///?}
    }
}
