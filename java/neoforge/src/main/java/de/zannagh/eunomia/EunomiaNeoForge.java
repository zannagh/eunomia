package de.zannagh.eunomia;

import de.zannagh.eunomia.client.EunomiaClient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@Mod(Eunomia.MOD_ID)
public final class EunomiaNeoForge {

    public EunomiaNeoForge(IEventBus modEventBus) {
        Eunomia.init();
        // FMLClientSetupEvent only fires on a physical client, so the client half (and EunomiaClient,
        // which touches client-only Minecraft classes) is never loaded on a dedicated server.
        modEventBus.addListener(EunomiaNeoForge::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        EunomiaClient.init();
    }
}
