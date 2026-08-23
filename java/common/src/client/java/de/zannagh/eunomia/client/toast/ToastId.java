package de.zannagh.eunomia.client.toast;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An opaque, interned identity for a family of toasts. Two toasts raised with the same {@code ToastId}
 * are considered the same notification by the game: {@link ToastBuilder#replaceExisting(boolean)} then
 * updates the on-screen toast in place instead of stacking a second one below it.
 *
 * <p>Instances are deliberately opaque and carry no Minecraft types. The mapping from an id to the
 * per-version vanilla token happens inside the package, on the client thread, so that merely holding a
 * {@code ToastId} never loads a client-only game class — which matters for gametests and for dedicated
 * servers that classload the mod jar wholesale.
 *
 * <p>Ids are interned by key, so {@code ToastId.of("mymod:sync")} always yields the same instance and
 * consumers may hold it in a {@code static final} field or look it up ad hoc with equal results.
 */
public final class ToastId {

    private static final Map<String, ToastId> INTERNED = new ConcurrentHashMap<>();

    /** Key used by {@link EunomiaToasts#show(net.minecraft.network.chat.Component, net.minecraft.network.chat.Component)}. */
    private static final String DEFAULT_KEY = "eunomia:default";

    private final String key;

    private ToastId(String key) {
        this.key = key;
    }

    /**
     * Returns the id for the given key, creating it on first use. Use a namespaced key
     * ({@code "mymod:something"}) so ids from different mods cannot collide.
     * @param key the stable identity of this toast family; must not be blank.
     * @return the interned id for {@code key}.
     */
    public static ToastId of(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Toast id key must not be blank");
        }
        return INTERNED.computeIfAbsent(key, ToastId::new);
    }

    /**
     * The shared id used when a caller does not supply one. Toasts raised under it stack rather than
     * replace each other, which is the right default for one-off notifications.
     * @return the default id.
     */
    public static ToastId defaultId() {
        return of(DEFAULT_KEY);
    }

    /**
     * The key this id was created from. Exposed mainly for logging and debugging.
     * @return the namespaced key.
     */
    public String key() {
        return key;
    }

    @Override
    public String toString() {
        return "ToastId[" + key + "]";
    }
}
