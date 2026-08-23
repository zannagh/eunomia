package de.zannagh.eunomia.configuration;

/**
 * The framework-level defaults for eunomia's external ("Cloud Sync") transport - the lowest rung of the
 * effective-settings precedence chain, used whenever neither the player, nor the joined server, nor the
 * consuming mod expressed an opinion.
 * <p>
 * These literals live here and nowhere else. The client config, the server config, the handshake payload's
 * tolerant v1 decoding and the settings resolver all reference these constants, so the baked-in relay address
 * can be moved by editing exactly one line instead of hunting duplicated string literals across three modules.
 *
 * @since 0.3.0
 */
public final class EunomiaDefaults {

    private EunomiaDefaults() {
    }

    /**
     * The relay eunomia ships with. A framework default only: it is never contacted unless the external
     * fallback is switched on somewhere in the chain, so a plain install still sends nothing off-box.
     */
    public static final String DEFAULT_EXTERNAL_SERVER_ADDRESS = "https://eunomia.zannagh.me";

    /** Opt-in by default off: no player data leaves for a third-party server unless someone asks for it. */
    public static final boolean DEFAULT_ENABLE_EXTERNAL_FALLBACK = false;

    /**
     * Whether the external relay should be preferred even when the joined Minecraft server speaks eunomia.
     * Off by default: the in-game transport is the cheaper, lower-latency and more private path, so taking
     * the relay instead has to be an explicit decision.
     */
    public static final boolean DEFAULT_PREFER_EXTERNAL_TRANSPORT = false;
}
