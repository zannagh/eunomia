package de.zannagh.eunomia.client.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

//? if >= 1.21.6
import net.minecraft.client.renderer.RenderPipelines;

/**
 * Abstract toggle-button base that renders in layers: a background sprite, an optional centered
 * 15x15 mid-layer sprite, and a subclass-drawn foreground. Holds an on/off {@link #isEnabled} state
 * and swaps its message + tooltip between {@link #enabledMessage()} and {@link #disabledMessage()}.
 *
 * <p>Reusable across mods: the background defaults to the vanilla button sprite but can be overridden
 * per instance via the {@code bgNormal}/{@code bgHighlighted} constructor parameters, and the
 * mid-layer sprite is supplied by overriding {@link #midLayerSprite(boolean)}. Use the
 * {@link #sprite(String, String)} helper to build a namespaced sprite id without version-specific
 * {@code Identifier} construction.
 */
public abstract class LayeredButton extends Button {

    protected boolean isEnabled;

    // Optional custom background sprites (normal + hovered/focused). Null falls back to vanilla.
    @Nullable private final Identifier bgNormal;
    @Nullable private final Identifier bgHighlighted;

    @Nullable protected Identifier midLayerSprite(boolean enabled) { return null; }

    protected Identifier spriteBg() {
        boolean highlighted = this.isHoveredOrFocused();
        if (bgNormal != null && bgHighlighted != null) {
            return highlighted ? bgHighlighted : bgNormal;
        }
        //? if >= 1.21
        return highlighted ? Identifier.withDefaultNamespace("widget/button_highlighted") : Identifier.withDefaultNamespace("widget/button");
        //? if < 1.21
        //return highlighted ? new Identifier("minecraft/textures/gui/widgets/button_highlighted.png") : new Identifier("minecraft/textures/gui/widgets/button.png");
    }

    protected abstract void renderForeground(net.minecraft.client.gui.GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float a);

    /**
     * Builds a sprite {@link Identifier} in the given namespace, bridging the pre/post-1.21 factory.
     * @param namespace the resource namespace (usually the consuming mod's id).
     * @param path the sprite path.
     * @return the sprite identifier.
     */
    //? if >= 1.21
    protected static Identifier sprite(String namespace, String path) { return Identifier.fromNamespaceAndPath(namespace, path); }
    //? if < 1.21
    //protected static Identifier sprite(String namespace, String path) { return new Identifier(namespace, path); }

    public LayeredButton(boolean initial, int width, int height, Component message, OnPress onPress) {
        this(initial, width, height, message, onPress, null, null);
    }

    public LayeredButton(boolean initial, int width, int height, Component message, OnPress onPress,
                         @Nullable Identifier bgNormal, @Nullable Identifier bgHighlighted) {
        super(0, 0, width, height, message, onPress, (discarded) -> MutableComponent.create(message.getContents()));
        this.setMessage(message);
        this.setTooltip(Tooltip.create(message));
        this.isEnabled = initial;
        this.bgNormal = bgNormal;
        this.bgHighlighted = bgHighlighted;
    }

    protected abstract Component enabledMessage();
    protected abstract Component disabledMessage();

    protected void setEnabled(boolean enabled) {
        isEnabled = enabled;
        if (isEnabled) {
            this.setMessage(enabledMessage());
            this.setTooltip(Tooltip.create(enabledMessage()));
        } else {
            this.setMessage(disabledMessage());
            this.setTooltip(Tooltip.create(disabledMessage()));
        }
    }

    public boolean toggle() {
        setEnabled(!isEnabled);
        return isEnabled;
    }

    //? if >= 26.1-1.pre.1 {
    @Override
    protected void extractContents(net.minecraft.client.gui.GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, spriteBg(), this.getX(), this.getY(), this.width, this.height);
        if (midLayerSprite(isEnabled) instanceof Identifier sprite) {
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, this.getX() + (this.width - 15) / 2, this.getY() + (this.height - 15) / 2, 15, 15);
        }
        renderForeground(guiGraphics, mouseX, mouseY, partialTicks);
    }
    //?}

    //? if < 26.1-1.pre.1 && > 1.21.10 {
    /*@Override
    protected void renderContents(net.minecraft.client.gui.GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, spriteBg(), this.getX(), this.getY(), this.width, this.height);
        if (midLayerSprite(isEnabled) instanceof Identifier sprite) {
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, this.getX() + (this.width - 15) / 2, this.getY() + (this.height - 15) / 2, 15, 15);
        }
        renderForeground(guiGraphics, mouseX, mouseY, partialTicks);
    }
    *///?}

    //? if <= 1.21.10 && >= 1.21.6 {
    /*@Override
    protected void renderWidget(net.minecraft.client.gui.GuiGraphicsExtractor guiGraphics, int i, int j, float f) {
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, spriteBg(), this.getX(), this.getY(), this.width, this.height);
        if (midLayerSprite(isEnabled) instanceof Identifier sprite) {
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, this.getX() + (this.width - 15) / 2, this.getY() + (this.height - 15) / 2, 15, 15);
        }
        renderForeground(guiGraphics, i, j, f);
    }
    *///?}

    //? if <= 1.21.5 && >= 1.21.2 {
    /*@Override
    protected void renderWidget(net.minecraft.client.gui.GuiGraphicsExtractor guiGraphics, int i, int j, float f) {
        guiGraphics.blitSprite((t) -> net.minecraft.client.renderer.rendertype.RenderType.guiTextured(t), spriteBg(), this.getX(), this.getY(), this.width, this.height);
        if (midLayerSprite(isEnabled) instanceof Identifier sprite) {
            guiGraphics.blitSprite((t) -> net.minecraft.client.renderer.rendertype.RenderType.guiTextured(t), sprite, this.getX() + (this.width - 15) / 2, this.getY() + (this.height - 15) / 2, 15, 15);
        }
        renderForeground(guiGraphics, i, j, f);
    }
    *///?}

    //? if < 1.21.2 && >= 1.21 {
    /*@Override
    protected void renderWidget(net.minecraft.client.gui.GuiGraphicsExtractor guiGraphics, int i, int j, float f) {
        guiGraphics.blitSprite(spriteBg(), this.getX(), this.getY(), this.width, this.height);
        if (midLayerSprite(isEnabled) instanceof Identifier sprite) {
            guiGraphics.blitSprite(sprite, this.getX() + (this.width - 15) / 2, this.getY() + (this.height - 15) / 2, 15, 15);
        }
        renderForeground(guiGraphics, i, j, f);
    }
    *///?}

    //? if < 1.21 {
    /*@Override
    public void renderWidget(net.minecraft.client.gui.GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        Component message = this.getMessage();
        super.setMessage(Component.empty());
        super.renderWidget(guiGraphics, mouseX, mouseY, partialTicks);
        super.setMessage(message);
        var midSprite = midLayerSprite(isEnabled);
        if (midSprite != null) {
            var texture = new Identifier(midSprite.getNamespace(), "textures/gui/sprites/" + midSprite.getPath() + ".png");
            guiGraphics.blit(texture, this.getX() + (this.width - 15) / 2, this.getY() + (this.height - 15) / 2, 15, 15, 0, 0, 16, 16, 16, 16);
        }
        renderForeground(guiGraphics, mouseX, mouseY, partialTicks);
    }
    *///?}
}
