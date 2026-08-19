package de.zannagh.eunomia.clients;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * A tiny reachability probe for the external relay: a short-timeout {@code GET /health}. Used by the client
 * transport selector to decide whether the configured relay is actually up before routing traffic to it.
 */
public final class PingClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private PingClient() {
    }

    /** Whether {@code baseAddress}'s {@code /health} answers 2xx within the timeout. Never throws. */
    public static boolean isReachable(String baseAddress) {
        if (baseAddress == null || baseAddress.isBlank()) {
            return false;
        }
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
            URI uri = RelayEndpoints.http(RelayEndpoints.base(baseAddress), "/health");
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            return false;
        }
    }
}
