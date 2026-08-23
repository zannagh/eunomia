package de.zannagh.eunomia.client.toast;

import de.zannagh.eunomia.configuration.EunomiaClientOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Game-version-agnostic entry point for raising toasts — the small notification cards that slide in at
 * the top right of the screen. A consuming mod supplies its own {@link Component}s (eunomia ships no
 * lang files and passes translation keys straight through) and eunomia handles the per-version vanilla
 * differences, which span 1.20.1 through 26.3.
 *
 * <p>Every entry point is safe from any thread. Netty read threads and background workers are the usual
 * callers, so the API marshals onto the client thread itself via {@code Minecraft.execute} rather than
 * making that each consumer's problem. It is also safe with no client present: on a dedicated server, in
 * gametests, or before the client has a render context, calls degrade to a no-op instead of throwing.
 *
 * <p>{@link #setEnabled(boolean)} is a single global kill switch covering every toast raised through this
 * class, including eunomia's own. It exists so a consuming mod (or a user-facing setting) can suppress
 * library toasts wholesale without eunomia having to know about each call site. The flag itself is stored
 * in {@code EunomiaClientOptions} rather than here, so that the same switch is reachable from a consuming
 * mod's <em>common</em> initializer - a dedicated server may not class-load this class, but it can set a
 * boolean. There is exactly one flag; these two methods are a client-side view onto it.
 */
public final class EunomiaToasts {

    private EunomiaToasts() {
    }

    /**
     * Turns all eunomia toast rendering on or off. Defaults to on. Disabling drops toasts silently at
     * the point of the call; nothing is queued up to appear later when re-enabled.
     * @param value {@code true} to allow toasts, {@code false} to suppress them.
     */
    public static void setEnabled(boolean value) {
        EunomiaClientOptions.setToastsEnabled(value);
    }

    /**
     * Whether toasts raised through this class currently reach the screen.
     * @return {@code true} while toasts are enabled.
     */
    public static boolean isEnabled() {
        return EunomiaClientOptions.toastsEnabled();
    }

    /**
     * Shows a one-off toast. Stacks below anything already on screen.
     * @param title the bold first line.
     * @param description the smaller second line, or {@code null} for none.
     */
    public static void show(Component title, @Nullable Component description) {
        toast(title).description(description).show();
    }

    /**
     * Shows a toast grouped under an id, so it can later be replaced in place by another toast with the
     * same id (see {@link ToastBuilder#replaceExisting(boolean)}).
     * @param id the toast family this notification belongs to.
     * @param title the bold first line.
     * @param description the smaller second line, or {@code null} for none.
     */
    public static void show(ToastId id, Component title, @Nullable Component description) {
        toast(title).id(id).description(description).show();
    }

    /**
     * Starts a fluent toast. Call {@link ToastBuilder#show()} to raise it.
     * @param title the bold first line.
     * @return a builder for the optional settings.
     */
    public static ToastBuilder toast(Component title) {
        return new ToastBuilder(title);
    }

    /**
     * Central gate and thread hop. Kept package-private: the enabled check and the client-thread
     * marshalling must happen for every path into {@link ToastDispatcher}, so there is exactly one.
     */
    static void enqueue(ToastId id, Component title, Component description, boolean replaceExisting) {
        if (!EunomiaClientOptions.toastsEnabled()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        minecraft.execute(() -> ToastDispatcher.dispatch(id, title, description, replaceExisting));
    }
}
