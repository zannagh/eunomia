package de.zannagh.eunomia.client.gui.screens;

import de.zannagh.eunomia.client.gui.factories.NarratedTooltipFactory;
import de.zannagh.eunomia.client.ui.ScreenAccessor;
import de.zannagh.eunomia.client.ui.ScreenInitializer;
import de.zannagh.eunomia.client.ui.ScreenWidgetSink;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Adds the "Eunomia Settings" button to whichever screen {@link EunomiaSettingsEntryPoint} points at.
 *
 * <p>Idempotence is handled by remembering the button that was handed to each screen and checking
 * whether that screen still holds it. That distinguishes the two ways vanilla re-enters init, which
 * need opposite answers: a <em>resize</em> re-runs init with the widget lists untouched (the button is
 * still there - do nothing, or the player collects a new button per window drag), while a
 * <em>rebuild</em> clears the lists first (the button is gone - put it back). The map is weak-keyed so
 * a closed screen does not keep itself alive.
 *
 * <p>The button sits in the top-left corner, which is empty on every supported version's options
 * screens and, unlike the footer row, never collides with vanilla's own centred buttons.
 */
public final class EunomiaSettingsButtonInitializer implements ScreenInitializer {

    private static final int MARGIN = 6;

    private static final int BUTTON_HEIGHT = 20;

    private static final int MIN_BUTTON_WIDTH = 120;

    private static final int MAX_BUTTON_WIDTH = 140;

    private static final int LABEL_PADDING = 16;

    private final Map<Screen, AbstractWidget> attached = Collections.synchronizedMap(new WeakHashMap<>());

    @Override
    public void initialize(Screen screen, ScreenWidgetSink sink) {
        if (isStillAttached(screen)) {
            return;
        }
        Component label = EunomiaSettingsEntryPoint.label();
        int width = Math.min(MAX_BUTTON_WIDTH,
                Math.max(MIN_BUTTON_WIDTH, Minecraft.getInstance().font.width(label) + LABEL_PADDING));
        Button button = Button.builder(label, press -> open(screen))
                .bounds(MARGIN, MARGIN, width, BUTTON_HEIGHT)
                .build();
        button.setTooltip(new NarratedTooltipFactory<Void>(
                Component.translatable("eunomia.settings.button.tooltip"),
                Component.translatable("eunomia.settings.button.narration")).apply(null));
        sink.eunomia$addWidget(button);
        attached.put(screen, button);
    }

    private boolean isStillAttached(Screen screen) {
        AbstractWidget previous = attached.get(screen);
        if (previous == null) {
            return false;
        }
        List<Renderable> renderables = ((ScreenAccessor) screen).eunomia$getRenderables();
        return renderables.contains(previous);
    }

    private static void open(Screen parent) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreenAndShow(new EunomiaSettingsScreen(parent, minecraft.options));
    }
}
