package de.zannagh.eunomia.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The API path segment is derived from the mod version, so a parsing slip would produce URLs no relay route serves. */
class ApiVersionTest {

    @Test
    void truncatesThePatchFromCleanVersions() {
        assertEquals("0.3", ApiVersion.of("0.3.0"));
        assertEquals("0.3", ApiVersion.of("0.3.7"));
        assertEquals("0.4", ApiVersion.of("0.4.0"));
        assertEquals("1.0", ApiVersion.of("1.0.0"));
        assertEquals("1.1", ApiVersion.of("1.1.2"));
        assertEquals("10.20", ApiVersion.of("10.20.30"));
    }

    @Test
    void handlesTheDirtyFormsGitVersionAndTheLoadersActuallyEmit() {
        assertEquals("0.3", ApiVersion.of("0.3.0-preview.5"));
        assertEquals("0.3", ApiVersion.of("0.3.1-alpha.2+7"));
        // A loader artifact version: the +mc suffix must not leak into the path.
        assertEquals("0.3", ApiVersion.of("0.3.0+mc-1.21.9-10"));
        assertEquals("0.3", ApiVersion.of("0.3.0-dev"));
        assertEquals("0.3", ApiVersion.of("  0.3.0  "));
    }

    @Test
    void neverProducesASegmentContainingAPlusOrAPrereleaseTail() {
        for (String version : new String[] {"0.3.1-alpha.2+7", "0.3.0+mc-1.21.9-10", "0.3.0-preview.5"}) {
            String segment = ApiVersion.of(version);
            assertTrue(segment.matches("\\d+\\.\\d+"), "segment for " + version + " was " + segment);
        }
    }

    @Test
    void failsLoudlyOnMalformedInput() {
        for (String version : new String[] {null, "", "   ", "not-a-version", "v0.3.0", "x.y.z", "-1.2.0"}) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> ApiVersion.of(version));
            assertTrue(error.getMessage().contains("-PsemVer"), "message should name -PsemVer: " + error.getMessage());
        }
    }

    @Test
    void theCurrentBuildResolvesToAUsableSegment() {
        assertNotNull(BuildInfo.VERSION);
        assertTrue(ApiVersion.CURRENT.matches("\\d+\\.\\d+"), "CURRENT was " + ApiVersion.CURRENT);
    }

    @Test
    void semanticVersionParseRejectsWhatIsNotAVersion() {
        assertNull(SemanticVersion.parse(null));
        assertNull(SemanticVersion.parse(""));
        assertNull(SemanticVersion.parse("nonsense"));
        SemanticVersion parsed = SemanticVersion.parse("0.3.1-alpha.2+7");
        assertNotNull(parsed);
        assertEquals(0, parsed.major());
        assertEquals(3, parsed.minor());
        assertEquals(1, parsed.patch());
        assertEquals("alpha.2+7", parsed.build());
    }
}
