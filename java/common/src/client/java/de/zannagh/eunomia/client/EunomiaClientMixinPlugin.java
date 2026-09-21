package de.zannagh.eunomia.client;

//? if forge {
/*import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
*///?}
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

    /**
     * Returns {@code null}, never {@code ""}.
     *
     * <p>Mixin only consults this method when the config JSON carries no {@code "refmap"} field
     * (see {@code MixinConfig.onSelect}: {@code refMapperConfig} is read from the JSON first and the
     * plugin is asked only if it is still null). A NON-null return is then used verbatim as the
     * resource path handed to {@code ReferenceMapper.read}. Returning {@code ""} therefore does not
     * mean "no preference" - it means "read the refmap from the resource named <empty string>",
     * which never resolves, silently yields {@code ReferenceMapper.DEFAULT_MAPPER}, and leaves every
     * obfuscated target unmapped. On Fabric/NeoForge (Mojang-mapped at runtime) that is invisible;
     * in a REOBFUSCATED classic-Forge jar it makes every injection fail to resolve while dev runs
     * stay green.</p>
     *
     * <p>{@code null} is the correct "I have no opinion" answer: Mixin then falls back to
     * {@code ReferenceMapper.DEFAULT_RESOURCE} AND sets its suppress-warning flag, so the absence of
     * a refmap in a deobfuscated dev run stays quiet.</p>
     */
    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        //? if forge {
        /*if (!eunomia$isPhysicalClient()) {
            return false;
        }
        *///?}
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

    /**
     * Whether this is a development launch, and therefore whether the dev-only mixins above may be
     * registered at all. Deliberately property-based rather than loader-API-based: this class is shared
     * by both loaders and runs inside the mixin bootstrap, before either loader's API is safe to touch.
     * {@code fabric.development} is set by Loom (and so by the gametest harness); the skin properties are
     * what the dev run configs pass, and are the only things DevSkinMixin does anything with.
     */
    private static boolean eunomia$isDevelopmentEnvironment() {
        return Boolean.getBoolean("fabric.development")
                || System.getProperty("eunomia.dev.skin.textures") != null
                || System.getProperty("armorhider.dev.skin.textures") != null;
    }

    // Whether this JVM is a physical client, on the one loader that needs to ask.
    //
    // Fabric and NeoForge both let a mixin config declare its side, so their dedicated servers never
    // register this config at all. Classic Forge 1.20.1 cannot: the config is picked up from the
    // `MixinConfigs` MANIFEST attribute, which has no side field, so it loads on a dedicated server
    // too - and ui.ScreenMixin targets a class extending AbstractContainerEventHandler, which is not
    // on a server's classpath. Mixin would fail to load the mixin class and abort the server at boot.
    //
    // Gating here is enough to make the whole config inert, because eunomia.client.mixins.json
    // declares NO static `mixins` array - every entry comes from getMixins() below, so an empty list
    // leaves nothing to apply.
    //
    // Written as a line comment rather than javadoc on purpose: the body below sits inside a
    // stonecutter `/* ... *``/` block on every non-Forge variant, and a javadoc's terminator would
    // close that block early.
    //? if forge {
    /*private static boolean eunomia$isPhysicalClient() {
        return FMLEnvironment.dist == Dist.CLIENT;
    }
    *///?}

    @Override
    public List<String> getMixins() {
        List<String> mixins = new ArrayList<>();
        //? if forge {
        /*if (!eunomia$isPhysicalClient()) {
            return mixins;
        }
        *///?}
        // Dev-only tooling, and ONLY ever added in a development environment. Shipping these to users
        // bought nothing - they are inert without the dev properties that drive them - and cost a boot
        // crash: DevSkinMixin's `get` target does not resolve on a production NeoForge runtime
        // ("No refMap loaded", this build emits none), which was survivable while a non-matching
        // injector was a silent no-op and fatal the moment injectors.defaultRequire was raised.
        // WindowFocusMixin below is the same kind of thing and the same hazard, one place further down
        // the list. Gate the lot: production must not load a mixin that only a developer needs.
        if (eunomia$isDevelopmentEnvironment()) {
            mixins.add("DevSkinMixin");
        }
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
        if (eunomia$isDevelopmentEnvironment()) {
            //? if >= 26.1-0.snapshot.10 && < 26.3-0.alpha.1 {
            mixins.add("devtools.WindowFocusMixin");
            //?}
            //? if < 26.1-0.snapshot.10 {
            /*mixins.add("devtools.LegacyWindowFocusMixin");
            *///?}
        }
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
