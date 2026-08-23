package de.zannagh.eunomia.clients;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The server-side relay-address rules. These back a security control, not a UX affordance: the settings
 * screen runs an equivalent check while the player types, but this is the one that decides what a server
 * will actually store on behalf of a client it cannot see the source of.
 *
 * <p>The assertions about normalisation are deliberately phrased against {@code RelayEndpoints.base} rather
 * than against a hand-written expectation, because the whole reason this class lives in this package is that
 * validation and dialling must not drift apart.</p>
 */
class RelayAddressesTest {

    @Test
    void nullAndBlankMeanClearTheAddressRatherThanMalformed() {
        assertThat(RelayAddresses.isClearing(null)).isTrue();
        assertThat(RelayAddresses.isClearing("")).isTrue();
        assertThat(RelayAddresses.isClearing("   ")).isTrue();
        assertThat(RelayAddresses.isClearing("relay.example")).isFalse();
        // "Clearing" is a distinct outcome from "valid": a cleared address is accepted but not normalisable.
        assertThat(RelayAddresses.isValid(null)).isFalse();
        assertThat(RelayAddresses.isValid("  ")).isFalse();
    }

    @Test
    void httpsHttpAndBareHostsAreAllUsable() {
        assertThat(RelayAddresses.isValid("https://relay.example")).isTrue();
        // Plain http is allowed on purpose - RelayEndpoints.base() has always assumed it for bare hosts,
        // so refusing it here would reject addresses the transport dials perfectly well.
        assertThat(RelayAddresses.isValid("http://192.168.1.10:8080")).isTrue();
        assertThat(RelayAddresses.isValid("relay.example")).isTrue();
        assertThat(RelayAddresses.isValid("relay.example:8080/sync")).isTrue();
    }

    @Test
    void anythingThatIsNotAnHttpUrlIsRefused() {
        assertThat(RelayAddresses.isValid("ftp://relay.example")).isFalse();
        assertThat(RelayAddresses.isValid("javascript:alert(1)")).isFalse();
        assertThat(RelayAddresses.isValid("not a url at all")).isFalse();
        assertThat(RelayAddresses.isValid("http://")).isFalse();
        assertThat(RelayAddresses.isValid("https://[unclosed")).isFalse();
        assertThat(RelayAddresses.isValid("a".repeat(RelayAddresses.MAX_LENGTH + 1) + ".example")).isFalse();
    }

    @Test
    void normalisationIsExactlyWhatTheTransportWouldDial() {
        assertThat(RelayAddresses.normalize("relay.example/")).isEqualTo(RelayEndpoints.base("relay.example"));
        assertThat(RelayAddresses.normalize("relay.example")).isEqualTo("http://relay.example");
        assertThat(RelayAddresses.normalize("  https://relay.example//  ")).isEqualTo("https://relay.example");
    }

    @Test
    void normalisingSomethingUnusableThrowsRatherThanInventingAnAddress() {
        assertThatThrownBy(() -> RelayAddresses.normalize("ftp://relay.example"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * The scheme a bare host is assumed to carry. This is the assertion that keeps the settings screen
     * honest: {@code RelayAddress} on the client used to re-implement this check and assumed
     * {@code https://} for a bare host, while {@code RelayEndpoints.base} - the function that actually
     * dials, and the one a server normalises stored addresses with - assumed {@code http://}. Typing a
     * bare host therefore produced a screen that described one scheme while the machinery used the
     * other. The client now delegates here, so there is one answer and this test names it.
     */
    @Test
    void aBareHostIsAssumedToBeHttpAndNothingElse() {
        assertThat(RelayAddresses.normalize("relay.example")).startsWith("http://");
        assertThat(RelayAddresses.normalize("relay.example")).doesNotStartWith("https://");
        assertThat(RelayAddresses.normalize("relay.example:8080/sync"))
                .isEqualTo(RelayEndpoints.base("relay.example:8080/sync"));
    }

    /**
     * Normalising an already-normalised address must be a no-op. The settings screen commits the
     * normalised form rather than the raw keystrokes, so that value is read straight back into the same
     * field the next time the screen opens and re-validated from there; a normalisation that changed its
     * own output would make an address drift a little further every time a player looked at it.
     */
    @Test
    void normalisationIsIdempotent() {
        String once = RelayAddresses.normalize("relay.example/");
        assertThat(RelayAddresses.isValid(once)).isTrue();
        assertThat(RelayAddresses.normalize(once)).isEqualTo(once);
    }

    /**
     * The sibling of the {@code ftp://} hole. {@code isValid} lower-cased before its scheme check while
     * {@code RelayEndpoints.base} compared case-sensitively, so {@code HTTPS://relay.example} was declared
     * valid and then normalised to {@code http://HTTPS://relay.example} - a URL whose host is the literal
     * string {@code HTTPS}. A server stored that, answered APPLIED, and advertised it to every joining
     * client, all of whom silently failed to dial anything. Both halves now fold the scheme to lower case.
     */
    @Test
    void anUppercaseSchemeIsRecognisedRatherThanTreatedAsPartOfTheHost() {
        assertThat(RelayAddresses.isValid("HTTPS://relay.example")).isTrue();
        assertThat(RelayAddresses.normalize("HTTPS://relay.example")).isEqualTo("https://relay.example");
        assertThat(RelayAddresses.isValid("hTTp://relay.example:8080")).isTrue();
        assertThat(RelayAddresses.normalize("hTTp://relay.example:8080"))
                .isEqualTo("http://relay.example:8080");
        assertThat(RelayAddresses.normalize("HTTP://relay.example")).isEqualTo(RelayEndpoints.base("relay.example"));
        // The mixed-case scheme must never survive into the stored value, or ws()'s "^http -> ws" rewrite
        // (also case-sensitive) would produce a WebSocket URI with no scheme change at all.
        assertThat(RelayAddresses.normalize("HTTPS://relay.example")).doesNotContain("HTTP");
        // And a foreign scheme is still refused however it is cased.
        assertThat(RelayAddresses.isValid("FTP://relay.example")).isFalse();
        assertThat(RelayAddresses.isValid("JavaScript:alert(1)")).isFalse();
    }

    /**
     * A relay address is a <em>base</em>: every URI the transport dials is this string with a path glued on.
     * A query or a fragment therefore cannot survive that concatenation intact, and one that is silently
     * dropped stores an address the operator did not type. Refused, so they retype the host they meant.
     */
    @Test
    void aQueryOrAFragmentIsRefusedRatherThanSilentlyMangled() {
        assertThat(RelayAddresses.isValid("http://relay.example?a=b")).isFalse();
        assertThat(RelayAddresses.isValid("relay.example?a=b")).isFalse();
        assertThat(RelayAddresses.isValid("http://relay.example#@evil.example")).isFalse();
        assertThat(RelayAddresses.isValid("https://relay.example/sync?token=1")).isFalse();
        assertThatThrownBy(() -> RelayAddresses.normalize("http://relay.example?a=b"))
                .isInstanceOf(IllegalArgumentException.class);
        // A plain path is still fine - that is the part concatenation handles correctly.
        assertThat(RelayAddresses.isValid("https://relay.example/sync")).isTrue();
    }

    /**
     * The holes that were already closed before this round, kept as a regression net: a normalisation that
     * invents a scheme makes any scheme filter applied after it meaningless.
     */
    @Test
    void theAlreadyClosedSchemeHolesStayClosed() {
        assertThat(RelayAddresses.isValid("ftp://relay.example")).isFalse();
        assertThat(RelayAddresses.isValid("file:///etc/passwd")).isFalse();
        assertThat(RelayAddresses.isValid("//evil.example")).isFalse();
        assertThat(RelayAddresses.isValid("http:///")).isFalse();
    }

    /**
     * {@link RelayAddresses#host(String)} answers "which party is this data going to", which is what the
     * client-side "you have never used this relay before" notification compares on. Port, path and scheme
     * changes are the same party; a different name is not.
     */
    @Test
    void theHostIsExtractedCaseFoldedAndIgnoresPortPathAndScheme() {
        assertThat(RelayAddresses.host("https://Relay.Example:8443/sync")).isEqualTo("relay.example");
        assertThat(RelayAddresses.host("relay.example")).isEqualTo("relay.example");
        assertThat(RelayAddresses.host("HTTPS://relay.example")).isEqualTo("relay.example");
        assertThat(RelayAddresses.host("http://relay.example")).isEqualTo(RelayAddresses.host("relay.example:9000"));
        assertThat(RelayAddresses.host("ftp://relay.example")).isNull();
        assertThat(RelayAddresses.host(null)).isNull();
        assertThat(RelayAddresses.host("  ")).isNull();
    }

    /**
     * Surrounding whitespace is trimmed rather than treated as a malformed address, while whitespace in
     * the middle still is one. The old client-side check rejected both, which meant a pasted address with
     * a stray trailing space read as invalid on the screen and as valid to the server.
     */
    @Test
    void surroundingWhitespaceIsTrimmedButInnerWhitespaceIsNot() {
        assertThat(RelayAddresses.isValid("  relay.example  ")).isTrue();
        assertThat(RelayAddresses.normalize("  relay.example  ")).isEqualTo("http://relay.example");
        assertThat(RelayAddresses.isValid("relay example")).isFalse();
    }
}
