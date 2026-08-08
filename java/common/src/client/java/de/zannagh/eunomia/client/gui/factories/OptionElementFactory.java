package de.zannagh.eunomia.client.gui.factories;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Assembles vanilla-styled option widgets and appends them via an injected {@link Consumer}, plus
 * generic builders for {@code OptionInstance}s. Mods compose their own screen rows from these; the
 * factory itself carries no mod-specific widgets.
 */
public class OptionElementFactory {
    private final Options gameOptions;
    private final Consumer<AbstractWidget> widgetAdder;
    private final int rowWidth;

    public OptionElementFactory(Consumer<AbstractWidget> widgetAdder, Options gameOptions, int rowWidth) {
        this.widgetAdder = widgetAdder;
        this.gameOptions = gameOptions;
        this.rowWidth = rowWidth;
    }

    public int getRowWidth() {
        return rowWidth;
    }

    public void addElementAsWidget(AbstractWidget widget) {
        widgetAdder.accept(widget);
    }

    public <T> void addSimpleOptionAsWidget(OptionInstance<T> option) {
        widgetAdder.accept(option.createButton(gameOptions, 0, 0, rowWidth));
    }

    public void addTextWidget(Component text) {
        var textWidget = new MultiLineTextWidget(text, Minecraft.getInstance().font).setCentered(true);
        widgetAdder.accept(textWidget);
    }

    /**
     * Builds a slider {@link OptionInstance} over a 0..20 integer range mapped to 0.0..1.0.
     * @param key the option/translation key.
     * @param tooltip the tooltip component.
     * @param narration the optional narration component (falls back to the tooltip when null).
     * @param sliderTextProvider maps the current value to the slider's displayed text.
     * @param defaultValue the default value.
     * @param setter receives the value on change.
     * @return the configured option instance.
     */
    public OptionInstance<Double> buildDoubleOption(String key,
                                                    MutableComponent tooltip,
                                                    @Nullable MutableComponent narration,
                                                    Function<Double, MutableComponent> sliderTextProvider,
                                                    Double defaultValue,
                                                    Consumer<Double> setter) {
        return new OptionInstance<>(
                key,
                new NarratedTooltipFactory<>(tooltip, narration),
                (text, value) -> sliderTextProvider.apply(value),
                // The trailing flag is applyValueImmediately. It MUST stay false: with it on, the setter
                // fires on every drag step, and each call writes the whole preset file to disk
                // synchronously on the render thread - enough to starve frames and input on a slider drag.
                //? if >= 1.21.11
                new OptionInstance.IntRange(0, 20).xmap(v -> v / 20.0, v -> (int) Math.round(v * 20), false)
                //? if >= 1.20.5 && < 1.21.11
                //new OptionInstance.IntRange(0, 20).xmap(v -> v / 20.0, v -> (int) Math.round(v * 20))
                //? if < 1.20.5
                //OptionInstance.UnitDouble.INSTANCE
                ,
                defaultValue,
                //? if > 26.1.2
                setter::accept
                //? if <= 26.1.2
                //setter
        );
    }

    /**
     * Builds a boolean toggle {@link OptionInstance}.
     * @param key the caption component (a translatable key is extracted when present).
     * @param tooltip the tooltip component.
     * @param narration the optional narration component (falls back to the tooltip when null).
     * @param valueText maps the current on/off value to its displayed text.
     * @param defaultValue the default value.
     * @param setter receives the value on change.
     * @return the configured option instance.
     */
    public OptionInstance<Boolean> buildBooleanOption(MutableComponent key,
                                                      MutableComponent tooltip,
                                                      @Nullable MutableComponent narration,
                                                      Function<Boolean, Component> valueText,
                                                      Boolean defaultValue,
                                                      Consumer<Boolean> setter) {
        String booleanKey;
        if (key.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translatableContents) {
            booleanKey = translatableContents.getKey();
        } else {
            booleanKey = key.getString();
        }
        return OptionInstance.createBoolean(
                booleanKey,
                new NarratedTooltipFactory<>(tooltip, narration),
                (text, value) -> valueText.apply(value),
                defaultValue,
                //? if > 26.1.2
                setter::accept
                //? if <= 26.1.2
                //setter
        );
    }
}
