package de.zannagh.eunomia.clients;

import com.google.gson.Gson;
import de.zannagh.eunomia.keyed.StoreSyncPackets;
import de.zannagh.eunomia.keyed.StoreSyncPayload;
import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.packets.ClientContext;
import de.zannagh.eunomia.networking.packets.PacketType;
import de.zannagh.eunomia.networking.serialization.NetworkSerializer;
import org.slf4j.Logger;

/** Decodes the relay's WebSocket frames and dispatches them into the clientbound handlers. */
final class RelayFrames {

    private RelayFrames() {
    }

    /** Decodes and dispatches one frame. Never throws - this runs straight off a WebSocket callback. */
    static void dispatch(String json, Logger logger) {
        try {
            decodeAndDispatch(json);
        } catch (Exception e) {
            logger.warn("Failed to handle relay frame", e);
        }
    }

    private static void decodeAndDispatch(String json) {
        Gson gson = NetworkSerializer.gson();
        WsFrame frame = gson.fromJson(json, WsFrame.class);
        if (frame == null || frame.type == null || frame.data == null) {
            return;
        }
        ClientContext context = new RelayClientContext();
        if ("store_sync".equals(frame.type)) {
            StoreSyncPayload sync = gson.fromJson(frame.data, StoreSyncPayload.class);
            CommunicationManager.dispatchClientbound(StoreSyncPackets.STORE_SYNC.channelKey(), sync, context);
            return;
        }
        if ("envelope".equals(frame.type)) {
            PacketEnvelope envelope = gson.fromJson(frame.data, PacketEnvelope.class);
            PacketType<?> type = CommunicationManager.type(envelope.channel);
            if (type != null && envelope.payload != null) {
                Object payload = gson.fromJson(envelope.payload, type.payloadClass());
                CommunicationManager.dispatchClientbound(envelope.channel, payload, context);
            }
        }
    }
}
