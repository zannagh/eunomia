package de.zannagh.eunomia.paper;

import com.google.gson.Gson;
import de.zannagh.eunomia.networking.packets.PacketType;
import de.zannagh.eunomia.networking.examples.ExamplePackets;
import de.zannagh.eunomia.networking.examples.PermissionPayload;
import de.zannagh.eunomia.networking.examples.PingPayload;
import de.zannagh.eunomia.networking.examples.PongPayload;
import de.zannagh.eunomia.networking.serialization.NetworkSerializer;
import de.zannagh.eunomia.networking.serialization.PayloadCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic (no Bukkit, no server) contract test proving the Paper side speaks the same wire protocol
 * as the loaders: it reuses the same {@link ExamplePackets} channel keys and the same
 * {@link PayloadCodec} resolution. If a channel name or payload shape ever diverged, this reddens
 * without needing a running server.
 */
class PaperWireContractTest {

    // Bukkit's plugin-channel rule: lowercase "namespace:key". If a PacketType channelKey ever fails
    // this, registerOutgoingPluginChannel would throw at runtime.
    private static final Pattern BUKKIT_CHANNEL = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    @BeforeEach
    void setUp() {
        NetworkSerializer.setGson(new Gson());
    }

    @Test
    void exampleChannelsAreValidBukkitChannels() {
        for (PacketType<?> type : new PacketType[]{ExamplePackets.PING, ExamplePackets.PONG, ExamplePackets.PERMISSION}) {
            assertTrue(BUKKIT_CHANNEL.matcher(type.channelKey()).matches(),
                    type.channelKey() + " is not a valid Bukkit plugin-message channel");
        }
    }

    @Test
    void payloadsRoundTripThroughTheSharedCodec() {
        // Serverbound: exactly what the plugin's PaperMessageListener does on inbound.
        byte[] pingWire = PayloadCodec.encode(new PingPayload("hi", 5L), true);
        PingPayload ping = PayloadCodec.decode(pingWire, ExamplePackets.PING.payloadClass());
        assertEquals("hi", ping.message);
        assertEquals(5L, ping.sentAtMillis);

        // Clientbound: exactly what PaperServerTransport.send does on outbound.
        byte[] pongWire = PayloadCodec.encode(new PongPayload("hi", 5L, 9L), false);
        PongPayload pong = PayloadCodec.decode(pongWire, ExamplePackets.PONG.payloadClass());
        assertEquals("hi", pong.message);
        assertEquals(9L, pong.serverTimeMillis);

        byte[] permWire = PayloadCodec.encode(new PermissionPayload(4), false);
        assertEquals(4, PayloadCodec.decode(permWire, ExamplePackets.PERMISSION.payloadClass()).permissionLevel);
    }
}
