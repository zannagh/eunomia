package de.zannagh.eunomia.client;

import net.fabricmc.api.ClientModInitializer;

public final class EunomiaFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EunomiaClient.init();
    }
}
