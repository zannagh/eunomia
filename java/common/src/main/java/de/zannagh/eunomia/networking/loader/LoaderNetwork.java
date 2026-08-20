package de.zannagh.eunomia.networking.loader;

import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.packets.PacketType;
import de.zannagh.eunomia.server.ServerConnectionEventConsumer;
import de.zannagh.eunomia.server.ServerHolder;
import de.zannagh.eunomia.server.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

//? if >= 1.20.5 {
import de.zannagh.eunomia.networking.payloads.EunomiaPayload;
import de.zannagh.eunomia.networking.payloads.PayloadEntry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
//?}
//? if < 1.20.5 {
/*import de.zannagh.eunomia.networking.packets.ServerContext;
import de.zannagh.eunomia.networking.packets.ClientContext;
import de.zannagh.eunomia.networking.serialization.PayloadCodec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

import java.util.concurrent.ConcurrentHashMap;
*///?}

/**
 * The loader-side adapter that turns Eunomia's platform-neutral packet registry into vanilla
 * networking. It installs itself as the {@link CommunicationManager}'s registration listener and
 * server transport, so mods only ever call the manager - never anything here.
 * <p>
 * On 1.20.5+ each declared {@link PacketType} becomes a {@link CustomPacketPayload.Type} + StreamCodec
 * that the payload-packet mixins inject into vanilla's codec lists. On 1.20.x (no
 * {@code CustomPacketPayload}) the same registry drives manual {@code FriendlyByteBuf} encode/decode
 * through the legacy mixins.
 */
public final class LoaderNetwork {

    //? if >= 1.20.5 {
    private static final ConcurrentHashMap<Identifier, PayloadEntry> SERVERBOUND = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Identifier, PayloadEntry> CLIENTBOUND = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, CustomPacketPayload.Type<EunomiaPayload>> TYPES_BY_KEY =
            new ConcurrentHashMap<>();
    //?}
    //? if < 1.20.5 {
    /*private static final ConcurrentHashMap<String, PacketType<?>> LEGACY = new ConcurrentHashMap<>();
    *///?}

    private static volatile boolean installed;

    private LoaderNetwork() {
    }

    /** Installs the registration listener and server transport. Idempotent; called from {@code Eunomia.init}. */
    public static void init() {
        if (installed) {
            return;
        }
        installed = true;
        CommunicationManager.setRegistrationListener(LoaderNetwork::onRegister);
        CommunicationManager.setServerTransport(new McServerTransport());
        ServerLifecycleEvents.register(new ServerConnectionEventConsumer() {
            @Override
            public void acceptStarting(MinecraftServer server) {
                ServerHolder.set(server);
            }

            @Override
            public void acceptStopping(MinecraftServer server) {
                ServerHolder.clear();
            }
        });
    }

    private static void onRegister(PacketType<?> type) {
        //? if >= 1.20.5 {
        CustomPacketPayload.Type<EunomiaPayload> payloadType = EunomiaPayload.typeFor(type);
        PayloadEntry entry = new PayloadEntry(payloadType, EunomiaPayload.codecFor(type, payloadType));
        if (type.direction().allowsServerbound()) {
            SERVERBOUND.put(payloadType.id(), entry);
        }
        if (type.direction().allowsClientbound()) {
            CLIENTBOUND.put(payloadType.id(), entry);
        }
        TYPES_BY_KEY.put(type.channelKey(), payloadType);
        //?}
        //? if < 1.20.5 {
        /*LEGACY.put(type.channelKey(), type);
        *///?}
    }

    // ── 1.20.5+ codec injection + send support ──────────────────────────────────────────────────
    //? if >= 1.20.5 {

    public static Collection<PayloadEntry> serverboundEntries() {
        return SERVERBOUND.values();
    }

    public static Collection<PayloadEntry> clientboundEntries() {
        return CLIENTBOUND.values();
    }

    public static boolean isServerboundId(Identifier id) {
        return SERVERBOUND.containsKey(id);
    }

    public static boolean isClientboundId(Identifier id) {
        return CLIENTBOUND.containsKey(id);
    }

    /**
     * The codec for a serverbound channel, or {@code null} if {@code id} is not a registered eunomia
     * serverbound channel. Used as the dynamic fallback in the custom-payload codec-injection mixins so
     * a channel registered <em>after</em> the vanilla packet class initialised is still resolved (the
     * static list snapshot only captures channels registered before {@code <clinit>}).
     */
    public static StreamCodec<? super ByteBuf, EunomiaPayload> serverboundCodec(Identifier id) {
        PayloadEntry entry = SERVERBOUND.get(id);
        return entry != null ? entry.codec() : null;
    }

    /** The codec for a clientbound channel, or {@code null} if unregistered. See {@link #serverboundCodec}. */
    public static StreamCodec<? super ByteBuf, EunomiaPayload> clientboundCodec(Identifier id) {
        PayloadEntry entry = CLIENTBOUND.get(id);
        return entry != null ? entry.codec() : null;
    }

    /** Wraps a POJO in the per-channel {@link EunomiaPayload}, registering the type on demand. */
    public static EunomiaPayload wrap(PacketType<?> type, Object data) {
        CustomPacketPayload.Type<EunomiaPayload> payloadType = TYPES_BY_KEY.get(type.channelKey());
        if (payloadType == null) {
            CommunicationManager.register(type);
            payloadType = TYPES_BY_KEY.get(type.channelKey());
        }
        return new EunomiaPayload(payloadType, data);
    }
    //?}

    // ── 1.20.x legacy manual encode/decode ──────────────────────────────────────────────────────
    //? if < 1.20.5 {
    /*public static PacketType<?> legacyType(Identifier channel) {
        return LEGACY.get(channel.toString());
    }

    public static boolean dispatchLegacyServerbound(Identifier channel, FriendlyByteBuf buf, ServerContext ctx) {
        PacketType<?> type = LEGACY.get(channel.toString());
        if (type == null || !type.direction().allowsServerbound()) {
            return false;
        }
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return CommunicationManager.dispatchServerbound(type.channelKey(), PayloadCodec.decode(bytes, type.payloadClass()), ctx);
    }

    public static boolean dispatchLegacyClientbound(Identifier channel, FriendlyByteBuf buf, ClientContext ctx) {
        PacketType<?> type = LEGACY.get(channel.toString());
        if (type == null || !type.direction().allowsClientbound()) {
            return false;
        }
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return CommunicationManager.dispatchClientbound(type.channelKey(), PayloadCodec.decode(bytes, type.payloadClass()), ctx);
    }
    *///?}
}
