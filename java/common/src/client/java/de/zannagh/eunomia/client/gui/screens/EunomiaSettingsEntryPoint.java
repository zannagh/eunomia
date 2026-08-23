package de.zannagh.eunomia.client.gui.screens;

import de.zannagh.eunomia.client.ui.ScreenInitializationManager;
import de.zannagh.eunomia.configuration.EunomiaClientOptions;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OnlineOptionsScreen;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/**
 * The consumer-facing seam for eunomia's entry button: <em>where</em> it appears and <em>what</em> it
 * says. A mod embedding eunomia usually wants the settings reachable from its own options screen
 * rather than from vanilla's online options, and wants the button to carry its own name.
 *
 * <p>This is intentionally a bare static holder rather than a fluent API; the fluent builder behind
 * {@code EunomiaClient.configure()} delegates here rather than holding a second copy of the state.
 * Whether the button exists <em>at all</em> is not stored here but in {@link EunomiaClientOptions},
 * because that switch must also be settable from a consuming mod's common initializer, which may not
 * class-load anything under {@code net.minecraft.client}. {@link #install()} registers this class as
 * that holder's observer, so flipping the flag from either side reconciles the registration.
 *
 * <p>Note the exact-runtime-class matching of {@link ScreenInitializationManager}: the target must be
 * the concrete screen class the player actually opens, not a base class it extends.
 */
public final class EunomiaSettingsEntryPoint {

    private static final EunomiaSettingsButtonInitializer INITIALIZER = new EunomiaSettingsButtonInitializer();

    private static final Object LOCK = new Object();

    private static volatile Class<? extends Screen> targetScreen = OnlineOptionsScreen.class;

    private static volatile Component label = Component.translatable(EunomiaSettingsScreen.TITLE_KEY);

    private static volatile boolean registered;

    private static volatile boolean installRequested;

    private EunomiaSettingsEntryPoint() {
    }

    /**
     * Installs the entry button against the current target screen, and subscribes to
     * {@link EunomiaClientOptions#settingsButtonEnabled()} so a later suppression takes the button away
     * again. Called exactly once, from {@code EunomiaClient#init()}.
     *
     * <p>Idempotent, so a consuming mod that re-runs eunomia's client init (a test harness, typically)
     * does not end up with two registrations. It used to be called from the screen-init mixin instead,
     * which cost a volatile read on every single screen init; the entrypoint is the correct home.</p>
     */
    public static void install() {
        synchronized (LOCK) {
            installRequested = true;
            EunomiaClientOptions.setSettingsButtonObserver(EunomiaSettingsEntryPoint::reconcile);
            reconcile();
        }
    }

    /**
     * Whether the entry button is currently registered with the screen-initialisation registry.
     * @return {@code true} while the button is installed on {@link #targetScreen()}.
     */
    public static boolean isRegistered() {
        return registered;
    }

    /**
     * Brings the registration in line with {@link #installRequested} and the consumer's enable flag.
     * Cheap and safe to call repeatedly; every mutator funnels through it.
     */
    private static void reconcile() {
        synchronized (LOCK) {
            boolean wanted = installRequested && EunomiaClientOptions.settingsButtonEnabled();
            if (wanted && !registered) {
                ScreenInitializationManager.INSTANCE.registerInitializer(targetScreen, INITIALIZER);
                registered = true;
            } else if (!wanted && registered) {
                ScreenInitializationManager.INSTANCE.removeInitializer(targetScreen, INITIALIZER);
                registered = false;
            }
        }
    }

    /** The screen the entry button is attached to. Defaults to vanilla's online options screen. */
    public static Class<? extends Screen> targetScreen() {
        return targetScreen;
    }

    /**
     * Moves the entry button to a different screen. Safe to call before or after the button was
     * installed; the previous target stops showing it either way.
     * @param screenClass the concrete screen class to attach to.
     */
    public static void setTargetScreen(Class<? extends Screen> screenClass) {
        Objects.requireNonNull(screenClass, "screenClass");
        synchronized (LOCK) {
            if (registered) {
                ScreenInitializationManager.INSTANCE.removeInitializer(targetScreen, INITIALIZER);
                registered = false;
            }
            targetScreen = screenClass;
            reconcile();
        }
    }

    /** The label drawn on the entry button. */
    public static Component label() {
        return label;
    }

    /**
     * Renames the entry button. Takes effect the next time the target screen is (re)initialised.
     * @param newLabel the label to draw.
     */
    public static void setLabel(Component newLabel) {
        label = Objects.requireNonNull(newLabel, "newLabel");
    }
}
