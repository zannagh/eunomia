package de.zannagh.eunomia.paper.admin;

import de.zannagh.eunomia.configuration.EunomiaServerConfig;
import de.zannagh.eunomia.networking.admin.ServerSettingsAccess;
import de.zannagh.eunomia.paper.config.PaperServerConfig;

import java.util.Objects;

/**
 * Paper-side storage for the admin settings exchange, reading and writing through the same
 * {@link PaperServerConfig} the plugin installed as the capability handshake's policy source.
 *
 * <p>That identity is what makes an accepted write take effect: {@code PaperServerConfig#install} hands
 * {@code CommunicationManager} a supplier over {@code policy()}, evaluated per probe, and {@code policy()}
 * derives from the same provider value {@link #persist} replaces - so the next joining client is advertised
 * the new policy without the plugin re-registering the handshake.</p>
 *
 * @since 0.3.0
 */
final class PaperSettingsAccess implements ServerSettingsAccess {

    private final PaperServerConfig config;

    PaperSettingsAccess(PaperServerConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public EunomiaServerConfig current() {
        return config.value();
    }

    @Override
    public void persist(EunomiaServerConfig updated) {
        config.apply(updated);
    }
}
