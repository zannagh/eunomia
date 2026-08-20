package de.zannagh.eunomia.networking.packets;

import de.zannagh.eunomia.keyed.Keyed;
import de.zannagh.eunomia.keyed.KeyedStore;

/**
 * A {@link PacketType} whose payload is {@link Keyed} - it carries its own primary key. This is the
 * packet counterpart of a {@link KeyedStore}: because the payload self-describes where it belongs, a
 * consumer wires server-side handling in one call ({@code store.handleOn(packet)}) with no per-packet
 * key extractor to write.
 * <p>
 * It is an ordinary {@code PacketType} in every other respect (same {@code namespace:path} identity,
 * same JSON-on-the-wire codec, same direction validation); the only added guarantee is the compile-time
 * {@code T extends Keyed} bound.
 *
 * @param <T> the self-keyed payload type carried on this channel
 * @since 0.1.0
 */
public final class KeyedPacket<T extends Keyed> extends PacketType<T> {

    private KeyedPacket(String namespace, String path, Class<T> payloadClass, PacketDirection direction) {
        super(namespace, path, payloadClass, direction);
    }

    /**
     * A client-to-server keyed packet. Named {@code keyedServerbound} rather than {@code serverbound} because
     * the inherited {@link PacketType#serverbound} would otherwise clash on erasure (its {@code T} is unbounded,
     * ours is {@code T extends Keyed}), and Java forbids two same-erasure statics that neither hides the other.
     */
    public static <T extends Keyed> KeyedPacket<T> keyedServerbound(String namespace, String path, Class<T> payloadClass) {
        return new KeyedPacket<>(namespace, path, payloadClass, PacketDirection.SERVERBOUND);
    }

    /** A server-to-client keyed packet. */
    public static <T extends Keyed> KeyedPacket<T> keyedClientbound(String namespace, String path, Class<T> payloadClass) {
        return new KeyedPacket<>(namespace, path, payloadClass, PacketDirection.CLIENTBOUND);
    }

    /** A keyed packet valid in both directions. */
    public static <T extends Keyed> KeyedPacket<T> keyedBidirectional(String namespace, String path, Class<T> payloadClass) {
        return new KeyedPacket<>(namespace, path, payloadClass, PacketDirection.BIDIRECTIONAL);
    }
}
