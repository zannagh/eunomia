// Pre-26.1 half of the FCGT focus-steal suppression. See the sibling WindowFocusMixin for the full
// rationale (macOS/GLFW activation policy, why window hints are the only lever, and the three-era
// coverage matrix); this file only differs in WHERE the native create call lives.
//
// On every version from 1.20.1 through 1.21.11 there is no Window#createGlfwWindow yet:
// GLFW.glfwCreateWindow(IILjava/lang/CharSequence;JJ)J is invoked inline from the constructor
//   Window(WindowEventHandler, ScreenManager, DisplayData, String, String)
// right after vanilla's own glfwWindowHint block. Verified per version with `javap -p -c` on the
// loom-mapped clientonly jars (1.20.1, 1.21.4, 1.21.8, 1.21.10, 1.21.11 all agree - same constructor
// descriptor, same single glfwCreateWindow call site), so one gate covers the whole era.
//
// Injecting into <init> at an INVOKE point is legal here: the point sits far past the super()
// constructor call, which is the only thing Mixin forbids injecting ahead of. The handler declares
// only CallbackInfo - Mixin's supported "omit the target's arguments" handler form - so it does not
// have to track the constructor's parameter list across eight game versions.
//
// Registered dynamically from EunomiaClientMixinPlugin#getMixins() behind this same gate.
//? if < 26.1-0.snapshot.10 {
/*package de.zannagh.eunomia.client.mixins.devtools;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SuppressWarnings("UnusedMixin")
@Mixin(Window.class)
public class LegacyWindowFocusMixin {

    // require = 0: purely cosmetic dev tooling - degrade quietly if the target ever drifts.
    @Inject(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J",
                    shift = At.Shift.BEFORE
            ),
            require = 0
    )
    private void eunomia$suppressGametestFocusSteal(CallbackInfo ci) {
        if (System.getProperty("fabric.client.gametest") == null) {
            return;
        }
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
    }
}
*///?}
