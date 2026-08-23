package de.zannagh.eunomia.client.gui.screens;

import de.zannagh.eunomia.clients.RelayAddresses;

/**
 * The cheap, offline sanity check applied to a relay address typed into the settings screen.
 *
 * <p>Deliberately syntactic only. A settings screen must not perform network I/O - a DNS lookup or a
 * probe request would block the render thread, leak the fact that the player is typing to whatever
 * host is half-typed, and be wrong the moment the player is offline while configuring. So this answers
 * "could this ever be dialled", not "is this reachable"; the transport layer finds out the rest.
 *
 * <p><strong>Every rule below lives in {@code :core}.</strong> This class used to re-implement the
 * check, and the re-implementation had quietly diverged: it assumed {@code https://} for a bare host
 * while {@code RelayEndpoints.base(String)} - the function the transport actually dials through, and
 * the one the server normalises stored addresses with - assumes {@code http://}. Typing
 * {@code relay.example} therefore produced a screen that talked about one scheme while the machinery
 * behind it used the other. Two implementations of "what is a relay address" is one too many, so this
 * is now a thin forwarder onto {@link RelayAddresses}, which is itself a thin forwarder onto
 * {@code RelayEndpoints}. There is exactly one answer and the UI reads it rather than guessing it.
 *
 * <p>The surviving convention is therefore {@code http://}, because that is what gets dialled. Making
 * the screen say {@code https} instead would have been a one-line lie; making the transport say
 * {@code https} would have silently broken every relay reachable only over plaintext on a LAN or
 * behind a TLS-terminating proxy, and would have changed the meaning of addresses already stored in
 * config files and already advertised by servers in the capability handshake.
 */
public final class RelayAddress {

    /** Generous enough for a long path-carrying URL, short enough to keep the config file sane. */
    public static final int MAX_LENGTH = RelayAddresses.MAX_LENGTH;

    private RelayAddress() {
    }

    /**
     * Whether the text is a plausible relay address: an absolute {@code http}/{@code https} URL with a
     * host, or a bare host (optionally with a port), which {@link #normalize} qualifies with
     * {@code http://}.
     * @param text the trimmed text typed by the player.
     * @return {@code true} when the address is syntactically usable.
     */
    public static boolean isValid(String text) {
        return RelayAddresses.isValid(text);
    }

    /**
     * The form the address is actually stored and dialled in - scheme-qualified, no trailing slash.
     * The screen commits this rather than the raw keystrokes so that what a player reads back out of
     * the field afterwards is the string the transport will use, not an abbreviation of it.
     * @param text a value that passed {@link #isValid}.
     * @return the normalised address.
     * @throws IllegalArgumentException if {@code text} does not pass {@link #isValid}.
     */
    public static String normalize(String text) {
        return RelayAddresses.normalize(text);
    }
}
