package de.zannagh.eunomia.networking.loader;

import de.zannagh.eunomia.Eunomia;
import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.packets.PacketType;
import de.zannagh.eunomia.server.ServerConnectionEventConsumer;
import de.zannagh.eunomia.server.ServerHolder;
import de.zannagh.eunomia.server.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

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
import de.zannagh.eunomia.networking.serialization.PayloadDecodeGuard;
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
 *
 * <p><strong>Registration timing differs by loader.</strong> On Fabric a channel may be declared at any
 * moment - the codec-injection mixins resolve unknown ids through a dynamic fallback, so registration order
 * is irrelevant. On NeoForge it is not: {@code NeoForgePayloadRegistration} hands the channels to a
 * {@code PayloadRegistrar} that closes when mod loading ends, and anything declared later is unsendable.
 * {@link #setSendGuard} exists so that case degrades to a logged drop instead of a thrown encoder. The
 * contract consumer mods have to honour is on {@link CommunicationManager#register}.</p>
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

    /**
     * Loader veto on outbound sends, or {@code null} when the loader has no such notion.
     *
     * <p>Fabric never sets this: its transport is vanilla's own custom-payload packet, whose codec list the
     * codec-injection mixins extend at any time, so every declared channel is always sendable. NeoForge does
     * set it, because {@code PayloadRegistrar} closes after mod loading and a send on a channel that missed
     * that window is NOT a no-op there - NeoForge falls back to {@code DiscardedPayload} and the encoder dies
     * with a {@code ClassCastException} on the netty thread, taking the connection with it.</p>
     */
    private static volatile Predicate<String> sendGuard;

    /** Channels already reported as unsendable, so a per-tick channel logs once rather than once per send. */
    private static final Set<String> UNSENDABLE_REPORTED = ConcurrentHashMap.newKeySet();

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
                // Drop every player's clientbound gate state. Disconnects normally do this one at a time,
                // but a single-player world stops the integrated server without necessarily running that
                // path for everyone, and the next world must not inherit the previous one's answers.
                CommunicationManager.onServerStopping();
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

    /**
     * Re-installs this class' registration listener with {@code after} chained behind it, so a loader can
     * observe every channel declaration without displacing the codec bookkeeping above.
     * {@link CommunicationManager#setRegistrationListener} keeps exactly one listener, so a loader that
     * simply set its own would silently break {@code wrap} - hence this explicit chain.
     *
     * <p>Installing a listener replays every already-declared {@link PacketType} into it, so {@code after}
     * sees the existing channels once before it sees any new one; anything that must distinguish "new" from
     * "replayed" has to track that itself.</p>
     *
     * <p>Used by the NeoForge loader, whose payload registration closes at a fixed point in mod loading and
     * which therefore has to warn about declarations that arrive too late to be honoured.</p>
     */
    public static void chainRegistrationListener(Consumer<PacketType<?>> after) {
        CommunicationManager.setRegistrationListener(type -> {
            onRegister(type);
            after.accept(type);
        });
    }

    /**
     * Installs the loader's outbound veto - see {@link #sendGuard}. Called once by the loader that needs one;
     * a loader that does not simply never calls this, and {@link #canSend} then always says yes.
     *
     * @param guard answers "is this {@code channelKey} actually wired into the loader's networking?".
     */
    public static void setSendGuard(Predicate<String> guard) {
        sendGuard = guard;
    }

    /**
     * Whether {@code type} may be handed to the network right now. A {@code false} means DROP THE PACKET -
     * never "try anyway": on NeoForge the throw that follows an unwired send happens inside the netty
     * encoder, which kills the connection rather than the send.
     *
     * <p>Warns once per channel. Deliberately not once per send: a store that relays on every tick would
     * otherwise bury the log, and the second line says nothing the first did not.</p>
     */
    public static boolean canSend(PacketType<?> type) {
        Predicate<String> guard = sendGuard;
        if (guard == null) {
            return true;
        }
        String channelKey = type.channelKey();
        if (guard.test(channelKey)) {
            return true;
        }
        if (UNSENDABLE_REPORTED.add(channelKey)) {
            Eunomia.LOGGER.warn(
                    "Dropping packets on channel {}: it is not registered with the mod loader's networking, "
                            + "which on NeoForge means it was declared after the payload registration phase "
                            + "closed. Declare it with CommunicationManager.register(..) during mod init - "
                            + "before mod loading completes - and it will be sendable. Further packets on this "
                            + "channel are dropped silently.", channelKey);
        }
        return false;
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
        Object payload = PayloadDecodeGuard.decodeOrDrop(bytes, type.payloadClass(), type.channelKey());
        if (payload == null) {
            // Undecodable bytes on a channel we own (an older, differently-framed version on the other
            // end). Report it as consumed so vanilla does not also complain, but dispatch nothing - the
            // guard has logged it, and throwing from this network thread would drop the connection.
            return true;
        }
        return CommunicationManager.dispatchServerbound(type.channelKey(), payload, ctx);
    }

    public static boolean dispatchLegacyClientbound(Identifier channel, FriendlyByteBuf buf, ClientContext ctx) {
        PacketType<?> type = LEGACY.get(channel.toString());
        if (type == null || !type.direction().allowsClientbound()) {
            return false;
        }
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        Object payload = PayloadDecodeGuard.decodeOrDrop(bytes, type.payloadClass(), type.channelKey());
        if (payload == null) {
            // Undecodable bytes on a channel we own (an older, differently-framed version on the other
            // end). Report it as consumed so vanilla does not also complain, but dispatch nothing - the
            // guard has logged it, and throwing from this network thread would drop the connection.
            return true;
        }
        return CommunicationManager.dispatchClientbound(type.channelKey(), payload, ctx);
    }
    *///?}
}
