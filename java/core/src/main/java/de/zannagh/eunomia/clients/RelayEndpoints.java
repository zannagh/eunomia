package de.zannagh.eunomia.clients;

import java.net.URI;

/**
 * Derives the concrete REST and WebSocket URIs from a configured relay base address. A bare host is assumed to be
 * {@code http}; the WebSocket URI reuses the base with {@code http→ws} / {@code https→wss}.
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

    static URI http(String base, String path) {
        return URI.create(base + path);
    }

    static URI ws(String base, String path) {
        return URI.create(base.replaceFirst("^http", "ws") + path);
    }
}
