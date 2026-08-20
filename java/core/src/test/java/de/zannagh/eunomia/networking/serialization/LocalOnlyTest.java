package de.zannagh.eunomia.networking.serialization;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.zannagh.eunomia.networking.packets.PacketDirection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalOnlyTest {

    /** A plain POJO (not a ConfigurationItem) - proves the exclusion strategy works on any payload. */
    static final class SamplePayload {
        String label;

        @LocalOnly
        String secretPath;

        SamplePayload() {
        }

        SamplePayload(String label, String secretPath) {
            this.label = label;
            this.secretPath = secretPath;
        }
    }

    private Gson localGson;
    private Gson networkGson;

    @BeforeEach
    void setUp() {
        localGson = new Gson();
        networkGson = new GsonBuilder().setExclusionStrategies(NetworkSerializer.localOnlyExclusion()).create();
        NetworkSerializer.setGson(networkGson);
    }

    @AfterEach
    void tearDown() {
        NetworkSerializer.setGson(new Gson());
    }

    @Test
    void networkGsonOmitsLocalOnlyFieldButKeepsTheRest() {
        String json = networkGson.toJson(new SamplePayload("visible", "/home/me/.secret"));

        assertTrue(json.contains("\"label\""));
        assertFalse(json.contains("secretPath"));
    }

    @Test
    void localGsonKeepsBothFields() {
        String json = localGson.toJson(new SamplePayload("visible", "/home/me/.secret"));

        assertTrue(json.contains("\"label\""));
        assertTrue(json.contains("\"secretPath\""));
    }

    @Test
    void payloadCodecRoundTripDropsTheLocalOnlyField() {
        byte[] encoded = PayloadCodec.encode(new SamplePayload("visible", "/home/me/.secret"), PacketDirection.CLIENTBOUND);
        SamplePayload decoded = PayloadCodec.decode(encoded, SamplePayload.class);

        assertEquals("visible", decoded.label);
        assertNull(decoded.secretPath, "local-only field must not survive the wire");
    }
}
