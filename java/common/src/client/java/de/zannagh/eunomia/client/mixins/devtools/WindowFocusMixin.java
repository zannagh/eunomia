// Keeps the FCGT (fabric-client-gametest) window from stealing OS focus on macOS - it otherwise pops to
// the foreground the moment it is created and kicks the developer out of whatever fullscreen app they
// are in, once per gametest loop, for every variant the smoke suite runs.
//
// Why GLFW window hints and not a JVM flag: GLFW on macOS ignores the process-level
// -Dapple.awt.UIElement hint, because GLFW installs its own NSApplication delegate and forces a
// Regular activation policy during glfwInit. The only reliable lever is the pair of GLFW window hints,
// set immediately before the native window is created:
//   - GLFW_FOCUSED=false        stops the create-time [NSApp activateIgnoringOtherApps] grab.
//   - GLFW_FOCUS_ON_SHOW=false  stops the later glfwShowWindow from taking focus.
// GLFW hints are sticky global state, so they have to land between vanilla's own hint block and the
// glfwCreateWindow call - hence an INVOKE/BEFORE injection point rather than HEAD or RETURN.
//
// Version coverage - three distinct eras, each verified with `javap -p -c` against the loom-mapped
// Minecraft jars under ~/.gradle/caches/fabric-loom/minecraftMaven/:
//   1) <= 1.21.11  : Window's constructor calls GLFW.glfwCreateWindow inline; there is no
//                    createGlfwWindow method yet. Handled by the sibling LegacyWindowFocusMixin.
//   2) 26.1.2/26.2 : creation was extracted into the static
//                    Window#createGlfwWindow(int,int,String,long,GpuBackend). THIS file.
//   3) 26.3+       : the window backend moved to SDL and Window no longer references GLFW at all.
//                    That era is covered outside the game code, by the SDL_WINDOW_ACTIVATE_WHEN_SHOWN
//                    and SDL_WINDOW_ACTIVATE_WHEN_RAISED environment variables the clientGametest run
//                    config sets, so this file is gated out there.
//
// Both mixins are registered dynamically from EunomiaClientMixinPlugin#getMixins() behind the *same*
// stonecutter gates as the files themselves, never statically in eunomia.client.mixins.json - a static
// listing would resolve to the empty stub this file compiles to outside its gate and would (rightly)
// trip the `checkMixinConfigs` guard.
//
// Only active under the fabric.client.gametest system property, so ordinary dev/play windows still
// focus exactly as they always did.
//
// Upper bound is 26.3-0.alpha.1, NOT 26.3-0.snapshot.2: stonecutter compares prerelease identifiers
// alphabetically, so "pre" < "snapshot" and the active 26.3-0.pre.2 parses as LOWER than any
// 26.3-0.snapshot.* despite shipping later. A snapshot bound would wrongly re-include this GLFW hook
// on 26.3 (SDL backend, no org.lwjgl.glfw). An "alpha" sentinel sorts before every real 26.3
// prerelease, so the whole 26.3 series is excluded.
//? if >= 26.1-0.snapshot.10 && < 26.3-0.alpha.1 {
package de.zannagh.eunomia.client.mixins.devtools;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings("UnusedMixin")
@Mixin(Window.class)
public class WindowFocusMixin {

    // require = 0: purely cosmetic dev tooling. If Mojang moves the call again we want the focus grab
    // to come back quietly, never a hard crash at client load.
    @Inject(
            method = "createGlfwWindow",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J",
                    shift = At.Shift.BEFORE
            ),
            require = 0
    )
    // createGlfwWindow returns long, so Mixin demands a CallbackInfoReturnable<Long> here even though
    // we never set a return value - we only run before the native create call and let it proceed.
    private static void eunomia$suppressGametestFocusSteal(CallbackInfoReturnable<Long> cir) {
        if (System.getProperty("fabric.client.gametest") == null) {
            return;
        }
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
    }
}
//?}
