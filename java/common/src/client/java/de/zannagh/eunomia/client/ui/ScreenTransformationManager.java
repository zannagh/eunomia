package de.zannagh.eunomia.client.ui;

import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry mapping a concrete {@link Screen} class to the {@link ScreenTransformer}s that should run
 * for it. The game-version-agnostic {@code ScreenMixin} queries this on every screen render, so a
 * consuming mod only has to register a transformer here instead of writing a per-version mixin.
 */
public final class ScreenTransformationManager {

    /**
     * The shared registry instance.
     */
    public static final ScreenTransformationManager INSTANCE = new ScreenTransformationManager();

    // ConcurrentHashMap + LinkedHashSet: registration usually happens during client init but the
    // mixin reads on the render thread; the sets preserve registration order for deterministic apply.
    private final Map<Class<? extends Screen>, Set<ScreenTransformer<? extends Screen>>> transformers =
            new ConcurrentHashMap<>();

    private ScreenTransformationManager() {
    }

    /**
     * Registers a transformer for a screen class. Transformers run in registration order, and only
     * when the rendered screen's runtime class equals {@code screenClass}.
     * @param screenClass the screen type to target.
     * @param transformer the transformer to run.
     * @param <T> the screen type.
     */
    public <T extends Screen> void registerTransformer(Class<T> screenClass, ScreenTransformer<T> transformer) {
        transformers.computeIfAbsent(screenClass, k -> new LinkedHashSet<>()).add(transformer);
    }

    /**
     * The transformers registered for the accessor's screen (empty if none).
     * @param screenAccessor the screen accessor.
     * @return an ordered snapshot of the applicable transformers.
     */
    public List<ScreenTransformer<? extends Screen>> getTransformers(ScreenAccessor screenAccessor) {
        return getTransformers(screenAccessor.eunomia$getScreen());
    }

    /**
     * The transformers registered for the screen's runtime class (empty if none).
     * @param screen the screen.
     * @return an ordered snapshot of the applicable transformers.
     */
    public List<ScreenTransformer<? extends Screen>> getTransformers(Screen screen) {
        Set<ScreenTransformer<? extends Screen>> registered = transformers.get(screen.getClass());
        if (registered == null || registered.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(registered);
    }
}
