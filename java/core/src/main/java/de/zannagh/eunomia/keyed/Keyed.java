package de.zannagh.eunomia.keyed;

/**
 * A value that carries its own {@link KeyPath} - its primary key. A self-keyed payload sent as a
 * {@link de.zannagh.eunomia.networking.packets.KeyedPacket} tells the receiving {@link KeyedStore}
 * exactly where it belongs, so the server-side wiring is a single {@code store.handleOn(packet)} with
 * no per-packet extractor to write.
 * <p>
 * The returned path's {@link KeyPath#length() length} must match the store's key depth; a store keyed
 * by player id expects a one-segment path, a per-slot store a two-segment one, and so on.
 *
 * @since 0.1.0
 */
public interface Keyed {

    /** This value's primary key - where it is stored and looked up in a {@link KeyedStore}. */
    KeyPath keyPath();
}
