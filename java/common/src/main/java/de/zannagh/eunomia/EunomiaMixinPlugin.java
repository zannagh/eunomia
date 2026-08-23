package de.zannagh.eunomia;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Adds the common (non-client) mixins that only exist on some game versions, mirroring
 * {@code de.zannagh.eunomia.client.EunomiaClientMixinPlugin}.
 *
 * <p>{@code eunomia.mixins.json} is {@code "required": true}, so every name in its static
 * {@code mixins} array must resolve to an <em>annotated</em> class on <em>every</em> variant - a
 * stonecutter-stubbed class without {@code @Mixin} makes mixin abort during PREPARE and the game
 * crashes at boot. {@link de.zannagh.eunomia.mixins.PackRepositoryMixin} is exactly that: it only
 * carries {@code @Mixin} on {@code fabric && >= 26.1-0.snapshot.11}. Listing it here instead, behind
 * the identical stonecutter condition, means it is only ever registered where the annotated class
 * actually exists.</p>
 *
 * <p>The same reasoning covers the three {@code >= 1.20.5} networking mixins listed below. Their
 * stonecutter gate wraps the <em>entire</em> file, package declaration included, so on
 * {@code fabric-1.20.1} the class does not exist at all - naming it in the static array crashed the
 * game at boot. They are added here behind the identical gate instead.</p>
 *
 * <p>CONTRACT: every guard below must stay identical to the stonecutter gate on the file it names.</p>
 */
public class EunomiaMixinPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return "";
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // Everything this config declares - the three statically listed networking mixins and the
        // conditionally added ones below - is meant to apply wherever it is registered.
        // Deliberately NOT a package-substring gate like the client plugin's: PackRepositoryMixin
        // sits in the bare `de.zannagh.eunomia.mixins` package with no subpackage, so such a gate
        // would silently drop it on exactly the versions that want it.
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        List<String> mixins = new ArrayList<>();
        //? if fabric && >= 26.1-0.snapshot.11 {
        mixins.add("PackRepositoryMixin");
        //?}
        // The CustomPacketPayload era only starts at 1.20.5: these three mixins inject the eunomia
        // payload codec into the clientbound/serverbound custom-payload packets and dispatch the
        // decoded payload server-side. Each file is stonecutter-gated in its entirety, so below
        // 1.20.5 the classes are not merely stubs - they do not exist - and a "required": true
        // config naming them aborts mixin PREPARE and crashes the game at boot.
        //
        // 1.20.x is not left without serverbound dispatch: networking.ServerPlayNetworkHandlerMixin
        // (statically listed, ungated at class level) carries the pre-payload FriendlyByteBuf path
        // and is inert on 1.20.5+, exactly mirroring the client-side
        // ClientPacketListenerMixin / ClientPlayNetworkHandlerMixin split.
        //? if >= 1.20.5 {
        mixins.add("networking.ClientboundCustomPayloadPacketMixin");
        mixins.add("networking.ServerboundCustomPayloadPacketMixin");
        mixins.add("networking.ServerGamePacketListenerMixin");
        //?}
        return mixins;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
