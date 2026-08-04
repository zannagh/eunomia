package de.zannagh.eunomia.networking.examples;

/**
 * A trivial serverbound payload used by the example handlers and the gametests. A plain POJO with
 * public fields is all a packet ever needs to be - {@code PayloadCodec} serializes it as JSON and no
 * {@code CustomPacketPayload}/{@code StreamCodec} boilerplate is required.
 */
public class PingPayload {

    public String message;
    public long sentAtMillis;

    public PingPayload() {
    }

    public PingPayload(String message, long sentAtMillis) {
        this.message = message;
        this.sentAtMillis = sentAtMillis;
    }
}
