package de.zannagh.eunomia.networking.admin;

import de.zannagh.eunomia.clients.RelayAddresses;
import de.zannagh.eunomia.configuration.EunomiaServerConfig;
import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.packets.ServerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * The server half of the administrative Cloud Sync settings channel, written once for every platform.
 *
 * <p><strong>This class is the security boundary.</strong> Two things are true of it and must stay true:</p>
 * <ol>
 *   <li>The permission question is asked <em>here</em>, on the server, against the authenticated sender of
 *       the packet - once for every read and again, independently, for every write. A write is never
 *       admitted on the strength of an earlier read having succeeded, because nothing stops a client from
 *       skipping the read entirely and sending a write as its first packet.</li>
 *   <li>Nothing a client says about its own privileges is read. The serverbound payloads have no such
 *       field, and even a client that appends one to the JSON gets it dropped on decode - so a forged
 *       admin flag is not "checked and rejected", it is structurally unable to reach a decision.</li>
 * </ol>
 *
 * <p>The submitted address is validated here too, with {@link RelayAddresses}. A settings screen doing the
 * same check is a courtesy to the person typing, not a control: this handler is reachable from any client,
 * modified or not.</p>
 *
 * <p>Every accepted and every refused write is logged with the acting player. An operator redirecting where
 * all their players' data is sent is worth an audit line, and so is someone trying to.</p>
 *
 * @since 0.3.0
 */
public final class ServerSettingsExchange {

    private static final Logger LOGGER = LoggerFactory.getLogger("eunomia-admin");

    private static final String DENIED_DETAIL =
            "You are not a eunomia administrator on this server.";

    private static final String INVALID_ADDRESS_DETAIL =
            "Not a usable relay address - expected an http:// or https:// URL, or a bare host.";

    private final ServerSettingsAccess access;
    private final ServerSettingsAuthority authority;

    /**
     * @param access    where this server keeps its {@link EunomiaServerConfig}.
     * @param authority the server's own permission check; consulted per packet, never cached per player.
     */
    public ServerSettingsExchange(ServerSettingsAccess access, ServerSettingsAuthority authority) {
        this.access = Objects.requireNonNull(access, "access");
        this.authority = Objects.requireNonNull(authority, "authority");
    }

    /**
     * Registers both serverbound handlers and declares the clientbound answer channel, so the platform
     * builds a codec (loaders) or a plugin channel (Paper) for it. Call once during server startup, before
     * the platform enumerates the registered channels.
     */
    public void register() {
        CommunicationManager.register(AdminPackets.SERVER_SETTINGS);
        CommunicationManager.onServerReceive(AdminPackets.SERVER_SETTINGS_REQUEST, this::handleRequest);
        CommunicationManager.onServerReceive(AdminPackets.SERVER_SETTINGS_WRITE, this::handleWrite);
    }

    /**
     * Answers a read. A non-administrator is not refused: these three values are already advertised
     * unsolicited to every joining client in the capability handshake, so the only thing a refusal would
     * buy is a worse screen. They get the same snapshot with {@code editable = false}.
     */
    private void handleRequest(ServerSettingsRequestPayload request, ServerContext context) {
        boolean editable = isAdministrator(context);
        context.reply(AdminPackets.SERVER_SETTINGS, ServerSettingsPayload.of(
                request.correlationId, access.current(), editable, ServerSettingsStatus.OK, null));
    }

    /** Applies a write, after re-deciding the permission question and validating the address. */
    private void handleWrite(ServerSettingsWritePayload write, ServerContext context) {
        if (!isAdministrator(context)) {
            LOGGER.warn("Refused a eunomia Cloud Sync settings write from {} ({}): not an administrator "
                            + "(requested fallback={}, address='{}', prefer={})",
                    context.senderName(), context.senderId(), write.enableExternalFallback,
                    write.externalServerAddress, write.preferExternalTransport);
            refuse(write.correlationId, context, false, ServerSettingsStatus.DENIED, DENIED_DETAIL);
            return;
        }
        String stored;
        if (RelayAddresses.isClearing(write.externalServerAddress)) {
            stored = "";
        } else if (RelayAddresses.isValid(write.externalServerAddress)) {
            stored = RelayAddresses.normalize(write.externalServerAddress);
        } else {
            LOGGER.warn("Refused a eunomia Cloud Sync settings write from administrator {} ({}): "
                            + "'{}' is not a usable relay address",
                    context.senderName(), context.senderId(), write.externalServerAddress);
            refuse(write.correlationId, context, true, ServerSettingsStatus.INVALID_ADDRESS,
                    INVALID_ADDRESS_DETAIL);
            return;
        }
        apply(write, context, stored);
    }

    /** Persists the validated write and answers with the configuration as it now stands. */
    private void apply(ServerSettingsWritePayload write, ServerContext context, String stored) {
        access.persist(new EunomiaServerConfig(
                write.enableExternalFallback, stored, write.preferExternalTransport));
        LOGGER.info("Accepted a eunomia Cloud Sync settings write from administrator {} ({}): "
                        + "fallback={}, address='{}', prefer={}",
                context.senderName(), context.senderId(), write.enableExternalFallback, stored,
                write.preferExternalTransport);
        context.reply(AdminPackets.SERVER_SETTINGS, ServerSettingsPayload.of(
                write.correlationId, access.current(), true, ServerSettingsStatus.APPLIED, null));
    }

    /** Answers a refused write with the <em>unchanged</em> current values, so the screen can snap back. */
    private void refuse(
            long correlationId,
            ServerContext context,
            boolean editable,
            ServerSettingsStatus status,
            String detail) {
        context.reply(AdminPackets.SERVER_SETTINGS, ServerSettingsPayload.of(
                correlationId, access.current(), editable, status, detail));
    }

    /** Fails closed: a permission backend that throws means "not an administrator", never "probably fine". */
    private boolean isAdministrator(ServerContext context) {
        try {
            return authority.isAdministrator(context);
        } catch (Exception e) {
            LOGGER.error("eunomia permission check threw for {} ({}); treating as non-administrator",
                    context.senderName(), context.senderId(), e);
            return false;
        }
    }
}
