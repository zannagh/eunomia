package de.zannagh.eunomia;

import de.zannagh.eunomia.client.EunomiaClient;
import de.zannagh.eunomia.networking.loader.NeoForgePayloadRegistration;
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
        // The mod event bus, not the game bus: RegisterPayloadHandlersEvent is an IModBusEvent. This is
        // eunomia's ONLY wire transport on NeoForge - the custom-payload codec-injection mixins that carry
        // it on Fabric cannot match here, because NeoForge patches CustomPacketPayload.codec() to a
        // four-argument signature. See NeoForgePayloadRegistration for the full account.
        modEventBus.addListener(NeoForgePayloadRegistration::onRegisterPayloadHandlers);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        EunomiaClient.init();
    }
}
