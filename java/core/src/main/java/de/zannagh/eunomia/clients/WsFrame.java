package de.zannagh.eunomia.clients;

import com.google.gson.JsonElement;

/**
 * A tagged frame the relay pushes over the WebSocket. {@link #type} is {@code "envelope"} (a single relayed
 * {@link PacketEnvelope} update) or {@code "store_sync"} (a batch {@code StoreSyncPayload} snapshot); {@link #data}
 * is the corresponding object. The tag lets the client route the frame without guessing from its shape.
 */
public class WsFrame {

    public String type;

    public JsonElement data;

    public WsFrame() {
    }
}
