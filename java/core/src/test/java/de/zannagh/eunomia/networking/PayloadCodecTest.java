package de.zannagh.eunomia.networking;

import com.google.gson.Gson;
import de.zannagh.eunomia.networking.serialization.NetworkHealable;
import de.zannagh.eunomia.networking.serialization.NetworkSerializer;
import de.zannagh.eunomia.networking.serialization.PayloadCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayloadCodecTest {

    public static class Sample {
        public String name;
        public int count;

        public Sample() {
        }

        public Sample(String name, int count) {
            this.name = name;
            this.count = count;
        }
    }

    public static class HealingSample implements NetworkHealable {
        public int value;
        public transient boolean healed;

        @Override
        public void heal() {
            healed = true;
            if (value < 0) {
                value = 0;
            }
        }
    }

    @BeforeEach
    void resetSerializer() {
        NetworkSerializer.setGson(new Gson());
    }

    @Test
    void roundTripsAValue() {
        Sample original = new Sample("hello", 7);
        byte[] wire = PayloadCodec.encode(original, false);
        Sample back = PayloadCodec.decode(wire, Sample.class);
        assertEquals("hello", back.name);
        assertEquals(7, back.count);
    }

    @Test
    void compressesRepetitiveData() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            sb.append("armor");
        }
        Sample big = new Sample(sb.toString(), 1);
        byte[] wire = PayloadCodec.encode(big, false);
        // 25k of highly repetitive text must gzip far below its raw size.
        assertTrue(wire.length < 1000, "expected strong compression, got " + wire.length + " bytes");
        assertEquals(big.name, PayloadCodec.decode(wire, Sample.class).name);
    }

    @Test
    void healsAfterDecode() {
        HealingSample sample = new HealingSample();
        sample.value = -5;
        byte[] wire = PayloadCodec.encode(sample, false);
        HealingSample back = PayloadCodec.decode(wire, HealingSample.class);
        assertTrue(back.healed, "heal() should run after decode");
        assertEquals(0, back.value, "heal() should have clamped the negative value");
    }

    @Test
    void refusesOversizedServerboundPayload() {
        // High-entropy letters from an LCG (~4.7 bits/char) so gzip cannot crush it below the 32 KiB
        // serverbound ceiling; 300k chars leaves comfortable margin above it.
        StringBuilder sb = new StringBuilder();
        long seed = 0x9E3779B97F4A7C15L;
        for (int i = 0; i < 300_000; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            sb.append((char) ('a' + (int) ((seed >>> 33) % 26)));
        }
        Sample huge = new Sample(sb.toString(), 1);
        byte[] clientboundWire = PayloadCodec.encode(huge, false);
        // Sanity: it really did blow past the serverbound ceiling but fits the clientbound one.
        assertTrue(clientboundWire.length > PayloadCodec.MAX_SERVERBOUND_PAYLOAD_BYTES,
                "test data must exceed the serverbound ceiling, was " + clientboundWire.length);
        assertThrows(IllegalStateException.class, () -> PayloadCodec.encode(huge, true));
        assertEquals(huge.name, PayloadCodec.decode(clientboundWire, Sample.class).name);
    }
}
