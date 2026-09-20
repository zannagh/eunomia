package de.zannagh.eunomia.client.networking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The relay partitions every player's data by scope, so two spellings of one server address silently become
 * two stores. These pin the normalisation that stops that.
 */
@DisplayName("Relay scope normalisation")
class LocalClientIdentityScopeTest {

    @Test
    @DisplayName("an explicit default port and the bare host produce one scope")
    void defaultPortIsElided() {
        // The exact pair observed in production on 2026-09-20, from a single account minutes apart.
        assertEquals(LocalClientIdentity.normaliseScope("mc.hypixel.net"),
                LocalClientIdentity.normaliseScope("mc.hypixel.net:25565"),
                "a server-list entry and a direct connect to the same server must share a scope");
    }

    @Test
    @DisplayName("a non-default port stays part of the scope")
    void nonDefaultPortIsKept() {
        assertEquals("play.example.com:25566", LocalClientIdentity.normaliseScope("play.example.com:25566"));
    }

    @Test
    @DisplayName("case and surrounding whitespace do not split a scope")
    void caseAndWhitespaceAreNormalised() {
        assertEquals("play.example.com", LocalClientIdentity.normaliseScope("  Play.Example.COM  "));
    }

    @Test
    @DisplayName("a trailing FQDN dot does not split a scope")
    void trailingDotIsDropped() {
        assertEquals("play.example.com", LocalClientIdentity.normaliseScope("play.example.com."));
    }

    @Test
    @DisplayName("distinct hostnames stay distinct - normalisation never resolves DNS")
    void distinctHostsStayDistinct() {
        assertEquals("eu.example.com", LocalClientIdentity.normaliseScope("eu.example.com"));
        assertEquals("us.example.com", LocalClientIdentity.normaliseScope("us.example.com"));
    }

    @Test
    @DisplayName("absent or blank addresses yield no scope rather than an empty one")
    void blankYieldsNull() {
        assertNull(LocalClientIdentity.normaliseScope(null));
        assertNull(LocalClientIdentity.normaliseScope("   "));
        assertNull(LocalClientIdentity.normaliseScope(":25565"));
    }
}
