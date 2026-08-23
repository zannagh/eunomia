package de.zannagh.eunomia.admin;

import de.zannagh.eunomia.Eunomia;
import de.zannagh.eunomia.configuration.EunomiaServerConfig;
import de.zannagh.eunomia.networking.admin.ServerSettingsAccess;

/**
 * Loader-side storage for the admin settings exchange, reading and writing through the very provider the
 * capability handshake's policy supplier reads.
 *
 * <p>That identity is the point. {@code Eunomia.initConfiguration} installs
 * {@code () -> getServerConfig().toSyncPolicy()} as the source {@code CommunicationManager} evaluates
 * <em>per probe</em>, and {@code getServerConfig()} is this provider's current value - so a write persisted
 * here is advertised to the next client that joins, with nothing re-registering the handshake and no cached
 * policy to invalidate.</p>
 *
 * @since 0.3.0
 */
final class ServerConfigProviderAccess implements ServerSettingsAccess {

    @Override
    public EunomiaServerConfig current() {
        return Eunomia.getServerConfig();
    }

    @Override
    public void persist(EunomiaServerConfig updated) {
        // Bootstraps the provider if a consumer somehow reached a packet handler before init did.
        Eunomia.initConfiguration();
        Eunomia.SERVER_CONFIG_PROVIDER.updateAndSave(updated);
    }
}
