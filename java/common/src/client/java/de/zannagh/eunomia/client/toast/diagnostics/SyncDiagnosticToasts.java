package de.zannagh.eunomia.client.toast.diagnostics;

import de.zannagh.eunomia.Eunomia;
import de.zannagh.eunomia.client.networking.LocalClientIdentity;
import de.zannagh.eunomia.client.toast.EunomiaToasts;
import de.zannagh.eunomia.client.toast.ToastId;
import de.zannagh.eunomia.configuration.EunomiaSyncSettings;
import de.zannagh.eunomia.diagnostics.ClientSyncState;
import de.zannagh.eunomia.diagnostics.SyncDiagnostics;
import de.zannagh.eunomia.networking.handshake.ServerCapabilities;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Eunomia's own three diagnostic toasts: "this server has no eunomia sync", "your cloud sync relay is not
 * answering", and "this server has just put your data on a relay you have not used before". All are
 * informational only - nothing here influences the transport decision or the send gate,
 * and every entry point is written so that it cannot: the toast is always raised <em>after</em> the caller has
 * finished deciding, and a failure to raise one is swallowed.
 *
 * <p>The judgement about whether a toast is warranted is not made here. This class samples the running client
 * into a {@link ClientSyncState} and hands it to {@link SyncDiagnostics}, which is Minecraft-free and unit
 * tested. What is left here is the sampling, the copy, and the hand-off to {@link EunomiaToasts} - which in
 * turn owns the client-thread marshalling, the headless no-op safety, and the global
 * {@link EunomiaToasts#setEnabled(boolean)} kill switch that a consuming mod uses to silence library toasts.
 * Because every path below goes through that one entry point, both toasts honour the switch by construction.
 *
 * <p>The three are mutually exclusive by construction, not by a rule anyone has to remember. The first two
 * both require that the joined Minecraft server did <em>not</em> answer the handshake, while the new-relay
 * notification can only fire when it did - a server has to have advertised something for it to have steered
 * anybody anywhere. So no connection can ever produce two of these cards.
 *
 * <p>No toast replaces another in place. They use distinct {@link ToastId}s but stack rather than
 * replace, which sidesteps the one behavioural asymmetry in the toast API: on 1.20.1 the vanilla toast-id enum
 * is closed, so all eunomia toasts share a single token there and a replace-in-place would collapse across
 * unrelated ids. Stacking behaves identically on every supported version.
 */
public final class SyncDiagnosticToasts {

    /** Translation key for the title of the "server does not speak eunomia" toast. */
    public static final String MISSING_SERVER_SYNC_TITLE = "eunomia.toast.sync.unavailable.title";
    /** Translation key for the description of the "server does not speak eunomia" toast. */
    public static final String MISSING_SERVER_SYNC_DESCRIPTION = "eunomia.toast.sync.unavailable.description";
    /** Translation key for the title of the "configured relay is unreachable" toast. */
    public static final String RELAY_UNREACHABLE_TITLE = "eunomia.toast.sync.relay_unreachable.title";
    /** Translation key for the description of the "configured relay is unreachable" toast; takes the address. */
    public static final String RELAY_UNREACHABLE_DESCRIPTION = "eunomia.toast.sync.relay_unreachable.description";
    /** Translation key for the title of the "this server put you on a new relay" toast. */
    public static final String NEW_RELAY_HOST_TITLE = "eunomia.toast.sync.new_relay_host.title";
    /** Translation key for the description of the "new relay" toast; takes the relay host. */
    public static final String NEW_RELAY_HOST_DESCRIPTION = "eunomia.toast.sync.new_relay_host.description";

    private static final ToastId MISSING_SERVER_SYNC_ID = ToastId.of("eunomia:sync_unavailable");
    private static final ToastId RELAY_UNREACHABLE_ID = ToastId.of("eunomia:relay_unreachable");
    private static final ToastId NEW_RELAY_HOST_ID = ToastId.of("eunomia:new_relay_host");

    private SyncDiagnosticToasts() {
    }

    /**
     * Capability-resolution listener for the "server side sync is not available" toast. Register it once at
     * client init: the capability view keeps its listeners across reconnects and fires them again on every new
     * resolution, so one registration yields exactly one evaluation per join.
     *
     * <p>Runs on whichever thread resolved the probe - the netty read thread for an ACK, a delayed-executor
     * thread for a timeout. Only volatile capability fields and the (client-thread-written, plainly-read)
     * current server entry are touched, the same reads the transport selector already makes from those threads,
     * and the actual toast is marshalled onto the client thread by {@link EunomiaToasts}.
     *
     * @param capabilities the resolved capability view for this connection.
     */
    public static void onCapabilitiesResolved(ServerCapabilities capabilities) {
        try {
            ClientSyncState state = sample(capabilities.isResolved(), capabilities.isPresent());
            // Only on a resolved, real multiplayer connection: a singleplayer or LAN world has no third party
            // to have steered anybody anywhere, and an in-flight probe has not steered anybody yet either.
            if (state.capabilitiesResolved() && state.remoteServer()) {
                announceNewRelayHostIfAny();
            }
            if (!SyncDiagnostics.shouldWarnMissingServerSync(state)) {
                return;
            }
            EunomiaToasts.toast(Component.translatable(MISSING_SERVER_SYNC_TITLE))
                    .description(Component.translatable(MISSING_SERVER_SYNC_DESCRIPTION))
                    .id(MISSING_SERVER_SYNC_ID)
                    .show();
        } catch (Exception e) {
            // A diagnostic must never be able to break the thing it is diagnosing.
            Eunomia.LOGGER.debug("Failed to evaluate the missing-server-sync toast", e);
        }
    }

    /**
     * Tells the player, once, that the server they just joined has pointed their Cloud Sync at a relay host
     * they have never used before - naming the host, because "your data is being sent somewhere" is only
     * useful if it says where.
     *
     * <p>It notifies rather than blocks. The transport decision has already been taken by the time this runs
     * and nothing here can change it; a library that halted a join on a policy question would be answering a
     * question that is the player's to answer, in a dialog they did not ask for. What it does instead is make
     * the change visible at the moment it happens, with the settings screen one click away.</p>
     *
     * <p>This is also the mitigation for the 1.0.0 -&gt; 1.1.0 client-config migration, which turns a
     * recorded {@code false} into "inherit" and can therefore switch Cloud Sync on for an early adopter who
     * had it off. Such a player's very first join onto a relay-advertising server records a host they have
     * never used, so this fires and names it.</p>
     *
     * <p>The "have I said this already" bookkeeping lives in
     * {@link EunomiaSyncSettings#recordRelayHostAndReportIfNewlyServerChosen()}, which persists the host set
     * in the client config; this method only draws the result. Called from
     * {@link #onCapabilitiesResolved(ServerCapabilities)} rather than registered separately, so it inherits
     * that listener's exactly-once-per-join guarantee.</p>
     */
    private static void announceNewRelayHostIfAny() {
        String host = EunomiaSyncSettings.recordRelayHostAndReportIfNewlyServerChosen();
        if (host == null) {
            return;
        }
        EunomiaToasts.toast(Component.translatable(NEW_RELAY_HOST_TITLE))
                .description(Component.translatable(NEW_RELAY_HOST_DESCRIPTION, host))
                .id(NEW_RELAY_HOST_ID)
                .show();
    }

    /**
     * Raises the "cloud sync is configured but the relay is not answering" toast, if warranted.
     *
     * <p>Call this only from a path that has <em>already</em> concluded the transport decision (and therefore
     * the send gate). It deliberately returns nothing and swallows everything: it must never become a reason
     * for a caller to branch, retry or return early.
     *
     * @param address             the relay address that failed its health probe, shown to the player so they
     *                            can tell a typo from an outage.
     * @param serverSpeaksEunomia whether the joined Minecraft server answered the handshake. When it did, the
     *                            client simply demotes back to a working in-game transport and no toast is due.
     */
    public static void relayUnreachable(String address, boolean serverSpeaksEunomia) {
        try {
            ClientSyncState state = sample(true, serverSpeaksEunomia);
            if (!SyncDiagnostics.shouldWarnRelayUnreachable(state)) {
                return;
            }
            EunomiaToasts.toast(Component.translatable(RELAY_UNREACHABLE_TITLE))
                    .description(Component.translatable(RELAY_UNREACHABLE_DESCRIPTION, address))
                    .id(RELAY_UNREACHABLE_ID)
                    .show();
        } catch (Exception e) {
            Eunomia.LOGGER.debug("Failed to evaluate the relay-unreachable toast", e);
        }
    }

    /**
     * Freezes the client-side facts into the pure state object. A missing client (dedicated server, gametest,
     * pre-render startup) reads as "not a remote connection", which suppresses both toasts - the same outcome
     * the toast API itself would reach a moment later.
     */
    private static ClientSyncState sample(boolean resolved, boolean serverSpeaksEunomia) {
        Minecraft client = Minecraft.getInstance();
        boolean remote = client != null && LocalClientIdentity.currentServerScope(client) != null;
        return new ClientSyncState(resolved, serverSpeaksEunomia, remote, EunomiaSyncSettings.externalRelayUsable());
    }
}
