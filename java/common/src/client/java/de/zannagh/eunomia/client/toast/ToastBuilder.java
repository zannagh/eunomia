package de.zannagh.eunomia.client.toast;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Fluent configuration for a single toast. Obtain one from {@link EunomiaToasts#toast(Component)},
 * set the optional bits, then call {@link #show()}.
 *
 * <p>The knobs exposed here are exactly the ones that behave the same on every supported game version.
 * Vanilla's per-version extras (custom icons, explicit display durations, the multiline helper that was
 * dropped in 26.2) are deliberately absent: a knob that silently does nothing on half the matrix is
 * worse than no knob at all.
 *
 * <p>A builder is not thread-safe and is meant to be configured and shown in one expression; the
 * {@link #show()} call itself is safe from any thread.
 */
public final class ToastBuilder {

    private final Component title;
    private Component description = Component.empty();
    private ToastId id = ToastId.defaultId();
    private boolean replaceExisting;

    ToastBuilder(Component title) {
        if (title == null) {
            throw new IllegalArgumentException("Toast title must not be null");
        }
        this.title = title;
    }

    /**
     * Sets the smaller second line. Defaults to empty. Eunomia ships no lang files, so pass a
     * {@code Component} your own mod resolved (usually {@code Component.translatable("mymod.…")}).
     * @param value the description line, or {@code null} for none.
     * @return this builder.
     */
    public ToastBuilder description(@Nullable Component value) {
        description = value == null ? Component.empty() : value;
        return this;
    }

    /**
     * Groups this toast under an id. Only meaningful together with {@link #replaceExisting(boolean)},
     * which uses the id to find the toast to update.
     * @param value the id to raise this toast under.
     * @return this builder.
     */
    public ToastBuilder id(ToastId value) {
        id = value == null ? ToastId.defaultId() : value;
        return this;
    }

    /**
     * Convenience for {@code id(ToastId.of(key))}.
     * @param key a namespaced key such as {@code "mymod:sync"}.
     * @return this builder.
     */
    public ToastBuilder id(String key) {
        return id(ToastId.of(key));
    }

    /**
     * When {@code true}, a toast already on screen under the same {@link ToastId} is rewritten with
     * this toast's text and its timer restarted, instead of a second toast stacking below it. Use it
     * for status that supersedes itself (a progress or connection state); leave it off for events the
     * user should see each time.
     * @param value whether to replace rather than stack.
     * @return this builder.
     */
    public ToastBuilder replaceExisting(boolean value) {
        replaceExisting = value;
        return this;
    }

    /**
     * Raises the toast. Safe to call from any thread — the work is marshalled onto the client thread —
     * and a harmless no-op when toasts are disabled or no client is running.
     */
    public void show() {
        EunomiaToasts.enqueue(id, title, description, replaceExisting);
    }
}
