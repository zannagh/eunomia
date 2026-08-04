package de.zannagh.eunomia.networking;

import de.zannagh.eunomia.networking.handshake.ClientHelloPayload;
import de.zannagh.eunomia.networking.handshake.HandshakePackets;
import de.zannagh.eunomia.networking.handshake.ServerCapabilities;
import de.zannagh.eunomia.networking.handshake.ServerHelloPayload;
import de.zannagh.eunomia.networking.serialization.PayloadCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * The single hub for defining, sending, receiving and routing packets - on both the client and the
 * server, and on any platform. A mod only ever touches this class to add networking:
 *
 * <pre>{@code
 * var HELLO = PacketType.serverbound("mymod", "hello", HelloPayload.class);
 * CommunicationManager.onServerReceive(HELLO, (payload, ctx) ->
 *         ctx.reply(WELCOME, new WelcomePayload("hi " + ctx.senderName())));
 * // elsewhere, on the client:
 * CommunicationManager.sendToServer(HELLO, new HelloPayload());
 * }</pre>
 *
 * <p>Everything platform-specific is injected once at startup: a {@link #setRegistrationListener
 * registration listener} that builds the native codec/channel for each declared packet, and a
 * {@link #setServerTransport server}/{@link #setClientTransport client} transport that puts bytes on
 * the wire. The manager holds the routing tables and hands decoded payloads to handlers, which is
 * exactly the seam a future non-game (HTTP) relay plugs into: it registers the same packet types,
 * feeds inbound bytes to {@link #dispatchServerboundRaw} and installs its own transport.
 */
public final class CommunicationManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("eunomia-net");

    private static final ConcurrentHashMap<String, PacketType<?>> TYPES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ServerPacketHandler<?>> SERVER_HANDLERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ClientPacketHandler<?>> CLIENT_HANDLERS = new ConcurrentHashMap<>();

    private static volatile Consumer<PacketType<?>> registrationListener;
    private static volatile ServerTransport serverTransport;
    private static volatile ClientTransport clientTransport;

    private static final ServerCapabilities SERVER_CAPABILITIES = new ServerCapabilities();

    private CommunicationManager() {
    }

    // ── Platform wiring (called once at startup by each loader/plugin) ──────────────────────────

    /**
     * Installs the hook that turns a declared {@link PacketType} into a platform channel - the
     * loader builds a {@code StreamCodec}, Paper registers a Bukkit channel. Types declared before
     * the listener is installed are replayed into it so registration order does not matter.
     */
    public static void setRegistrationListener(Consumer<PacketType<?>> listener) {
        registrationListener = listener;
        if (listener != null) {
            for (PacketType<?> type : TYPES.values()) {
                listener.accept(type);
            }
        }
    }

    public static void setServerTransport(ServerTransport transport) {
        serverTransport = transport;
    }

    public static void setClientTransport(ClientTransport transport) {
        clientTransport = transport;
    }

    // ── Registration (mod-facing) ───────────────────────────────────────────────────────────────

    /** Declares a packet so the platform builds its channel/codec. Idempotent. */
    public static <T> PacketType<T> register(PacketType<T> type) {
        PacketType<?> previous = TYPES.putIfAbsent(type.channelKey(), type);
        if (previous == null) {
            Consumer<PacketType<?>> listener = registrationListener;
            if (listener != null) {
                listener.accept(type);
            }
            LOGGER.debug("Registered packet {}", type);
        }
        return type;
    }

    /** Registers {@code type} (if needed) and a server-side handler for it. */
    public static <T> PacketType<T> onServerReceive(PacketType<T> type, ServerPacketHandler<T> handler) {
        register(type);
        SERVER_HANDLERS.put(type.channelKey(), handler);
        return type;
    }

    /** Registers {@code type} (if needed) and a client-side handler for it. */
    public static <T> PacketType<T> onClientReceive(PacketType<T> type, ClientPacketHandler<T> handler) {
        register(type);
        CLIENT_HANDLERS.put(type.channelKey(), handler);
        return type;
    }

    // ── Lookups (platform mixins / listeners) ───────────────────────────────────────────────────

    public static PacketType<?> type(String channelKey) {
        return TYPES.get(channelKey);
    }

    /** Every declared packet that may travel client-to-server (for serverbound codec injection). */
    public static Collection<PacketType<?>> serverboundTypes() {
        return filterByDirection(true);
    }

    /** Every declared packet that may travel server-to-client (for clientbound codec injection). */
    public static Collection<PacketType<?>> clientboundTypes() {
        return filterByDirection(false);
    }

    private static Collection<PacketType<?>> filterByDirection(boolean serverbound) {
        List<PacketType<?>> out = new ArrayList<>();
        for (PacketType<?> type : TYPES.values()) {
            if (serverbound ? type.direction().allowsServerbound() : type.direction().allowsClientbound()) {
                out.add(type);
            }
        }
        return out;
    }

    // ── Sending (mod-facing) ────────────────────────────────────────────────────────────────────

    /** Client → server. No-ops with a debug log if no client transport is installed (dedicated server). */
    public static <T> void sendToServer(PacketType<T> type, T data) {
        requireDirection(type, true);
        ClientTransport transport = clientTransport;
        if (transport == null) {
            LOGGER.debug("No client transport installed; dropping serverbound {}", type.channelKey());
            return;
        }
        transport.sendToServer(type, data);
    }

    /** Server → a single player. */
    public static <T> void sendToPlayer(UUID playerId, PacketType<T> type, T data) {
        requireDirection(type, false);
        ServerTransport transport = requireServerTransport();
        transport.sendToPlayer(playerId, type, data);
    }

    /** Server → all players. */
    public static <T> void broadcast(PacketType<T> type, T data) {
        requireDirection(type, false);
        requireServerTransport().broadcast(type, data);
    }

    /** Server → all players except one (e.g. re-broadcasting a C2S event to everyone but its sender). */
    public static <T> void broadcastExcept(UUID excludedPlayerId, PacketType<T> type, T data) {
        requireDirection(type, false);
        requireServerTransport().broadcastExcept(excludedPlayerId, type, data);
    }

    // ── Dispatch (platform inbound) ─────────────────────────────────────────────────────────────

    /**
     * Routes an already-decoded serverbound payload to its handler. This is the loader path: the
     * native StreamCodec has already turned bytes into the POJO.
     *
     * @return true if a handler consumed it (so the caller cancels vanilla handling)
     */
    public static boolean dispatchServerbound(String channelKey, Object payload, ServerContext context) {
        ServerPacketHandler<?> handler = SERVER_HANDLERS.get(channelKey);
        if (handler == null) {
            return false;
        }
        invokeServer(channelKey, handler, payload, context);
        return true;
    }

    /** Routes an already-decoded clientbound payload to its handler. */
    public static boolean dispatchClientbound(String channelKey, Object payload, ClientContext context) {
        ClientPacketHandler<?> handler = CLIENT_HANDLERS.get(channelKey);
        if (handler == null) {
            return false;
        }
        invokeClient(channelKey, handler, payload, context);
        return true;
    }

    /**
     * Decodes raw {@code gzip(json)} bytes for {@code channelKey} and routes them. This is the Paper /
     * HTTP path, where no native codec ran, so the shared {@link PayloadCodec} does the resolution.
     */
    public static boolean dispatchServerboundRaw(String channelKey, byte[] raw, ServerContext context) {
        PacketType<?> type = TYPES.get(channelKey);
        if (type == null || !SERVER_HANDLERS.containsKey(channelKey)) {
            return false;
        }
        return dispatchServerbound(channelKey, PayloadCodec.decode(raw, type.payloadClass()), context);
    }

    /** Decodes raw {@code gzip(json)} bytes for {@code channelKey} and routes them to the client handler. */
    public static boolean dispatchClientboundRaw(String channelKey, byte[] raw, ClientContext context) {
        PacketType<?> type = TYPES.get(channelKey);
        if (type == null || !CLIENT_HANDLERS.containsKey(channelKey)) {
            return false;
        }
        return dispatchClientbound(channelKey, PayloadCodec.decode(raw, type.payloadClass()), context);
    }

    @SuppressWarnings("unchecked")
    private static void invokeServer(String key, ServerPacketHandler<?> handler, Object payload, ServerContext ctx) {
        try {
            ((ServerPacketHandler<Object>) handler).handle(payload, ctx);
        } catch (Exception e) {
            LOGGER.error("Error handling serverbound packet {}", key, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void invokeClient(String key, ClientPacketHandler<?> handler, Object payload, ClientContext ctx) {
        try {
            ((ClientPacketHandler<Object>) handler).handle(payload, ctx);
        } catch (Exception e) {
            LOGGER.error("Error handling clientbound packet {}", key, e);
        }
    }

    // ── Server capability handshake ─────────────────────────────────────────────────────────────

    /**
     * Client-side view of whether the joined server runs Eunomia and which packets it receives. Query
     * this (or {@link ServerCapabilities#onResolved}) to decide, per connection, whether to talk to
     * the MC server or fall back to a custom communications server.
     */
    public static ServerCapabilities serverCapabilities() {
        return SERVER_CAPABILITIES;
    }

    /** The channel keys the server currently has a serverbound handler for (its true receiver set). */
    public static Collection<String> serverHandlerChannels() {
        return List.copyOf(SERVER_HANDLERS.keySet());
    }

    /**
     * Server-side: answer capability probes. Registers a HELLO handler that replies with the list of
     * channels this server actually receives. Call once during server startup.
     */
    public static void enableServerHandshake() {
        register(HandshakePackets.HELLO);
        register(HandshakePackets.HELLO_ACK);
        onServerReceive(HandshakePackets.HELLO, (hello, context) ->
                context.reply(HandshakePackets.HELLO_ACK,
                        new ServerHelloPayload(HandshakePackets.PROTOCOL_VERSION,
                                new ArrayList<>(serverHandlerChannels()))));
    }

    /**
     * Client-side: consume capability probes. Registers the HELLO_ACK handler that records the
     * server's capabilities. Call once during client startup, then {@link #beginServerProbe()} on join.
     */
    public static void enableClientHandshake() {
        register(HandshakePackets.HELLO);
        register(HandshakePackets.HELLO_ACK);
        onClientReceive(HandshakePackets.HELLO_ACK, (ack, context) ->
                SERVER_CAPABILITIES.markPresent(ack.protocolVersion, ack.receiverChannels));
    }

    /** Client-side: reset capability state and send the HELLO probe. Call on each join. */
    public static void beginServerProbe() {
        SERVER_CAPABILITIES.reset();
        sendToServer(HandshakePackets.HELLO, new ClientHelloPayload(HandshakePackets.PROTOCOL_VERSION));
    }

    /** Client-side: conclude the probe as "no Eunomia server" if no ACK arrived. Call after a timeout. */
    public static void markServerProbeTimedOut() {
        SERVER_CAPABILITIES.markAbsentIfUnresolved();
    }

    private static void requireDirection(PacketType<?> type, boolean serverbound) {
        boolean ok = serverbound ? type.direction().allowsServerbound() : type.direction().allowsClientbound();
        if (!ok) {
            throw new IllegalArgumentException(type + " cannot be sent "
                    + (serverbound ? "to the server" : "to a client"));
        }
    }

    private static ServerTransport requireServerTransport() {
        ServerTransport transport = serverTransport;
        if (transport == null) {
            throw new IllegalStateException("No server transport installed - cannot send a clientbound packet");
        }
        return transport;
    }

    /** Clears all registrations and wiring. Intended for tests only. */
    public static void resetForTesting() {
        TYPES.clear();
        SERVER_HANDLERS.clear();
        CLIENT_HANDLERS.clear();
        registrationListener = null;
        serverTransport = null;
        clientTransport = null;
        SERVER_CAPABILITIES.reset();
    }
}
