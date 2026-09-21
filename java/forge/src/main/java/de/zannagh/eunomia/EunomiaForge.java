package de.zannagh.eunomia;

import de.zannagh.eunomia.forge.client.EunomiaForgeClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge (classic / LexForge, 1.20.1 only) entry point for the Eunomia library.
 *
 * <p>Classic Forge constructs the mod class with a NO-ARG constructor - the NeoForge-style
 * {@code IEventBus} parameter injection does not exist here. Anything that needs the mod event bus
 * pulls it from {@code FMLJavaModLoadingContext.get().getModEventBus()} instead (see
 * {@link EunomiaForgeClient}).</p>
 *
 * <p>The client half is reached through {@link DistExecutor} rather than through a client-only
 * source set: classic Forge has no split environments, so the dist check IS the gate. The supplier
 * is only class-loaded on the physical client, which keeps {@code EunomiaForgeClient} (and the
 * client-only Minecraft classes it touches) off a dedicated server entirely.</p>
 */
@Mod(Eunomia.MOD_ID)
public final class EunomiaForge {

    public EunomiaForge() {
        Eunomia.init();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> EunomiaForgeClient::init);
    }
}
