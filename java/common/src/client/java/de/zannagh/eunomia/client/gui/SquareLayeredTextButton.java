package de.zannagh.eunomia.client.gui;

import de.zannagh.eunomia.ui.UiSizes;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * A square {@link LayeredButton} (20x20) whose foreground is a short centered text label.
 */
public abstract class SquareLayeredTextButton extends LayeredButton {

    private final String label;

    public SquareLayeredTextButton(boolean initial, String label, Component message, OnPress onPress) {
        super(initial, UiSizes.SQUARE_BUTTON_WIDTH, UiSizes.DEFAULT_BUTTON_HEIGHT, message, onPress);
        this.label = label;
    }

    public SquareLayeredTextButton(boolean initial, String label, Component message, OnPress onPress,
                                   @Nullable Identifier bgNormal, @Nullable Identifier bgHighlighted) {
        super(initial, UiSizes.SQUARE_BUTTON_WIDTH, UiSizes.DEFAULT_BUTTON_HEIGHT, message, onPress, bgNormal, bgHighlighted);
        this.label = label;
    }

    @Override
    protected void renderForeground(net.minecraft.client.gui.GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float a) {
        var font = Minecraft.getInstance().font;
        int textX = this.getX() + this.width / 2;
        int textY = this.getY() + (this.height - font.lineHeight) / 2 + 1;
        //? if >= 26.1-1.pre.1
        guiGraphics.centeredText(font, label, textX, textY, 0xFFFFFFFF);
        //? if < 26.1-1.pre.1
        //guiGraphics.drawCenteredString(font, label, textX, textY, 0xFFFFFFFF);
    }
}
