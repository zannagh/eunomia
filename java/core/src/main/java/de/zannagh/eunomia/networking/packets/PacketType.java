package de.zannagh.eunomia.networking.packets;

import java.util.Objects;

/**
 * A game-version-agnostic packet definition. This is the single thing a mod declares to add a
 * packet: a channel identity ({@code namespace:path}), the plain-Java payload class, and the
 * direction it may travel.
 * <p>
 * There is deliberately no {@code CustomPacketPayload}, {@code StreamCodec} or {@code Identifier}
 * here - those exist only from Minecraft 1.20.5 onward and only inside the game process. The loader
 * adapter derives the version-specific codec from this definition; the Bukkit plugin derives a
 * channel name from it; a future HTTP relay can key routes on {@link #channelKey()}.
 *
 * @param <T> the payload type carried on this channel, serialized as JSON by {@code PayloadCodec}
 */
public class PacketType<T> {

    private final String namespace;
    private final String path;
    private final Class<T> payloadClass;
    private final PacketDirection direction;

    protected PacketType(String namespace, String path, Class<T> payloadClass, PacketDirection direction) {
        this.namespace = requireChannelPart(namespace, "namespace");
        this.path = requireChannelPart(path, "path");
        this.payloadClass = Objects.requireNonNull(payloadClass, "payloadClass");
        this.direction = Objects.requireNonNull(direction, "direction");
    }

    /** A client-to-server packet. */
    public static <T> PacketType<T> serverbound(String namespace, String path, Class<T> payloadClass) {
        return new PacketType<>(namespace, path, payloadClass, PacketDirection.SERVERBOUND);
    }

    /** A server-to-client packet. */
    public static <T> PacketType<T> clientbound(String namespace, String path, Class<T> payloadClass) {
        return new PacketType<>(namespace, path, payloadClass, PacketDirection.CLIENTBOUND);
    }

    /** A packet valid in both directions (e.g. a symmetric sync message). */
    public static <T> PacketType<T> bidirectional(String namespace, String path, Class<T> payloadClass) {
        return new PacketType<>(namespace, path, payloadClass, PacketDirection.BIDIRECTIONAL);
    }

    public String namespace() {
        return namespace;
    }

    public String path() {
        return path;
    }

    public Class<T> payloadClass() {
        return payloadClass;
    }

    public PacketDirection direction() {
        return direction;
    }

    /**
     * The stable {@code namespace:path} routing key. This is what the manager, the loader codec
     * registry and the Bukkit channel name all agree on, so it is the wire identity of the packet.
     */
    public String channelKey() {
        return namespace + ":" + path;
    }

    private static String requireChannelPart(String value, String what) {
        Objects.requireNonNull(value, what);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Packet " + what + " must not be empty");
        }
        // Mirrors vanilla ResourceLocation/Identifier constraints so the same string is a legal MC
        // channel id and a legal Bukkit NamespacedKey - a packet defined here is registrable on
        // every platform without a second, stricter validation pass surprising the caller later.
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.';
            boolean pathSlash = "path".equals(what) && c == '/';
            if (!ok && !pathSlash) {
                throw new IllegalArgumentException(
                        "Illegal character '" + c + "' in packet " + what + " '" + value
                                + "' (allowed: [a-z0-9_.-]" + ("path".equals(what) ? " and '/'" : "") + ")");
            }
        }
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PacketType<?> other)) {
            return false;
        }
        return namespace.equals(other.namespace) && path.equals(other.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, path);
    }

    @Override
    public String toString() {
        return "PacketType[" + channelKey() + " " + direction + " -> " + payloadClass.getSimpleName() + "]";
    }
}
