package de.zannagh.eunomia.paper.admin;

import de.zannagh.eunomia.networking.admin.ServerSettingsExchange;
import de.zannagh.eunomia.paper.config.PaperServerConfig;
import de.zannagh.eunomia.paper.perm.PermissionResolver;

/**
 * The Paper binding of {@link ServerSettingsExchange}. The plugin calls {@link #register} at enable, after
 * loading its {@link PaperServerConfig} and before it enumerates the registered channels for Bukkit
 * plugin-messaging registration - otherwise the three admin channels would exist in the manager but have no
 * Bukkit channel to travel on.
 *
 * <p>A Paper server is a peer of the loaders on this wire, not a lesser participant: the same channels, the
 * same payloads, and the same server-side permission and address checks, because the checking code is the
 * shared {@link ServerSettingsExchange} and only the two answers it consults are platform-specific.</p>
 *
 * @since 0.3.0
 */
public final class PaperServerSettings {

    private PaperServerSettings() {
    }

    /**
     * Wires and registers the admin settings channel for this plugin.
     *
     * @param config      the plugin's Cloud Sync config binding, also the handshake's policy source.
     * @param permissions the resolver built at enable, reused so LuckPerms is detected exactly once.
     */
    public static void register(PaperServerConfig config, PermissionResolver permissions) {
        new ServerSettingsExchange(new PaperSettingsAccess(config), new PaperSettingsAuthority(permissions))
                .register();
    }
}
