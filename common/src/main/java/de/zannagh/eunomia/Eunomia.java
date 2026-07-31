package de.zannagh.eunomia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared entry point for the Eunomia library. Holds nothing loader-specific; the per-loader
 * initializers ({@code EunomiaFabric}, {@code EunomiaNeoForge}) delegate to {@link #init()}.
 */
public final class Eunomia {
    public static final String MOD_ID = "eunomia";
    public static final Logger LOGGER = LoggerFactory.getLogger("Eunomia");

    private Eunomia() {
    }

    public static void init() {
        LOGGER.info("Eunomia shared library loaded");
    }
}
