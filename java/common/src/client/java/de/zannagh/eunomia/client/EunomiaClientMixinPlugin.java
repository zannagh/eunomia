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
        // The networking client mixins and the generic screen-transformation hook must apply. Other
        // dynamically-listed mixins keep whatever gate they had (DevSkin stays off until it is wired up).
        return mixinClassName.contains(".networking.") || mixinClassName.contains(".ui.");
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
