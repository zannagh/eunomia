package de.zannagh.eunomia.configuration;

import org.jspecify.annotations.Nullable;

/**
 * Side-agnostic value holder for the two presentation switches a consuming mod may want to flip from its
 * <em>common</em> init: whether eunomia may raise toasts at all, and whether eunomia installs its own
 * settings button.
 *
 * <p>Why this class exists at all, rather than the flags simply living next to the client code that reads
 * them: {@code EunomiaToasts} and {@code EunomiaSettingsEntryPoint} are in the <em>client</em> source set and
 * reference {@code net.minecraft.client.*}. A dedicated server never has those classes on its classpath, so a
 * configuration entry point in the common source set may not so much as name them - not in a field type, not
 * in a method descriptor, not in a constant-pool entry. This holder stores plain {@code boolean}s and nothing
 * else, which lets {@link EunomiaConfiguration} (common) and the client-side builder write the very same state
 * without the common half ever touching a client type.</p>
 *
 * <p>The flags are read, never pushed, wherever that is possible: {@code EunomiaToasts} consults
 * {@link #toastsEnabled()} at the moment a toast is raised, so a change applies immediately regardless of when
 * it was made. The settings button cannot work that way - it is registered once against a screen class - so
 * that one flag additionally notifies {@link #setSettingsButtonObserver(Runnable) an observer} which the client
 * installs during its init. Consumers never register that observer themselves.</p>
 *
 * @since 0.3.0
 */
public final class EunomiaClientOptions {

    private static volatile boolean toastsEnabled = true;

    private static volatile boolean settingsButtonEnabled = true;

    private static volatile @Nullable Runnable settingsButtonObserver;

    private EunomiaClientOptions() {
    }

    /** Whether eunomia's toasts currently reach the screen. Defaults to {@code true}. */
    public static boolean toastsEnabled() {
        return toastsEnabled;
    }

    /**
     * Turns all eunomia toast rendering on or off. Safe to call from any side and at any time; on a dedicated
     * server it simply sets a boolean nothing ever reads.
     * @param value {@code true} to allow toasts, {@code false} to suppress them.
     */
    public static void setToastsEnabled(boolean value) {
        toastsEnabled = value;
    }

    /** Whether eunomia installs its own settings button. Defaults to {@code true}. */
    public static boolean settingsButtonEnabled() {
        return settingsButtonEnabled;
    }

    /**
     * Turns eunomia's built-in settings button on or off. Order-independent: switching it off after the client
     * already installed the button removes it again, and switching it on before client init simply means the
     * button is installed when init runs.
     * @param value {@code true} to install eunomia's own button, {@code false} to suppress it.
     */
    public static void setSettingsButtonEnabled(boolean value) {
        settingsButtonEnabled = value;
        Runnable observer = settingsButtonObserver;
        if (observer != null) {
            observer.run();
        }
    }

    /**
     * Installs the callback that reconciles the button registration with {@link #settingsButtonEnabled()}.
     * Called by eunomia's own client init; a consuming mod has no reason to touch this.
     * @param observer the reconciler, or {@code null} to detach.
     */
    public static void setSettingsButtonObserver(@Nullable Runnable observer) {
        settingsButtonObserver = observer;
    }

    /** Restores both flags (and drops the observer). Tests, and consumers wanting a clean slate. */
    public static void reset() {
        toastsEnabled = true;
        settingsButtonEnabled = true;
        settingsButtonObserver = null;
    }
}
