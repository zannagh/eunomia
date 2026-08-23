package de.zannagh.eunomia.client.gui.factories;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
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

    /**
     * Forces the displayed value of a boolean option widget without notifying the option's own change
     * listener - the "somebody else changed this, catch up" direction, which vanilla's option plumbing
     * has no vocabulary for.
     *
     * <p>Needed because a widget built from an {@code OptionInstance} owns a copy of the value: once
     * something other than a click moves the underlying setting (a reset button, a layered default
     * winning again, an answer arriving from the server), the widget keeps rendering the stale copy.
     * {@code CycleButton#setValue} is the only public door onto that copy, and on every supported
     * version it does exactly the two things wanted here and nothing else: it re-derives the caption
     * and re-runs the tooltip supplier, without ever calling the value-change callback. Verified by
     * bytecode on 1.20.1, 1.21.x and 26.x - {@code setValue} calls {@code updateValue}, which calls
     * {@code setMessage} and {@code updateTooltip} only.
     *
     * <p>Silently does nothing for a widget that is not a boolean cycle button, so a caller may hand it
     * whatever {@code createButton} returned without first proving what that was.
     *
     * @param widget the widget produced for a boolean {@code OptionInstance}.
     * @param value the value it should now display.
     */
    public static void setBooleanValue(AbstractWidget widget, boolean value) {
        if (!(widget instanceof CycleButton<?>)) {
            return;
        }
        @SuppressWarnings("unchecked")
        CycleButton<Boolean> button = (CycleButton<Boolean>) widget;
        button.setValue(value);
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
