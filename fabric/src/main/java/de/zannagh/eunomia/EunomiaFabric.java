package de.zannagh.eunomia;

import net.fabricmc.api.ModInitializer;

public final class EunomiaFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Eunomia.init();
    }
}
