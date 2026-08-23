package de.zannagh.eunomia.client.settings;

import de.zannagh.eunomia.networking.admin.AdminPackets;
import de.zannagh.eunomia.networking.admin.ServerSettingsPayload;
import de.zannagh.eunomia.networking.admin.ServerSettingsRequestPayload;
import de.zannagh.eunomia.networking.admin.ServerSettingsWritePayload;
import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.comms.SendOptions;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * The client-side entry point for reading and writing the joined server's Cloud Sync settings. This is the
 * whole surface a settings screen needs; it names no {@code Screen} and no GUI type at all, so the UI slice
 * layered on top can be replaced without touching any networking.
 *
 * <pre>{@code
 * ServerSettingsClient.request(view -> {
 *     boolean readOnly = !view.editable();          // the server's verdict, not ours
 *     render(view.enableExternalFallback(), view.externalServerAddress(), readOnly);
 * });
 *
 * ServerSettingsClient.submit(true, "https://relay.example", false, result -> {
 *     if (result.applied()) {
 *         toast("Saved");
 *     } else {
 *         toast(result.detail());                   // DENIED, INVALID_ADDRESS or UNAVAILABLE
 *     }
 * });
 * }</pre>
 *
 * <p><strong>{@code editable()} is a rendering hint.</strong> The server decides, per packet, whether a
 * write is allowed, and refuses one from a non-administrator no matter what this client believed. Rendering
 * an editable screen for someone who is not an admin is a cosmetic bug, never a privilege escalation.</p>
 *
 * <p><strong>Callbacks run on the network thread</strong> (or on the timeout scheduler's thread), fire
 * exactly once per exchange, and must therefore hop to the render thread themselves before touching
 * anything Minecraft owns.</p>
 *
 * <p><strong>Send policy.</strong> Both serverbound packets go out with {@link SendOptions#ALWAYS} rather
 * than the default {@code AFTER_SUCCESSFUL_HANDSHAKE}. Gating on the capability handshake would be the more
 * conservative choice in principle, but a settings screen is opened deliberately, by one player, on a server
 * they administer - and the packet is well under the serverbound size ceiling that makes an unknown payload
 * dangerous on a vanilla server. The {@link #RESPONSE_TIMEOUT_SECONDS} timeout below is what covers the
 * "server does not answer" case that gating would otherwise have covered, and it covers it better: the
 * caller gets an {@code UNAVAILABLE} view instead of a callback that never fires.</p>
 *
 * @since 0.3.0
 */
public final class ServerSettingsClient {

    /** How long to wait for the server's answer before concluding the exchange is unanswerable. */
    public static final long RESPONSE_TIMEOUT_SECONDS = 5;

    private static final AtomicLong NEXT_CORRELATION_ID = new AtomicLong(1);

    private static final Map<Long, Consumer<ServerSettingsView>> PENDING = new ConcurrentHashMap<>();

    private static volatile boolean registered;

    private static volatile ServerSettingsView lastKnown;

    private ServerSettingsClient() {
    }

    /**
     * Registers the clientbound answer handler. Idempotent, and called automatically by {@link #request} and
     * {@link #submit}, so a caller never has to - it is public only so a mod that wants the channel declared
     * during its own init can say so explicitly.
     */
    public static synchronized void init() {
        if (registered) {
            return;
        }
        registered = true;
        CommunicationManager.onClientReceive(AdminPackets.SERVER_SETTINGS,
                (payload, context) -> complete(payload));
    }

    /**
     * Asks the server for its current settings and for whether this player may change them.
     *
     * @param onResult invoked exactly once with the answer, or with an
     *                 {@link de.zannagh.eunomia.networking.admin.ServerSettingsStatus#UNAVAILABLE} view if
     *                 none arrives within {@link #RESPONSE_TIMEOUT_SECONDS}.
     */
    public static void request(Consumer<ServerSettingsView> onResult) {
        long id = begin(onResult);
        CommunicationManager.sendToServer(AdminPackets.SERVER_SETTINGS_REQUEST,
                new ServerSettingsRequestPayload(id), SendOptions.ALWAYS);
    }

    /**
     * Asks the server to store new settings. Whether the write lands is entirely the server's decision; this
     * method makes no local permission judgement and deliberately does not consult {@link #lastKnown()}.
     *
     * @param enableExternalFallback  the requested external-fallback switch.
     * @param externalServerAddress   the requested relay address; blank clears it. Validated server-side.
     * @param preferExternalTransport the requested "prefer the relay" switch.
     * @param onResult                invoked exactly once with the outcome: {@code applied()} on success,
     *                                otherwise a refusal carrying a {@code detail()} worth showing.
     */
    public static void submit(
            boolean enableExternalFallback,
            String externalServerAddress,
            boolean preferExternalTransport,
            Consumer<ServerSettingsView> onResult) {
        long id = begin(onResult);
        CommunicationManager.sendToServer(AdminPackets.SERVER_SETTINGS_WRITE,
                new ServerSettingsWritePayload(id, enableExternalFallback, externalServerAddress,
                        preferExternalTransport),
                SendOptions.ALWAYS);
    }

    /**
     * The most recent answer this client received, or {@code null} if none has arrived on this connection.
     * A convenience for re-opening a screen without a round trip; it is a cache, not a source of truth.
     */
    public static ServerSettingsView lastKnown() {
        return lastKnown;
    }

    /**
     * Drops the cached view and fails every still-pending callback as unavailable. Call when the play
     * connection ends, so nothing from the previous server leaks into the next one.
     */
    public static void reset() {
        lastKnown = null;
        PENDING.keySet().forEach(ServerSettingsClient::timeOut);
    }

    /** Allocates a correlation id, parks the callback under it and arms the timeout. */
    private static long begin(Consumer<ServerSettingsView> onResult) {
        init();
        long id = NEXT_CORRELATION_ID.getAndIncrement();
        if (onResult != null) {
            PENDING.put(id, onResult);
            CompletableFuture.delayedExecutor(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .execute(() -> timeOut(id));
        }
        return id;
    }

    /** Resolves the callback waiting on this answer, if it has not already timed out. */
    private static void complete(ServerSettingsPayload payload) {
        ServerSettingsView view = ServerSettingsView.of(payload);
        lastKnown = view;
        Consumer<ServerSettingsView> callback = PENDING.remove(payload.correlationId);
        if (callback != null) {
            callback.accept(view);
        }
    }

    /** Fires a still-pending callback as unavailable. A no-op once the answer has arrived. */
    private static void timeOut(long correlationId) {
        Consumer<ServerSettingsView> callback = PENDING.remove(correlationId);
        if (callback != null) {
            callback.accept(ServerSettingsView.unavailable());
        }
    }
}
