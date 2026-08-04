plugins {
    id("dev.kikugie.stonecutter")
    id("net.neoforged.moddev") version "2.0.140" apply false
}

stonecutter active "fabric-26.2" /* [SC] DO NOT EDIT */

stonecutter parameters {
    // Cross-version source replacements go here (Stonecutter rewrites matching tokens per active
    // MC version at generation time). Empty for the bare library base - a consuming mod adds the
    // rules its own source needs, e.g.:
    //   replacements.string(current.parsed >= "1.21.11") { replace("ResourceLocation", "Identifier") }

    replacements.string(current.parsed < "1.20.5") { replace("net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket", "net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket")}
    replacements.string(current.parsed >= "1.21.11") { replace("ResourceLocation", "Identifier") }
    replacements.string(false) { replace("packet.getIdentifier()", "packet.getResourceLocation()") }
    replacements.string(current.parsed <= "1.21.8") { replace("AvatarRenderState", "PlayerRenderState") }
    replacements.string(current.parsed < "1.21.11") { replace("net.minecraft.client.renderer.rendertype.RenderType", "net.minecraft.client.renderer.RenderType") }
    replacements.string(current.parsed < "1.21.11") { replace("Lnet/minecraft/client/renderer/rendertype/RenderType", "Lnet/minecraft/client/renderer/RenderType")}
    replacements.string(current.parsed < "1.21.11") { replace("net.minecraft.client.model.player.PlayerModel", "net.minecraft.client.model.PlayerModel") }
    replacements.string(current.parsed < "26.1-0.snapshot.11") { replace("net.minecraft.client.renderer.state.level.CameraRenderState", "net.minecraft.client.renderer.state.CameraRenderState") }
    replacements.string(current.parsed <= "26.1-1.pre.1") { replace("net.minecraft.client.gui.GuiGraphicsExtractor", "net.minecraft.client.gui.GuiGraphics") }
    replacements.string(current.parsed < "1.21") { replace("net.minecraft.client.gui.screens.options.OptionsSubScreen", "net.minecraft.client.gui.screens.OptionsSubScreen") }
    replacements.string(current.parsed < "1.21.9") { replace(".setScreenAndShow(", ".setScreen(") }
    replacements.string(current.parsed <= "1.21.1") { replace("WingsLayer", "ElytraLayer") }
    replacements.string(current.parsed < "1.21") { replace("net.minecraft.client.gui.screens.options.SkinCustomizationScreen", "net.minecraft.client.gui.screens.SkinCustomizationScreen")}
    replacements.string(current.parsed < "1.21") { replace("net.minecraft.client.gui.screens.options.OptionsScreen", "net.minecraft.client.gui.screens.OptionsScreen")}
    // 26.3-snapshot-3 extracted the render pipeline API out of blaze3d into the new
    // com.mojang.renderpearl module (same types/methods, new package) and changed
    // BakedQuad.MaterialInfo's boolean shade() accessor to Direction shadeDirectionOverride()
    // (same constructor slot, value passed straight through).
    replacements.string(current.parsed >= "26.3-0.snapshot.3") { replace("com.mojang.blaze3d.pipeline.RenderPipeline", "com.mojang.renderpearl.api.pipeline.RenderPipeline") }
    replacements.string(current.parsed >= "26.3-0.snapshot.3") { replace("com.mojang.blaze3d.pipeline.DepthStencilState", "com.mojang.renderpearl.api.pipeline.DepthStencilState") }
    replacements.string(current.parsed >= "26.3-0.snapshot.3") { replace("com.mojang.blaze3d.pipeline.ColorTargetState", "com.mojang.renderpearl.api.pipeline.ColorTargetState") }
    replacements.string(current.parsed >= "26.3-0.snapshot.3") { replace("info.shade()", "info.shadeDirectionOverride()") }

}
