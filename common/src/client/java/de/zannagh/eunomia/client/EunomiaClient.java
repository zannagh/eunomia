package de.zannagh.eunomia.client;

import de.zannagh.eunomia.Eunomia;

/**
 * Shared client-side entry point for the Eunomia library. The per-loader client initializers
 * delegate to {@link #init()}.
 */
public final class EunomiaClient {

    private EunomiaClient() {
    }

    public static void init() {
        Eunomia.LOGGER.info("Eunomia client initialized");
    }
}
