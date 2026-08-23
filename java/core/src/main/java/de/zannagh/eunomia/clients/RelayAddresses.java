package de.zannagh.eunomia.clients;

import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Server-side validation and normalisation of a configured relay ("Cloud Sync") address.
 *
 * <p>It lives in this package rather than next to the packets so it can delegate to the
 * package-private {@link RelayEndpoints#base(String)} - the very function that later turns the stored
 * value into the URIs actually dialled. Validating against a re-implementation of those rules is how
 * the two drift apart and a value passes the check but fails the dial, so there is exactly one
 * normalisation and this class is a thin, public front door onto it.</p>
 *
 * <p><strong>Plain {@code http://} is allowed, on purpose.</strong> {@link RelayEndpoints#base(String)}
 * prefixes a bare host with {@code http://}, so a relay run on a LAN or behind a reverse proxy that
 * terminates TLS elsewhere has always been reachable; rejecting {@code http} here would refuse to store
 * addresses the transport is perfectly happy to use, which is a worse outcome than an operator
 * knowingly configuring a plaintext relay on their own network.</p>
 *
 * <p>Syntactic only, like the settings screen's client-side check: this answers "could this ever be
 * dialled", not "is this reachable". A packet handler must not block a server thread on DNS.</p>
 *
 * @since 0.3.0
 */
public final class RelayAddresses {

    /** Generous enough for a long path-carrying URL, short enough to keep the config file sane. */
    public static final int MAX_LENGTH = 256;

    private RelayAddresses() {
    }

    /**
     * Whether {@code address} means "this server points at no relay". Null and blank are a legitimate,
     * expressible state - {@code ServerSyncPolicy} normalises a blank address to "said nothing" - so
     * clearing the field is an accepted write, not a malformed one.
     *
     * @param address the address as submitted, may be {@code null}.
     * @return whether the submission clears the address.
     */
    public static boolean isClearing(@Nullable String address) {
        return address == null || address.isBlank();
    }

    /**
     * Whether {@code address} is a syntactically usable relay address: an absolute {@code http}/{@code https}
     * URL with a host, or a bare host (optionally with a port), which {@link #normalize} will qualify.
     *
     * @param address the address as submitted, may be {@code null}.
     * @return {@code true} only for a non-blank, well-formed address.
     */
    public static boolean isValid(@Nullable String address) {
        if (isClearing(address)) {
            return false;
        }
        String value = address.trim();
        if (value.length() > MAX_LENGTH || value.chars().anyMatch(Character::isWhitespace)) {
            return false;
        }
        // Reject a foreign scheme BEFORE normalising. RelayEndpoints.base() prefixes anything that is not
        // already http(s) with "http://", so "ftp://relay.example" would otherwise become the perfectly
        // parseable "http://ftp://relay.example" and sail through the check below - a scheme filter applied
        // after a normalisation that invents a scheme is no filter at all.
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("://") && !lower.startsWith("http://") && !lower.startsWith("https://")) {
            return false;
        }
        // A query or a fragment is refused rather than stripped, because a relay base address is never used
        // whole: RelayEndpoints concatenates "/health", "/api/v<x.y>/..." and "/ws?..." onto it. A base of
        // "http://relay.example?a=b" would therefore be dialled as "http://relay.example?a=b/health", where
        // the path has become part of the query string and the probe hits the site root. A fragment is worse
        // still - "http://relay.example#@evil.example" reads to a human as if it addressed evil.example.
        // Silently stripping either would store an address that is not the one the operator typed, so the
        // honest answer is to refuse it and let them retype the host they actually meant.
        if (value.indexOf('?') >= 0 || value.indexOf('#') >= 0) {
            return false;
        }
        try {
            URI uri = new URI(RelayEndpoints.base(value));
            String scheme = uri.getScheme();
            return uri.getHost() != null
                    && !uri.getHost().isBlank()
                    && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
        } catch (URISyntaxException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * The host of {@code address}, lower-cased, or {@code null} when the address is not usable at all.
     *
     * <p>Exists so that "have I seen this relay before?" can be asked about the destination rather than about
     * the exact string: an operator who appends a path, changes the port or switches scheme has not moved the
     * player's data anywhere new, and a notification that fired on every such edit would be noise. Derived
     * from {@link #normalize} rather than from the raw text, so the answer is the host that will actually be
     * dialled - which, before the scheme comparison in {@code RelayEndpoints} was made case-insensitive,
     * was not always the host a reader of the string would name.</p>
     *
     * @param address the address as configured or as advertised, may be {@code null}.
     * @return the host, lower-cased, or {@code null} if {@code address} fails {@link #isValid}.
     */
    public static @Nullable String host(@Nullable String address) {
        if (!isValid(address)) {
            return null;
        }
        try {
            String host = new URI(normalize(address)).getHost();
            return host == null || host.isBlank() ? null : host.toLowerCase(Locale.ROOT);
        } catch (URISyntaxException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * The form of {@code address} that gets stored: scheme-qualified and without a trailing slash, exactly
     * as {@link RelayEndpoints} would derive it at dial time. Storing the normalised form means the value an
     * operator reads back from the config, the value advertised in the handshake and the value dialled are
     * all the same string.
     *
     * @param address a value that passed {@link #isValid}.
     * @return the normalised address.
     * @throws IllegalArgumentException if {@code address} does not pass {@link #isValid}.
     */
    public static String normalize(@Nullable String address) {
        if (!isValid(address)) {
            throw new IllegalArgumentException("Not a usable relay address: " + address);
        }
        return RelayEndpoints.base(address.trim());
    }
}
