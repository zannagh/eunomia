package de.zannagh.eunomia.networking.handshake;

import java.util.ArrayList;
import java.util.List;

/**
 * The server's answer to a {@link ClientHelloPayload}: proof it runs Eunomia, plus the exact list of
 * serverbound channels it has a handler for. A consuming mod can therefore check not just "is Eunomia
 * present" but "does this server actually receive <em>my</em> packet" - the precise condition for
 * deciding whether the mod's server half is installed, or whether to route through a custom server.
 */
public class ServerHelloPayload {

    public int protocolVersion;
    public List<String> receiverChannels;

    public ServerHelloPayload() {
        this.receiverChannels = new ArrayList<>();
    }

    public ServerHelloPayload(int protocolVersion, List<String> receiverChannels) {
        this.protocolVersion = protocolVersion;
        this.receiverChannels = receiverChannels;
    }
}
