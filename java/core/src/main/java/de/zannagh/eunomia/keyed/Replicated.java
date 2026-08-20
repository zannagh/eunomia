package de.zannagh.eunomia.keyed;

/**
 * A {@link Keyed} value that additionally declares "store me on the server and push the whole set to everyone
 * who connects." A DTO implementing this marker opts its channel into a {@link ReplicatedKeyedStore}: the server
 * persists each entry keyed by {@link Keyed#keyPath()}, relays every update to the other connected clients, and
 * dumps the full store to each newcomer once its handshake completes.
 * <p>
 * This is the in-DTO signal the game-agnostic server (and the external C# relay, via the {@code replicated} flag
 * on its wire envelope) keys its behaviour off - the type itself says it is part of a bigger, shared storage.
 *
 * @since 0.1.0
 */
public interface Replicated extends Keyed {
}
