package de.zannagh.eunomia.networking.handshake;

/**
 * Serverbound probe a Eunomia client sends on join to ask "do you speak Eunomia, and which packets
 * can you receive?". A server running Eunomia (loader or Paper) answers with a {@link ServerHelloPayload};
 * silence means the server does not run Eunomia at all - which is the signal a client uses to fall
 * back to a custom (e.g. HTTP) communications server.
 */
public class ClientHelloPayload {

    public int protocolVersion;

    public ClientHelloPayload() {
    }

    public ClientHelloPayload(int protocolVersion) {
        this.protocolVersion = protocolVersion;
    }
}
