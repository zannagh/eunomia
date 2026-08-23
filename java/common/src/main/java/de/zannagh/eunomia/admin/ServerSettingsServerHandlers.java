package de.zannagh.eunomia.admin;

import de.zannagh.eunomia.networking.admin.ServerSettingsExchange;

/**
 * The loader (Fabric/NeoForge) binding of {@link ServerSettingsExchange}: it points the shared exchange at
 * {@code Eunomia.SERVER_CONFIG_PROVIDER} and at {@code ServerUtil}'s permission ladder, and registers it.
 * The Paper plugin does the same with its own two answers - the exchange itself, and therefore the security
 * decision, is one implementation shared by both rather than two that could drift.
 *
 * <p>Lives in the main source set and names no client type, because a dedicated server loads exactly this
 * half. {@code DedicatedServerSafetyTest} asserts as much against the compiled bytes.</p>
 *
 * @since 0.3.0
 */
public final class ServerSettingsServerHandlers {

    private ServerSettingsServerHandlers() {
    }

    /** Wires and registers the admin settings channel. Called once from {@code Eunomia#init()}. */
    public static void register() {
        new ServerSettingsExchange(new ServerConfigProviderAccess(), new LoaderSettingsAuthority()).register();
    }
}
