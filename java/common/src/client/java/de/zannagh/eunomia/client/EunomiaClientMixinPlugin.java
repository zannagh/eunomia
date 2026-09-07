package de.zannagh.eunomia.client;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Adds the client mixins dynamically. This is how the version-specific client networking mixin is
 * chosen: the modern {@code ClientPacketListenerMixin} injects a {@code handleCustomPayload} whose
 * signature only exists on 1.20.5+, so the legacy {@code ClientPlayNetworkHandlerMixin} is added
 * instead on 1.20.x. Stonecutter resolves exactly one of the two into this list per game version.
 */
public class EunomiaClientMixinPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return "";
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // The networking client mixins, the generic screen-transformation hook, and the dev-tooling
        // hooks must apply. Other dynamically-listed mixins keep whatever gate they had (DevSkin stays
        // off until it is wired up).
        //
        // NOTE the shape of this gate: it is a package-substring allowlist, so a mixin sitting directly
        // in the bare `mixins` package would be listed by getMixins() below and then silently dropped
        // here. Anything added must live in - and be matched by - one of these subpackages. That is why
        // the window-focus hooks live in `mixins.devtools` and why `.devtools.` is listed here.
        return mixinClassName.contains(".networking.")
                || mixinClassName.contains(".ui.")
                || mixinClassName.contains(".devtools.");
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        List<String> mixins = new ArrayList<>();
        mixins.add("DevSkinMixin");
        // The game-version-agnostic screen-transformation hook (registry in client.ui).
        mixins.add("ui.ScreenMixin");
        // Its init-time counterpart: the hook that lets a registered ScreenInitializer add a real,
        // persistent, narratable widget to a vanilla screen (registry in client.ui). Separate from
        // ScreenMixin because it targets Screen#init / Screen#rebuildWidgets, not the render entrypoint.
        mixins.add("ui.ScreenInitMixin");
        // Dev tooling: stop the fabric-client-gametest window from stealing macOS focus. Two eras,
        // two classes, each gated exactly like the file it names - 26.1.2/26.2 hook the extracted
        // static Window#createGlfwWindow, everything at or below 1.21.11 hooks the Window constructor
        // that still calls glfwCreateWindow inline. 26.3+ moved to SDL and is handled by the
        // SDL_WINDOW_ACTIVATE_* env vars on the clientGametest run config, so neither is added there.
        // Registered here rather than statically in eunomia.client.mixins.json precisely because the
        // classes compile to empty stubs outside their gate, which `checkMixinConfigs` rejects.
        // Upper bound 26.3-0.alpha.1 (not .snapshot.2): "pre" sorts before "snapshot" in stonecutter's
        // prerelease comparison, so the active 26.3-0.pre.2 must be excluded via an alpha sentinel that
        // precedes every real 26.3 prerelease. Kept identical to WindowFocusMixin's own file gate.
        //? if >= 26.1-0.snapshot.10 && < 26.3-0.alpha.1 {
        mixins.add("devtools.WindowFocusMixin");
        //?}
        //? if < 26.1-0.snapshot.10 {
        /*mixins.add("devtools.LegacyWindowFocusMixin");
        *///?}
        //? if >= 1.20.5 {
        mixins.add("networking.ClientPacketListenerMixin");
        //?}
        //? if < 1.20.5 {
        /*mixins.add("networking.ClientPlayNetworkHandlerMixin");
        *///?}
        return mixins;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
