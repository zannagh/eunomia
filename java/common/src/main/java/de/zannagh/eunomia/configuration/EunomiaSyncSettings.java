package de.zannagh.eunomia.configuration;

import de.zannagh.eunomia.Eunomia;
import de.zannagh.eunomia.clients.RelayAddresses;
import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.handshake.ServerSyncPolicy;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Resolves the <em>effective</em> value of each external-transport setting on the client. This is the single
 * place any consumer may read these settings from; reading a config field directly is a bug, because a field is
 * only ever one of four possible sources and the four are ranked.
 *
 * <p>Precedence, highest first:</p>
 * <ol>
 *   <li>a value the joined server <em>enforces</em> - it has both stated an opinion on that setting and declared
 *       it non-negotiable. Nothing outranks it, the player's own override included, because the point of
 *       enforcement is that relay traffic cannot be made to bypass the server the player joined;</li>
 *   <li>the player's explicit override in {@link EunomiaConfig} (a non-null field - a deliberate choice);</li>
 *   <li>the policy the joined server advertised in the capability handshake ({@link ServerSyncPolicy});</li>
 *   <li>the consuming mod's client-side default ({@link EunomiaSyncDefaults});</li>
 *   <li>the framework default ({@link EunomiaDefaults}).</li>
 * </ol>
 *
 * <p>Enforcement is not a rung of its own so much as a promotion of rung two, and it is deliberately narrow: a
 * server can only enforce what it actually has a value for, so "enforce" over a setting the server says nothing
 * about changes nothing at all. A server that does not enforce behaves exactly as it always has.</p>
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
                SyncSetting.EXTERNAL_FALLBACK,
                EunomiaConfig::enableExternalFallbackOverride,
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
                SyncSetting.EXTERNAL_SERVER_ADDRESS,
                EunomiaConfig::externalServerAddressOverride,
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
                SyncSetting.PREFER_EXTERNAL_TRANSPORT,
                EunomiaConfig::preferExternalTransportOverride,
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

    /**
     * Records that the "this server offers no eunomia sync" notification is being shown for
     * {@code serverScope}, and reports whether it should be shown at all - {@code false} once that server has
     * already been announced.
     *
     * <p>This is the "have I said this already" half of the missing-server-sync diagnostic, kept here rather
     * than in the client code that draws it for the same reason the relay-host bookkeeping is: the client
     * source set has no tests, and the decision whether eunomia nags is exactly the part worth covering. The
     * other half - whether the situation warrants a card in the first place - stays in
     * {@code SyncDiagnostics}, which is a pure predicate over the connection's facts. Both must hold.</p>
     *
     * <p>A query with a side effect, deliberately: the record and the "was it new" answer have to be one
     * atomic step or two resolutions racing through the same server both announce it.</p>
     *
     * <p>Unlike {@link #recordRelayHostAndReportIfNewlyServerChosen()} this flushes the config to disk
     * immediately. Merely marking it dirty would leave the record durable only if the player later happens to
     * open the settings screen, which is precisely the player who never will - so the notification would come
     * back on the next launch and the dedup would only work within a session.</p>
     *
     * @param serverScope the joined Minecraft server's address; {@code null} and blank suppress the card,
     *                    because a server that cannot be identified cannot be announced only once.
     * @return {@code true} when this server has not been announced before and the card should be raised.
     */
    public static boolean recordSyncUnavailableAnnouncement(@Nullable String serverScope) {
        EunomiaConfig config = clientConfig();
        if (config == null) {
            return false;
        }
        if (!config.rememberAnnouncedSyncUnavailable(serverScope)) {
            return false;
        }
        persistClientConfig();
        return true;
    }

    /**
     * Flushes the client config, swallowing everything. The caller is a diagnostic: losing the record costs
     * one repeated notification, while letting an IO failure out of here would break the join it rode in on.
     */
    private static void persistClientConfig() {
        try {
            if (Eunomia.CONFIG_PROVIDER != null) {
                Eunomia.CONFIG_PROVIDER.saveCurrent();
            }
        } catch (Exception e) {
            Eunomia.LOGGER.debug("Failed to persist the sync-diagnostic bookkeeping", e);
        }
    }

    /** Whether the joined server is what put this connection on a relay, as opposed to the player or the mod. */
    private static boolean serverSteeredOntoTheRelay() {
        ServerSyncPolicy policy = advertisedPolicy();
        return Boolean.TRUE.equals(policy.enableExternalFallback())
                || validAdvertisedAddress(policy) != null;
    }

    // ── Enforcement (public API for consuming mods' own UIs) ───────────────────────────────────

    /**
     * Whether the joined server forces {@code setting}, so that nothing the player chooses can change it.
     *
     * <p>This is what a consuming mod greys its own control out on. It is deliberately the <em>same</em>
     * predicate the resolver uses, so a control shown as locked is exactly a control whose value the resolver is
     * taking from the server: a server that declares a setting enforced but states no value for it does not lock
     * anything, and an enforced relay address that could not be dialled does not either.</p>
     *
     * <p>Never throws. Like every other read here it degrades to "not locked" when the capability view is not
     * up yet, because this is called from a UI thread while a screen is being built.</p>
     *
     * @param setting the setting to test, may be {@code null}.
     * @return whether the server has locked that setting to its own value.
     */
    public static boolean isLockedByServer(@Nullable SyncSetting setting) {
        if (setting == null) {
            return false;
        }
        try {
            ServerSyncPolicy policy = advertisedPolicy();
            return locks(policy, setting, advertisedValue(policy, setting));
        } catch (Exception e) {
            // A screen being built mid-handshake must not crash over decoration; unlocked is the safe read.
            return false;
        }
    }

    /**
     * Every setting the joined server currently locks - the set form of {@link #isLockedByServer}, for a UI that
     * would otherwise ask three times while the policy could change underneath it.
     *
     * @return the locked settings, never {@code null}; empty on a server that enforces nothing.
     */
    public static Set<SyncSetting> lockedByServer() {
        try {
            ServerSyncPolicy policy = advertisedPolicy();
            EnumSet<SyncSetting> locked = EnumSet.noneOf(SyncSetting.class);
            for (SyncSetting setting : SyncSetting.values()) {
                if (locks(policy, setting, advertisedValue(policy, setting))) {
                    locked.add(setting);
                }
            }
            return Set.copyOf(locked);
        } catch (Exception e) {
            return Set.of();
        }
    }

    // ── The chain ───────────────────────────────────────────────────────────────────────────────

    /**
     * The whole precedence chain, for one setting.
     *
     * <p>The advertised value is read exactly once, through {@link #advertisedValue}, and that single read is
     * what both the enforcement check and rung two use. Reading it twice - or letting the caller pass its own
     * accessor - is how a setting ends up locked to one value and resolved to another.</p>
     *
     * <p>The cast is safe by construction: {@link #advertisedValue} is the only mapping from a
     * {@link SyncSetting} to a policy component, and every caller here passes the matching {@code T}.</p>
     */
    @SuppressWarnings("unchecked")
    private static <T> T resolve(
            SyncSetting setting,
            Function<EunomiaConfig, @Nullable T> playerOverride,
            @Nullable T modDefault,
            T frameworkDefault) {
        ServerSyncPolicy policy = advertisedPolicy();
        T fromServer = (T) advertisedValue(policy, setting);
        if (locks(policy, setting, fromServer)) {
            // Rung zero: the server both has a value here and insists on it, so the player's override does not
            // even get asked. Note that the override is not cleared - it applies again the moment they join a
            // server that does not enforce this.
            return fromServer;
        }
        EunomiaConfig config = clientConfig();
        if (config != null) {
            T override = playerOverride.apply(config);
            if (override != null) {
                return override;
            }
        }
        if (fromServer != null) {
            return fromServer;
        }
        return modDefault != null ? modDefault : frameworkDefault;
    }

    /**
     * The one mapping from a setting to what the joined server advertised for it, or {@code null} for "the
     * server said nothing usable". The address goes through {@link #validAdvertisedAddress}, so an enforced but
     * malformed address locks nothing and falls through - a server with a typo in its config loses that one
     * value, it does not get to pin every joining player to an address nobody can dial.
     */
    private static @Nullable Object advertisedValue(ServerSyncPolicy policy, SyncSetting setting) {
        return switch (setting) {
            case EXTERNAL_FALLBACK -> policy.enableExternalFallback();
            case EXTERNAL_SERVER_ADDRESS -> validAdvertisedAddress(policy);
            case PREFER_EXTERNAL_TRANSPORT -> policy.preferExternalTransport();
        };
    }

    /**
     * The single enforcement predicate, shared by the resolver and by the public {@link #isLockedByServer}.
     * Both halves are necessary: enforcement is a modifier on a value, so a server that enforces a setting it
     * has no opinion about locks nothing. If these two callers ever disagreed, a consuming mod would grey out a
     * control the resolver is not in fact overriding.
     */
    private static boolean locks(ServerSyncPolicy policy, SyncSetting setting, @Nullable Object advertised) {
        return advertised != null && policy.enforces(setting);
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
