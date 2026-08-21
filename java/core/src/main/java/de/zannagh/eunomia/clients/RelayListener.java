package de.zannagh.eunomia.clients;

import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;

/**
 * The relay's WebSocket listener. Reassembles fragmented text frames and hands every lifecycle event back to the
 * owning {@link ExternalServerClient}, which owns the state machine (block detection, backoff, and the
 * {@link RelayConnectionState} the send gate keys off).
 */
final class RelayListener implements WebSocket.Listener {

    private final ExternalServerClient client;
    private final StringBuilder buffer = new StringBuilder();

    RelayListener(ExternalServerClient client) {
        this.client = client;
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        buffer.append(data);
        if (last) {
            String message = buffer.toString();
            buffer.setLength(0);
            client.handleFrame(message);
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        client.handleClose(statusCode, reason);
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        client.handleConnectFailure(error);
    }
}
