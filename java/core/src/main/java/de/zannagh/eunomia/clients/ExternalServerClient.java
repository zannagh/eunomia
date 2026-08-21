package de.zannagh.eunomia.clients;

import com.google.gson.Gson;
import de.zannagh.eunomia.keyed.Keyed;
import de.zannagh.eunomia.keyed.Replicated;
import de.zannagh.eunomia.keyed.StoreSyncPackets;
import de.zannagh.eunomia.keyed.StoreSyncPayload;
import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.packets.ClientContext;
import de.zannagh.eunomia.networking.packets.KeyedPacket;
import de.zannagh.eunomia.networking.packets.PacketType;
import de.zannagh.eunomia.networking.serialization.NetworkSerializer;
import org.slf4j.Logger;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * The client's connection to the external relay (the C# server), used as a drop-in for the Minecraft transport
 * when the joined MC server does not run Eunomia. Sends go out over REST ({@code PUT /api/packets/...}) and inbound
 * pushes arrive over a WebSocket ({@code /ws?id=<uuid>&scope=<scope>}), which the relay uses to dump the stored
 * replicated snapshot on connect and to relay live updates.
 * <p>
 * The WebSocket is self-healing: a drop schedules a capped exponential-backoff reconnect, and the relay re-pushes
 * the snapshot on each new connection, so no state is lost. All traffic is tagged with {@link #scope} so the relay
 * keeps this Minecraft server's data isolated from every other.
 * <p>
 * A <em>block</em> is terminal: the relay signals it with an HTTP 403 on a REST send or a WebSocket close code
 * 1008 (policy violation). Either one stops the client for good (no more sends, no reconnect) and fires
 * {@link #onBlocked} so the caller can fall back to the Minecraft transport for the rest of the session.
 */
public final class ExternalServerClient {

    private static final long BASE_BACKOFF_MS = 1000;
    private static final long MAX_BACKOFF_MS = 30_000;

    /** HTTP status the relay returns on a REST send to a blocked scope. */
    static final int BLOCKED_STATUS = 403;

    /** WebSocket close code (RFC 6455 "policy violation") the relay uses to close a blocked scope's socket. */
    static final int POLICY_VIOLATION_CLOSE = 1008;

    private final String base;
    private final String scope;
    private final String name;
    private final UUID playerId;
    private final Logger logger;
    private final Runnable onBlocked;
    private final HttpClient httpClient;

    private volatile boolean running;
    private volatile boolean blocked;
    private volatile WebSocket webSocket;
    private volatile int attempt;

    public ExternalServerClient(String address, String scope, String name, UUID playerId, Logger logger) {
        this(address, scope, name, playerId, logger, null);
    }

    /**
     * @param onBlocked run once when the relay reports this server as blocked (HTTP 403 / WS 1008), after the
     *                  client has stopped itself; used to hand control back to the Minecraft transport. May be
     *                  {@code null}.
     */
    public ExternalServerClient(String address, String scope, String name, UUID playerId, Logger logger, Runnable onBlocked) {
        this.base = RelayEndpoints.base(address);
        this.scope = scope;
        this.name = name;
        this.playerId = playerId;
        this.logger = logger;
        this.onBlocked = onBlocked;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /** Opens the receive WebSocket (with reconnect). Idempotent-ish; call once per activation. */
    public void start() {
        running = true;
        attempt = 0;
        connect();
    }

    /** Closes the WebSocket and stops reconnecting. Safe to call more than once. */
    public void stop() {
        running = false;
        WebSocket current = webSocket;
        webSocket = null;
        if (current != null) {
            try {
                current.sendClose(WebSocket.NORMAL_CLOSURE, "client stop");
            } catch (Exception ignored) {
                // best effort
            }
        }
    }

    /** Sends a packet to the relay over REST, wrapped in a {@link PacketEnvelope}. Never throws. */
    public <T> void send(PacketType<T> type, T data) {
        boolean keyed = type instanceof KeyedPacket<?>;
        String key = data instanceof Keyed keyedData ? keyedData.keyPath().toString() : null;
        boolean replicated = data instanceof Replicated;
        Gson gson = gson();
        PacketEnvelope envelope = new PacketEnvelope(
                scope, name, type.channelKey(), key, replicated, playerId.toString(), gson.toJsonTree(data));
        String path = keyed ? "/api/packets/keyed" : "/api/packets/plain";
        HttpRequest request = HttpRequest.newBuilder(RelayEndpoints.http(base, path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(gson.toJson(envelope)))
                .build();
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) -> {
            if (error != null) {
                logger.warn("Failed to send {} to relay {}", type.channelKey(), base, error);
            } else {
                handleSendStatus(type.channelKey(), response.statusCode(), response.body());
            }
        });
    }

    /**
     * Reacts to a REST send's status. A 403 is a hard block (stop + fall back); any other 3xx/4xx/5xx (the benign
     * 409 "no live socket" among them) is logged and left to the next send's retry. Package-private for tests.
     */
    void handleSendStatus(String channelKey, int statusCode, String body) {
        if (statusCode == BLOCKED_STATUS) {
            handleBlocked("HTTP " + BLOCKED_STATUS + " on REST send");
        } else if (statusCode >= 300) {
            logger.warn("Relay rejected {} ({}): {}", channelKey, statusCode, body);
        }
    }

    private void connect() {
        if (!running) {
            return;
        }
        String encodedScope = URLEncoder.encode(scope, StandardCharsets.UTF_8);
        String encodedName = URLEncoder.encode(name == null ? "" : name, StandardCharsets.UTF_8);
        URI uri = RelayEndpoints.ws(base, "/ws?id=" + playerId + "&scope=" + encodedScope + "&name=" + encodedName);
        httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(uri, new RelayListener())
                .whenComplete((socket, error) -> {
                    if (error != null) {
                        logger.debug("Relay WebSocket connect to {} failed: {}", base, error.toString());
                        scheduleReconnect();
                    } else {
                        webSocket = socket;
                        attempt = 0;
                        logger.info("Connected to external relay {} (scope {})", base, scope);
                    }
                });
    }

    /**
     * Reacts to a WebSocket close. Close code 1008 (policy violation) is a hard block (stop + fall back); every
     * other code (normal, abnormal, server restart, ...) schedules a reconnect as before. Package-private for tests.
     */
    void handleClose(int statusCode, String reason) {
        if (statusCode == POLICY_VIOLATION_CLOSE) {
            handleBlocked("WS " + POLICY_VIOLATION_CLOSE + " (policy violation): " + reason);
        } else {
            scheduleReconnect();
        }
    }

    /**
     * Terminal blocked handling, idempotent: stops the client (no more sends, no reconnect) and hands control back
     * to the caller via {@link #onBlocked} so it can restore the Minecraft transport for the session.
     */
    private void handleBlocked(String reason) {
        if (blocked) {
            return;
        }
        blocked = true;
        logger.info("Relay blocked this server ({}); syncing disabled, falling back to the Minecraft transport", reason);
        stop();
        Runnable callback = onBlocked;
        if (callback != null) {
            callback.run();
        }
    }

    /** Whether the receive loop is active (false once stopped or blocked). Package-private for tests. */
    boolean isRunning() {
        return running;
    }

    /** Whether the relay reported this server as blocked. Package-private for tests. */
    boolean isBlocked() {
        return blocked;
    }

    private void scheduleReconnect() {
        if (!running) {
            return;
        }
        long backoff = Math.min(MAX_BACKOFF_MS, BASE_BACKOFF_MS * (1L << Math.min(attempt, 5)));
        long jitter = (long) (backoff * 0.2 * Math.random());
        attempt++;
        CompletableFuture.delayedExecutor(backoff + jitter, TimeUnit.MILLISECONDS).execute(this::connect);
    }

    private void handleFrame(String json) {
        Gson gson = gson();
        WsFrame frame = gson.fromJson(json, WsFrame.class);
        if (frame == null || frame.type == null || frame.data == null) {
            return;
        }
        ClientContext context = new RelayClientContext();
        if ("store_sync".equals(frame.type)) {
            StoreSyncPayload sync = gson.fromJson(frame.data, StoreSyncPayload.class);
            CommunicationManager.dispatchClientbound(StoreSyncPackets.STORE_SYNC.channelKey(), sync, context);
            return;
        }
        if ("envelope".equals(frame.type)) {
            PacketEnvelope envelope = gson.fromJson(frame.data, PacketEnvelope.class);
            PacketType<?> type = CommunicationManager.type(envelope.channel);
            if (type != null && envelope.payload != null) {
                Object payload = gson.fromJson(envelope.payload, type.payloadClass());
                CommunicationManager.dispatchClientbound(envelope.channel, payload, context);
            }
        }
    }

    private Gson gson() {
        return NetworkSerializer.gson();
    }

    /** A reply from a relay-received packet goes back to the server through the installed (relay) transport. */
    private static final class RelayClientContext implements ClientContext {
        @Override
        public <T> void reply(PacketType<T> type, T data) {
            CommunicationManager.sendToServer(type, data);
        }
    }

    private final class RelayListener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String message = buffer.toString();
                buffer.setLength(0);
                try {
                    handleFrame(message);
                } catch (Exception e) {
                    logger.warn("Failed to handle relay frame", e);
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            logger.debug("Relay WebSocket closed ({}): {}", statusCode, reason);
            handleClose(statusCode, reason);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            logger.debug("Relay WebSocket error: {}", error.toString());
            scheduleReconnect();
        }
    }
}
