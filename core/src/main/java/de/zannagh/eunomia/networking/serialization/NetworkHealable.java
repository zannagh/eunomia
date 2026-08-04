package de.zannagh.eunomia.networking.serialization;

/**
 * Optional post-decode hook. A payload class that implements this is given a chance to repair itself
 * immediately after being deserialized off the wire - the same healing/migration a config gets when
 * read from disk, applied to data that arrived from an untrusted peer.
 * <p>
 * Kept in the core (not tied to the config framework) so the codec never needs to know about
 * Minecraft-coupled config types: it just checks {@code instanceof NetworkHealable} and calls
 * {@link #heal()}.
 */
public interface NetworkHealable {
    /** Repair this instance in place after it was decoded from the network. */
    void heal();
}
