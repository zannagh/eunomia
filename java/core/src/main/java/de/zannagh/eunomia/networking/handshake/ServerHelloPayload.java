package de.zannagh.eunomia.networking.handshake;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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
        }
    }

    /**
     * The advertised policy, or {@link ServerSyncPolicy#UNKNOWN} when this payload carried none (a protocol-1
     * server, or a protocol-2 server that deliberately advertises nothing). Never throws and never invents
     * values, so a client on a newer protocol keeps working against an older server.
     */
    public ServerSyncPolicy syncPolicy() {
        return new ServerSyncPolicy(enableExternalFallback, externalServerAddress, preferExternalTransport);
    }

    /** The channels this payload lists, never {@code null} even if the JSON omitted the key entirely. */
    public List<String> receiverChannelsOrEmpty() {
        return receiverChannels == null ? List.of() : receiverChannels;
    }
}
