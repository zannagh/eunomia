package de.zannagh.eunomia.forge.client;

import de.zannagh.eunomia.client.EunomiaClient;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Client-only Forge glue. Lives in the MAIN source set on purpose: classic Forge does not split
 * environments, and the {@code DistExecutor} guard in {@code EunomiaForge} is what keeps this class
 * from ever being loaded on a dedicated server.
 *
 * <p>The listener goes on the MOD event bus (fetched from {@link FMLJavaModLoadingContext}, since a
 * classic-Forge mod constructor receives no bus), mirroring the NeoForge entry point: common client
 * init happens in {@link FMLClientSetupEvent}, which only ever fires on a physical client.</p>
 */
public final class EunomiaForgeClient {

    private EunomiaForgeClient() {
    }

    /** Registers the client-setup listener. Called from the mod constructor via {@code DistExecutor}. */
    public static void init() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(EunomiaForgeClient::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        EunomiaClient.init();
    }
}
