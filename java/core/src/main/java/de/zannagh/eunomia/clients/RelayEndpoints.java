package de.zannagh.eunomia.clients;

import de.zannagh.eunomia.common.ApiVersion;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * Derives the concrete REST and WebSocket URIs from a configured relay base address. A bare host is assumed to be
 * {@code http}; the WebSocket URI reuses the base with {@code http→ws} / {@code https→wss}.
 *
 * <p>This is the single place the {@code /api/v<major>.<minor>} version segment is injected, so no call site
 * hand-writes a versioned path. {@code /health} and {@code /ws} are deliberately NOT versioned - the relay serves
 * both unversioned, and the WebSocket carries its version in the handshake query instead.
 */
final class RelayEndpoints {

    private RelayEndpoints() {
    }

    /** Normalizes a configured address to a scheme-qualified base with no trailing slash. */
    static String base(String address) {
        String value = address.trim();
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "http://" + value;
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    /** An unversioned URI - for the relay's version-independent endpoints ({@code /health}). */
    static URI http(String base, String path) {
        return URI.create(base + path);
    }

    /**
     * A versioned REST URI: {@code <base>/api/v<major>.<minor><path>}. {@code path} is the part after the
     * version segment, e.g. {@code "/packets/keyed"}. The WebSocket handshake reads the same
     * {@link ApiVersion#CURRENT} into its query string, so both sides of the wire share one source of truth.
     */
    static URI api(String base, String path) {
        return URI.create(base + "/api/v" + ApiVersion.CURRENT + path);
    }

    static URI ws(String base, String path) {
        return URI.create(base.replaceFirst("^http", "ws") + path);
    }

    /**
     * The receive-socket handshake URI. {@code /ws} itself stays unversioned; the relay reads the client's API
     * version off the query string and treats a missing {@code v} as a legacy client, closing the socket with
     * {@link RelayProtocol#UNSUPPORTED_VERSION_CLOSE} when it cannot serve the one it is given.
     */
    static URI handshake(String base, UUID playerId, String scope, String name) {
        Charset utf8 = StandardCharsets.UTF_8;
        return ws(base, "/ws?id=" + playerId
                + "&scope=" + URLEncoder.encode(scope, utf8)
                + "&name=" + URLEncoder.encode(name == null ? "" : name, utf8)
                + "&v=" + ApiVersion.CURRENT);
    }

    /** A {@code PUT} of one already-serialized envelope to a versioned packet endpoint. */
    static HttpRequest packetPut(String base, String path, String json) {
        return HttpRequest.newBuilder(api(base, path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build();
    }
}
