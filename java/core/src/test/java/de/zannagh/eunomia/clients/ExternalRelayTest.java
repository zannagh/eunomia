package de.zannagh.eunomia.clients;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import de.zannagh.eunomia.common.ApiVersion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalRelayTest {

    private static final Gson GSON = new Gson();

    @Test
    void baseNormalizationAddsSchemeAndTrimsSlash() {
        assertEquals("http://host:8080", RelayEndpoints.base("host:8080"));
        assertEquals("http://host:8080", RelayEndpoints.base("http://host:8080/"));
        assertEquals("https://host", RelayEndpoints.base("https://host///"));
    }

    @Test
    void webSocketUriSwapsScheme() {
        assertEquals("ws://host:8080/ws?id=1", RelayEndpoints.ws("http://host:8080", "/ws?id=1").toString());
        assertEquals("wss://host/ws", RelayEndpoints.ws("https://host", "/ws").toString());
    }

    @Test
    void restPathsCarryTheApiVersionSegment() {
        String expectedVersion = ApiVersion.CURRENT;
        assertTrue(expectedVersion.matches("\\d+\\.\\d+"), "api version segment was " + expectedVersion);
        assertEquals(
                "http://host:8080/api/v" + expectedVersion + "/packets/keyed",
                RelayEndpoints.api("http://host:8080", "/packets/keyed").toString());
        assertEquals(
                "https://host/api/v" + expectedVersion + "/packets/plain",
                RelayEndpoints.api("https://host", "/packets/plain").toString());
    }

    @Test
    void healthStaysUnversioned() {
        // /health is the reachability probe that gates the whole HTTP fallback; the relay serves it
        // outside the versioned surface, so a version segment here would silently disable the fallback.
        assertEquals("http://host:8080/health", RelayEndpoints.http("http://host:8080", "/health").toString());
    }

    @Test
    void packetEnvelopeRoundTripsWithTheWireFieldNames() {
        JsonObject payload = new JsonObject();
        payload.addProperty("note", "hi");
        PacketEnvelope envelope = new PacketEnvelope(
                "mc.example:25565", "Example SMP", "eunomia:example_replicated", "uuid-1", true, "sender-uuid", payload);

        String json = GSON.toJson(envelope);
        // Field names the C# server binds against must be present verbatim.
        assertTrue(json.contains("\"scope\""));
        assertTrue(json.contains("\"name\""));
        assertTrue(json.contains("\"channel\""));
        assertTrue(json.contains("\"replicated\":true"));
        assertTrue(json.contains("\"sender\""));

        PacketEnvelope back = GSON.fromJson(json, PacketEnvelope.class);
        assertEquals("mc.example:25565", back.scope);
        assertEquals("Example SMP", back.name);
        assertEquals("eunomia:example_replicated", back.channel);
        assertEquals("uuid-1", back.key);
        assertTrue(back.replicated);
        assertEquals("hi", back.payload.getAsJsonObject().get("note").getAsString());
    }

    @Test
    void pingClientIsFalseForBlankAddress() {
        assertFalse(PingClient.isReachable(null));
        assertFalse(PingClient.isReachable("   "));
    }
}
