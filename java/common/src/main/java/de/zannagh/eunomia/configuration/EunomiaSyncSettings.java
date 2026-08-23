package de.zannagh.eunomia.configuration;

import de.zannagh.eunomia.clients.RelayAddresses;
import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.handshake.ServerSyncPolicy;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Resolves the <em>effective</em> value of each external-transport setting on the client. This is the single
 * place any consumer may read these settings from; reading a config field directly is a bug, because a field is
 * only ever one of four possible sources and the four are ranked.
 *
 * <p>Precedence, highest first:</p>
 * <ol>
 *   <li>the player's explicit override in {@link EunomiaConfig} (a non-null field - a deliberate choice);</li>
 *   <li>the policy the joined server advertised in the capability handshake ({@link ServerSyncPolicy});</li>
 *   <li>the consuming mod's client-side default ({@link EunomiaSyncDefaults});</li>
 *   <li>the framework default ({@link EunomiaDefaults}).</li>
 * </ol>
 *
 * <p>Rungs one to three are all "no opinion by default", so a plain install lands on the framework defaults and
 * nothing leaves the machine. Both sources are injectable suppliers rather than hard references: it keeps this
 * class unit-testable without a running client, and it lets the loader bind the config provider only once it
 * actually exists.</p>
 *
 * @since 0.3.0
 */
public final class EunomiaSyncSettings {

    private static final Supplier<@Nullable EunomiaConfig> NO_CONFIG = () -> null;

    private static final Supplier<ServerSyncPolicy> CAPABILITY_POLICY =
            () -> CommunicationManager.serverCapabilities().syncPolicy();

    private static volatile Supplier<@Nullable EunomiaConfig> clientConfigSource = NO_CONFIG;

    private static volatile Supplier<ServerSyncPolicy> advertisedPolicySource = CAPABILITY_POLICY;

    private EunomiaSyncSettings() {
    }

    /**
     * Binds where the player's overrides come from. Called once by {@code Eunomia} after the client config
     * provider is built; until then the resolver simply behaves as if the player overrode nothing.
     */
    public static void bindClientConfigSource(@Nullable Supplier<@Nullable EunomiaConfig> source) {
        clientConfigSource = source == null ? NO_CONFIG : source;
    }

    /**
     * Binds where the server-advertised policy comes from. Defaults to the live capability view, so a consumer
     * normally never touches this; tests and alternative transports do.
     */
    public static void bindAdvertisedPolicySource(@Nullable Supplier<ServerSyncPolicy> source) {
        advertisedPolicySource = source == null ? CAPABILITY_POLICY : source;
    }

    /** Restores the default bindings. Tests only. */
    public static void resetForTesting() {
        clientConfigSource = NO_CONFIG;
        advertisedPolicySource = CAPABILITY_POLICY;
    }

    // ── Effective settings ──────────────────────────────────────────────────────────────────────

    /** Whether the external relay may be used at all for this connection. */
    public static boolean externalFallbackEnabled() {
        return resolve(
                EunomiaConfig::enableExternalFallbackOverride,
                ServerSyncPolicy::enableExternalFallback,
                EunomiaSyncDefaults.enableExternalFallback(),
                EunomiaDefaults.DEFAULT_ENABLE_EXTERNAL_FALLBACK);
    }

    /**
     * The relay address to use for this connection. Never blank; falls back to the framework address.
     *
     * <p>The server-advertised address is <em>validated here</em>, and this is the only place it can be
     * validated once and cover everybody. The advertised string arrives from a party the client does not
     * control, and until now it was handed verbatim to the reachability probe and to the relay client: a
     * server could point every joining player at anything that fit in a string. Doing the check in the
     * resolver rather than at the dial site means no consumer can forget it, and no future consumer can
     * reintroduce the hole by reading the policy directly.</p>
     *
     * <p>An unusable advertised address is treated as {@link ServerSyncPolicy#UNKNOWN} would be - "the server
     * said nothing" - so resolution falls through to the consuming mod's default and then the framework
     * default. Not a hard failure: a server with a typo in its config is a reason to ignore that one value,
     * not a reason to take a player's sync away.</p>
     */
    public static String externalServerAddress() {
        return resolve(
                EunomiaConfig::externalServerAddressOverride,
                EunomiaSyncSettings::validAdvertisedAddress,
                EunomiaSyncDefaults.externalServerAddress(),
                EunomiaDefaults.DEFAULT_EXTERNAL_SERVER_ADDRESS);
    }

    /**
     * The address the joined server advertised, if it is one that could actually be dialled, otherwise
     * {@code null}. Runs the very same {@link RelayAddresses} check the server applies before storing an
     * address and the settings screen applies while a player types, so a value that is refused in one place
     * is refused in all three.
     */
    static @Nullable String validAdvertisedAddress(ServerSyncPolicy policy) {
        String advertised = policy.externalServerAddress();
        return RelayAddresses.isValid(advertised) ? advertised : null;
    }

    /** Whether the relay should be preferred even when the joined Minecraft server speaks Eunomia. */
    public static boolean preferExternalTransport() {
        return resolve(
                EunomiaConfig::preferExternalTransportOverride,
                ServerSyncPolicy::preferExternalTransport,
                EunomiaSyncDefaults.preferExternalTransport(),
                EunomiaDefaults.DEFAULT_PREFER_EXTERNAL_TRANSPORT);
    }

    /** Whether a usable relay destination exists at all (enabled plus a non-blank address). */
    public static boolean externalRelayUsable() {
        return externalFallbackEnabled() && !externalServerAddress().isBlank();
    }

    /**
     * Records the relay this connection will actually use and reports its host back <em>only</em> the first
     * time a server has steered the player onto a host they have never used before. {@code null} every other
     * time: nothing is going anywhere new, or nothing is going anywhere at all.
     *
     * <p>This is the fact behind the "your data is now going to &lt;host&gt;" notification, kept here rather
     * than in the client code that draws it because the client source set has no tests. It is a query with a
     * side effect and deliberately so - the recording and the "was it new" answer have to be one atomic step
     * or a re-entrant caller notifies twice.</p>
     *
     * <p>Three conditions, all necessary:</p>
     * <ol>
     *   <li>the relay is effectively usable, so data really is about to leave the machine;</li>
     *   <li>the joined server is the reason - it advertised the enable flag as {@code true}, or advertised an
     *       address, or both. A host that a player configured themselves, or that their mod pack shipped, is
     *       recorded but not announced: they chose it, and telling them about their own choice is nagging;</li>
     *   <li>the host has not been recorded before. Every usable host is recorded on sight, including the ones
     *       that go unannounced, so a host a player had already been using does not get announced later just
     *       because a server started advertising it too.</li>
     * </ol>
     *
     * @return the host to name in the notification, or {@code null} when there is nothing to announce.
     */
    public static @Nullable String recordRelayHostAndReportIfNewlyServerChosen() {
        if (!externalRelayUsable()) {
            return null;
        }
        String host = RelayAddresses.host(externalServerAddress());
        if (host == null) {
            return null;
        }
        EunomiaConfig config = clientConfig();
        if (config == null) {
            return null;
        }
        boolean firstTime = config.rememberRelayHost(host);
        return firstTime && serverSteeredOntoTheRelay() ? host : null;
    }

    /** Whether the joined server is what put this connection on a relay, as opposed to the player or the mod. */
    private static boolean serverSteeredOntoTheRelay() {
        ServerSyncPolicy policy = advertisedPolicy();
        return Boolean.TRUE.equals(policy.enableExternalFallback())
                || validAdvertisedAddress(policy) != null;
    }

    // ── The chain ───────────────────────────────────────────────────────────────────────────────

    private static <T> T resolve(
            Function<EunomiaConfig, @Nullable T> playerOverride,
            Function<ServerSyncPolicy, @Nullable T> advertised,
            @Nullable T modDefault,
            T frameworkDefault) {
        EunomiaConfig config = clientConfig();
        if (config != null) {
            T override = playerOverride.apply(config);
            if (override != null) {
                return override;
            }
        }
        T fromServer = advertised.apply(advertisedPolicy());
        if (fromServer != null) {
            return fromServer;
        }
        return modDefault != null ? modDefault : frameworkDefault;
    }

    private static @Nullable EunomiaConfig clientConfig() {
        try {
            return clientConfigSource.get();
        } catch (Exception e) {
            // A half-initialised config provider must not take the transport decision down with it.
            return null;
        }
    }

    private static ServerSyncPolicy advertisedPolicy() {
        try {
            ServerSyncPolicy policy = advertisedPolicySource.get();
            return policy == null ? ServerSyncPolicy.UNKNOWN : policy;
        } catch (Exception e) {
            return ServerSyncPolicy.UNKNOWN;
        }
    }
}
