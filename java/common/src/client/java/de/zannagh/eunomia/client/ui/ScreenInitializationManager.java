package de.zannagh.eunomia.client.ui;

import de.zannagh.eunomia.Eunomia;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Registry mapping a concrete {@link Screen} class to the {@link ScreenInitializer}s that should run
 * for it. The game-version-agnostic {@code ScreenInitMixin} queries this once per screen init, so a
 * consuming mod adds a widget to a vanilla screen without shipping its own per-version mixin.
 *
 * <p>Deliberately mirrors {@link ScreenTransformationManager}, including its exact-runtime-class
 * matching: {@code screen.getClass()} is the key, so registering against a base class does
 * <em>not</em> pick up subclasses.
 */
public final class ScreenInitializationManager {

    /** The shared registry instance. */
    public static final ScreenInitializationManager INSTANCE = new ScreenInitializationManager();

    /**
     * ConcurrentHashMap + CopyOnWriteArraySet, concurrent from the key down.
     *
     * <p>A plain {@code LinkedHashSet} behind a concurrent map is only half a guard, and the unguarded half is
     * the one that gets hit: {@code getInitializers} copies a set on the render thread while
     * {@code EunomiaSettingsEntryPoint.setTargetScreen} - documented as safe to call before or after init -
     * mutates it from whatever thread the consuming mod configured on, which is a
     * {@link java.util.ConcurrentModificationException} thrown from inside {@code Screen#init}.
     * {@code CopyOnWriteArraySet} keeps both properties this registry needs: it deduplicates like a set, and it
     * preserves registration order, so application stays deterministic. Registration is rare and reads happen
     * once per screen open, so copy-on-write is the cheap side of the trade.</p>
     */
    private final Map<Class<? extends Screen>, Set<ScreenInitializer>> initializers = new ConcurrentHashMap<>();

    private ScreenInitializationManager() {
    }

    /**
     * Registers an initializer for a screen class. Initializers run in registration order, and only
     * when the initialised screen's runtime class equals {@code screenClass}.
     * @param screenClass the screen type to target.
     * @param initializer the initializer to run.
     */
    public void registerInitializer(Class<? extends Screen> screenClass, ScreenInitializer initializer) {
        initializers.computeIfAbsent(screenClass, key -> new CopyOnWriteArraySet<>()).add(initializer);
    }

    /**
     * Removes a previously registered initializer. Used when a consumer re-points a contribution at a
     * different screen class, so the old target stops receiving it.
     * @param screenClass the screen type it was registered against.
     * @param initializer the initializer to remove.
     */
    public void removeInitializer(Class<? extends Screen> screenClass, ScreenInitializer initializer) {
        Set<ScreenInitializer> registered = initializers.get(screenClass);
        if (registered != null) {
            registered.remove(initializer);
        }
    }

    /**
     * The initializers registered for the screen's runtime class (empty if none).
     * @param screen the screen.
     * @return an ordered snapshot of the applicable initializers.
     */
    public List<ScreenInitializer> getInitializers(Screen screen) {
        Set<ScreenInitializer> registered = initializers.get(screen.getClass());
        if (registered == null || registered.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(registered);
    }

    /**
     * Runs every applicable initializer against a freshly initialised screen.
     * @param screen the screen.
     * @param sink the widget sink backed by that same screen.
     */
    public void applyTo(Screen screen, ScreenWidgetSink sink) {
        for (ScreenInitializer initializer : getInitializers(screen)) {
            // Isolated per initializer: this runs inside Screen#init, so an exception from one consuming mod's
            // contribution would abort the vanilla screen's own initialization and take the whole GUI down -
            // and it would take every later-registered contribution (eunomia's settings button among them)
            // with it. A missing widget is a bug in one mod; a crash here is a bug for the player.
            try {
                if (initializer.canInitialize(screen)) {
                    initializer.initialize(screen, sink);
                }
            } catch (Exception e) {
                Eunomia.LOGGER.error("Screen initializer failed for {}; skipping it",
                        screen.getClass().getName(), e);
            }
        }
    }
}
