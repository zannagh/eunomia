package de.zannagh.eunomia.networking.handshake;

import de.zannagh.eunomia.configuration.SyncSetting;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The server's answer to a {@link ClientHelloPayload}: proof it runs Eunomia, the exact list of serverbound
 * channels it has a handler for, and (since protocol 2) the external-transport policy it advertises. A consuming
 * mod can therefore check not just "is Eunomia present" but "does this server actually receive <em>my</em>
 * packet" - the precise condition for deciding whether the mod's server half is installed, or whether to route
 * through a custom server.
 * <p>
 * <strong>Wire tolerance.</strong> The three policy fields are boxed and left {@code null} by the no-argument
 * constructor on purpose. A protocol-1 server's JSON simply lacks those keys, so Gson leaves them null and
 * {@link #syncPolicy()} yields {@link ServerSyncPolicy#UNKNOWN} - an absent opinion, which the settings resolver
 * falls through instead of throwing. Decoding a v1 payload is therefore an ordinary success, not an error path.
 */
public class ServerHelloPayload {

    public int protocolVersion;
    public List<String> receiverChannels;

    /** Server-advertised: whether the external relay fallback is permitted. Absent on protocol 1. */
    public @Nullable Boolean enableExternalFallback;

    /** Server-advertised: the relay address to use. Absent on protocol 1. */
    public @Nullable String externalServerAddress;

    /** Server-advertised: whether clients should prefer the relay over this server. Absent on protocol 1. */
    public @Nullable Boolean preferExternalTransport;

    /**
     * Server-advertised: the names of the settings above this server <em>enforces</em> rather than suggests.
     * Absent on any server built before enforcement existed.
     * <p>
     * A nullable list of {@link SyncSetting} names, never a boolean, and the distinction is the whole wire
     * contract. Gson leaves an absent key at its no-argument-constructor value, so a primitive {@code boolean
     * enforceSettings} would decode as {@code false} on an old server - indistinguishable from a new server that
     * explicitly enforces nothing. That is harmless today (both mean "advisory") and stops being harmless the
     * moment anything wants to tell "this server cannot enforce" from "this server chose not to". Names rather
     * than ordinals, because an enum ordinal is a wire format that reorders itself when someone inserts a
     * constant. No PROTOCOL_VERSION bump: nothing branches on the version, and tolerance is structural.
     */
    public @Nullable List<String> enforcedSettings;

    public ServerHelloPayload() {
        this.receiverChannels = new ArrayList<>();
    }

    public ServerHelloPayload(int protocolVersion, List<String> receiverChannels) {
        this.protocolVersion = protocolVersion;
        this.receiverChannels = receiverChannels;
    }

    public ServerHelloPayload(int protocolVersion, List<String> receiverChannels, ServerSyncPolicy policy) {
        this(protocolVersion, receiverChannels);
        if (policy != null) {
            this.enableExternalFallback = policy.enableExternalFallback();
            this.externalServerAddress = policy.externalServerAddress();
            this.preferExternalTransport = policy.preferExternalTransport();
            Set<SyncSetting> enforced = policy.enforced();
            // Stays null when nothing is enforced, so the common case puts no key on the wire at all and an
            // enforcing server is visibly different from a merely advisory one.
            if (!enforced.isEmpty()) {
                this.enforcedSettings = enforced.stream().map(Enum::name).sorted().toList();
            }
        }
    }

    /**
     * The advertised policy, or {@link ServerSyncPolicy#UNKNOWN} when this payload carried none (a protocol-1
     * server, or a protocol-2 server that deliberately advertises nothing). Never throws and never invents
     * values, so a client on a newer protocol keeps working against an older server.
     */
    public ServerSyncPolicy syncPolicy() {
        return new ServerSyncPolicy(enableExternalFallback, externalServerAddress, preferExternalTransport,
                SyncSetting.parseAll(enforcedSettings));
    }

    /** The channels this payload lists, never {@code null} even if the JSON omitted the key entirely. */
    public List<String> receiverChannelsOrEmpty() {
        return receiverChannels == null ? List.of() : receiverChannels;
    }
}
