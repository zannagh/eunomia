package de.zannagh.eunomia.clients;

import de.zannagh.eunomia.common.ApiVersion;

import java.net.URI;

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
}
