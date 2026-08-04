package de.zannagh.eunomia.networking.examples;

/** The clientbound answer to a {@link PingPayload}, echoing the message and stamping a server time. */
public class PongPayload {

    public String message;
    public long echoedSentAtMillis;
    public long serverTimeMillis;

    public PongPayload() {
    }

    public PongPayload(String message, long echoedSentAtMillis, long serverTimeMillis) {
        this.message = message;
        this.echoedSentAtMillis = echoedSentAtMillis;
        this.serverTimeMillis = serverTimeMillis;
    }
}
