package de.zannagh.eunomia.networking.admin;

import de.zannagh.eunomia.configuration.EunomiaServerConfig;

/**
 * The seam between the platform-neutral settings exchange and wherever a given server flavour actually keeps
 * {@code eunomia-server.json}. The loaders back it with {@code Eunomia.SERVER_CONFIG_PROVIDER}, the Paper
 * plugin with its {@code PaperServerConfig} - neither of which {@code :core} may name.
 *
 * <p>{@link #current()} is deliberately re-read rather than snapshotted: the handshake advertises the policy
 * through a supplier that is evaluated per probe, so a write that lands in the provider is picked up by the
 * next joining client without anything re-registering. Reading fresh here keeps the answer sent back to the
 * writing admin agreeing with what the next client will be told.</p>
 *
 * @since 0.3.0
 */
public interface ServerSettingsAccess {

    /** The configuration as it stands right now. Never {@code null}. */
    EunomiaServerConfig current();

    /**
     * Stores {@code updated} durably and makes it the value {@link #current()} returns from here on.
     *
     * @param updated the already-validated replacement configuration.
     */
    void persist(EunomiaServerConfig updated);
}
