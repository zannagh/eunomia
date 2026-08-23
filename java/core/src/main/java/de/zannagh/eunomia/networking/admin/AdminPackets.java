package de.zannagh.eunomia.networking.admin;

import de.zannagh.eunomia.networking.packets.PacketType;

/**
 * The channel definitions for eunomia's administrative Cloud Sync settings exchange: an admin opens the
 * settings screen, asks the server what its server-side policy currently is, and (if the <em>server</em>
 * agrees they are an administrator) writes a new one.
 *
 * <p>Declared once here, in the Minecraft-free {@code :core}, for exactly the reason
 * {@code ExamplePackets} is: the loader half and the Paper plugin must route on the same
 * {@link PacketType#channelKey()} and resolve into the same POJOs, or an operator's experience would
 * silently differ by server flavour.</p>
 *
 * <p><strong>Security note.</strong> Nothing on these channels is trusted. The two serverbound payloads
 * carry no notion of who the sender is and no permission claim of any kind; the server derives the acting
 * player from the authenticated connection ({@code ServerContext#senderId()}) and re-runs its own
 * permission check on <em>every</em> request and <em>every</em> write. The {@code editable} flag on the
 * clientbound answer travels one way only - server to client - and exists purely so a screen can render
 * read-only controls instead of guessing. See {@link ServerSettingsExchange}.</p>
 *
 * @since 0.3.0
 */
public final class AdminPackets {

    private AdminPackets() {
    }

    /** Client asks the server for its current Cloud Sync policy. Answered with {@link #SERVER_SETTINGS}. */
    public static final PacketType<ServerSettingsRequestPayload> SERVER_SETTINGS_REQUEST =
            PacketType.serverbound("eunomia", "admin_server_settings_request", ServerSettingsRequestPayload.class);

    /**
     * Server states its current Cloud Sync policy, whether the receiving player may edit it, and how the
     * request that triggered this answer concluded. Sent only in reply to a request or a write - never
     * unsolicited, and never broadcast.
     */
    public static final PacketType<ServerSettingsPayload> SERVER_SETTINGS =
            PacketType.clientbound("eunomia", "admin_server_settings", ServerSettingsPayload.class);

    /** Client asks the server to store a new Cloud Sync policy. Answered with {@link #SERVER_SETTINGS}. */
    public static final PacketType<ServerSettingsWritePayload> SERVER_SETTINGS_WRITE =
            PacketType.serverbound("eunomia", "admin_server_settings_write", ServerSettingsWritePayload.class);
}
