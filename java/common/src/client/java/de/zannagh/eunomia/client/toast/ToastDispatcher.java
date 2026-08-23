package de.zannagh.eunomia.client.toast;

import de.zannagh.eunomia.Eunomia;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * The single place where the public toast API touches vanilla. Everything here runs on the client
 * thread (callers reach it only through {@link EunomiaToasts}, which marshals), so the token cache
 * needs no synchronisation.
 *
 * <p>The implementation builds on {@code SystemToast} rather than a custom {@code Toast}: the
 * {@code Toast} interface changed shape three times across the supported matrix (render-returns-visibility
 * → getWantedVisibility/update/render → extractRenderState), whereas the static
 * {@code SystemToast.add}/{@code addOrUpdate} entry points kept an identical four-argument shape from
 * 1.20.1 through 26.3. Only the toast-manager accessor and the id type name move, and both are handled
 * below.
 */
final class ToastDispatcher {

    /**
     * Per-key vanilla tokens, created lazily. Confined to the client thread by construction.
     */
    private static final Map<String, SystemToast.SystemToastId> TOKENS = new HashMap<>();

    /**
     * Ceiling on cached tokens. The map is keyed by {@link ToastId#key()}, which is consumer-supplied and may
     * well be generated (one id per entity, per key path, ...), and nothing ever removed from it - so it was an
     * unbounded cache living for the whole game session. Past the cap a fresh token is minted per toast
     * instead: the only property lost is replace-in-place collapsing onto the same slot, which is cosmetic and
     * strictly better than growing forever.
     */
    private static final int MAX_TOKENS = 256;

    private ToastDispatcher() {
    }

    /**
     * Hands one toast to the game. Never throws: a toast is cosmetic, and a client crash on a purely
     * informational notification would be a far worse outcome than a missing popup.
     */
    static void dispatch(ToastId id, Component title, Component description, boolean replaceExisting) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        // 26.2-snapshot-3 moved the toast manager off Minecraft and onto Gui, which is itself only
        // constructed once the client has a render context - hence the null guard on that branch.
        // Below that boundary the accessor lives on Minecraft, and a Stonecutter replacement renames
        // it to the pre-1.21.2 `getToasts()` form (see stonecutter.gradle.kts).
        //? if >= 26.2-0.snapshot.3 {
        var toasts = minecraft.gui == null ? null : minecraft.gui.toastManager();
        //?} else {
        /*var toasts = minecraft.getToastManager();
        *///?}
        if (toasts == null) {
            return;
        }
        try {
            SystemToast.SystemToastId token = tokenFor(id);
            if (replaceExisting) {
                SystemToast.addOrUpdate(toasts, token, title, description);
            } else {
                SystemToast.add(toasts, token, title, description);
            }
        } catch (Exception e) {
            Eunomia.LOGGER.debug("Failed to raise toast {}", id.key(), e);
        }
    }

    private static SystemToast.SystemToastId tokenFor(ToastId id) {
        String key = id.key();
        SystemToast.SystemToastId cached = TOKENS.get(key);
        if (cached != null) {
            return cached;
        }
        SystemToast.SystemToastId token = newToken();
        if (TOKENS.size() < MAX_TOKENS) {
            TOKENS.put(key, token);
        }
        return token;
    }

    /**
     * Mints a fresh vanilla token. From 1.21 the token type is an ordinary class with a public
     * constructor, so every {@link ToastId} gets its own. On 1.20.1 it is still a closed enum, so all
     * eunomia toasts necessarily share the {@code PERIODIC_NOTIFICATION} constant there; stacking (the
     * default) behaves identically, only replace-in-place collapses across ids on that one version.
     */
    private static SystemToast.SystemToastId newToken() {
        //? if >= 1.21 {
        return new SystemToast.SystemToastId();
        //?}
        //? if < 1.21 {
        /*return SystemToast.SystemToastId.PERIODIC_NOTIFICATION;
        *///?}
    }
}
