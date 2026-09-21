package de.zannagh.eunomia.networking.loader;

import de.zannagh.eunomia.Eunomia;
import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.packets.PacketType;
import de.zannagh.eunomia.networking.payloads.EunomiaPayload;
import de.zannagh.eunomia.networking.payloads.PayloadEntry;
import de.zannagh.eunomia.server.ServerHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

//? if < 1.21.8 {
/*import net.minecraft.network.protocol.PacketFlow;
*///?}

/**
 * The NeoForge counterpart of the custom-payload codec-injection mixins, and the reason those mixins
 * are Fabric-only (see {@code EunomiaMixinPlugin}).
 *
 * <p>WHY THIS EXISTS AT ALL: the mixins inject eunomia's per-channel codecs by {@code @ModifyArg} into
 * the call to {@code CustomPacketPayload.codec(FallbackProvider, List)} inside the vanilla payload
 * packets' {@code <clinit>}. NeoForge <em>patches that method</em> to four parameters
 * ({@code FallbackProvider, List, ConnectionProtocol, PacketFlow}); the two-argument overload the
 * injection points at simply does not exist in the patched jar. The injector therefore matches zero
 * instructions and - with mixin's default {@code require} of 0 - fails without a word, which is how
 * eunomia's entire wire transport came to be dead on NeoForge while every JVM test stayed green.</p>
 *
 * <p>NeoForge's answer to the same problem is {@link PayloadRegistrar}: you hand it the vanilla
 * {@code CustomPacketPayload.Type} + {@code StreamCodec} that {@link LoaderNetwork} already mints, plus
 * a handler, and it wires both the codec and the dispatch. So this class is a straight translation of
 * {@code LoaderNetwork}'s registry into NeoForge's registrar - the payloads, the codecs and the wire
 * format are byte-for-byte the same ones Fabric and the Paper plugin use.</p>
 *
 * <p>The registrar is {@link PayloadRegistrar#optional() optional} on purpose and this MUST NOT change:
 * eunomia deliberately talks to vanilla and non-eunomia clients (the capability handshake exists exactly
 * to detect them). A non-optional channel makes NeoForge refuse the connection of any client that does
 * not declare it, turning a library that degrades gracefully into one that kicks everybody.</p>
 */
public final class NeoForgePayloadRegistration {

    /**
     * The protocol version handed to {@code event.registrar(..)}. Bumping it makes NeoForge treat the
     * channels as incompatible with clients on the old value; eunomia's own compatibility story is the
     * capability handshake plus {@code PayloadDecodeGuard}, so this stays pinned.
     */
    private static final String PROTOCOL_VERSION = "1";

    /** Channel keys that made it into the registrar, so the late-registration warning can skip them. */
    private static final Set<String> REGISTERED_CHANNELS = ConcurrentHashMap.newKeySet();

    private NeoForgePayloadRegistration() {
    }

    /**
     * Registers every channel {@link LoaderNetwork} knows about with NeoForge's networking. Wired onto the
     * mod event bus from {@code EunomiaNeoForge}.
     *
     * <p>Timing is not a coincidence: NeoForge fires this from {@code NetworkRegistry.setup}, which
     * {@code CommonModLoader.load} runs <em>after</em> {@code FMLCommonSetupEvent},
     * {@code FMLClientSetupEvent} and {@code FMLLoadCompleteEvent}. Both eunomia entry points -
     * {@code Eunomia.init()} at mod construction and {@code EunomiaClient.init()} at client setup - have
     * therefore already declared their packets by the time we get here, as has any consumer mod that
     * registers from an ordinary initializer.</p>
     */
    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        // CAPTURE THE RETURN VALUE. optional() does NOT mutate the registrar - it returns a CLONE with the
        // flag set (PayloadRegistrar.optional(): `var clone = new PayloadRegistrar(this); clone.optional =
        // true; return clone;`, and versioned()/executesOn() behave the same way). Calling it for effect
        // and then registering on the original compiles cleanly and quietly registers every channel as
        // MANDATORY, at which point NeoForge disconnects every vanilla / non-eunomia client that joins.
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();

        Map<Identifier, PayloadEntry> serverbound = index(LoaderNetwork.serverboundEntries());
        Map<Identifier, PayloadEntry> clientbound = index(LoaderNetwork.clientboundEntries());

        Set<Identifier> channelIds = new LinkedHashSet<>(serverbound.keySet());
        channelIds.addAll(clientbound.keySet());

        for (Identifier id : channelIds) {
            boolean toServer = serverbound.containsKey(id);
            boolean toClient = clientbound.containsKey(id);
            // Either map holds the SAME PayloadEntry for a bidirectional channel - LoaderNetwork.onRegister
            // builds one entry per PacketType and files it under every direction the type allows.
            PayloadEntry entry = toServer ? serverbound.get(id) : clientbound.get(id);

            String direction;
            if (toServer && toClient) {
                registerBidirectional(registrar, entry);
                direction = "bidirectional";
            } else if (toServer) {
                registrar.playToServer(entry.type(), entry.codec(), NeoForgePayloadRegistration::handleServerbound);
                direction = "serverbound";
            } else {
                registrar.playToClient(entry.type(), entry.codec(), NeoForgePayloadRegistration::handleClientbound);
                direction = "clientbound";
            }

            REGISTERED_CHANNELS.add(id.toString());
            // The verification signal. Deliberately mirrors the Fabric-side "Injected S2C payload: {}" line
            // so a boot log can be grepped for proof that the transport is actually wired on this loader.
            Eunomia.LOGGER.info("Registered NeoForge payload: {} ({})", id, direction);
        }

        // From here on the registrar is closed: NetworkRegistry.register() throws
        // "Cannot register payload ... after registration phase." for anything later. CommunicationManager
        // on the other hand accepts a register() call at literally any time (LoaderNetwork.wrap even
        // registers on demand when a mod sends on an undeclared channel), so a late registration is
        // possible, silently useless, and has to be shouted about.
        LoaderNetwork.chainRegistrationListener(NeoForgePayloadRegistration::warnAboutLateRegistration);

        // ...and, crucially, has to be made HARMLESS. Warning about a late channel is not enough: the send
        // itself must not happen. NeoForge has no codec for an unregistered channel, so it encodes the
        // outgoing payload as a DiscardedPayload, and the cast blows up inside the netty encoder -
        //   EncoderException: Failed encoding custom payload eunomia:<channel>
        //     Caused by: ClassCastException: EunomiaPayload cannot be cast to DiscardedPayload
        // - which kills the connection. Strictly worse than the no-op this class was written to fix. The
        // guard makes the send a logged drop instead. Fabric never installs one.
        LoaderNetwork.setSendGuard(NeoForgePayloadRegistration::isWired);
    }

    /**
     * Whether {@code channelKey} actually made it into NeoForge's payload registry, i.e. whether a packet on
     * it can be put on the wire at all. Installed as {@link LoaderNetwork}'s send guard; see there for what a
     * {@code false} must cause (a dropped packet, never an attempted send).
     */
    public static boolean isWired(String channelKey) {
        return REGISTERED_CHANNELS.contains(channelKey);
    }

    /**
     * Registers a channel that carries traffic in both directions.
     *
     * <p>This is the one place where NeoForge's API is NOT uniform across the 1.21.1 - 26.3 range this
     * repo supports, verified against the {@code PayloadRegistrar} sources of every pinned NeoForge build
     * in {@code ~/.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge}:</p>
     * <ul>
     *   <li>NeoForge 21.1 - 21.4 (MC 1.21.1 - 1.21.4): {@code playBidirectional(type, codec, handler)} -
     *       ONE handler serving both directions, so it must branch on {@code context.flow()} itself.</li>
     *   <li>NeoForge 21.8+ (MC 1.21.5 - 26.3): the three-argument overload registers the SERVERBOUND
     *       handler only (the clientbound one is expected from {@code RegisterClientPayloadHandlersEvent}),
     *       and the four-argument {@code (type, codec, serverHandler, clientHandler)} overload is the one
     *       that wires both. Calling the three-argument form here would leave every clientbound packet
     *       unhandled - the exact failure mode this class exists to fix.</li>
     * </ul>
     */
    private static void registerBidirectional(PayloadRegistrar registrar, PayloadEntry entry) {
        //? if >= 1.21.8 {
        registrar.playBidirectional(
                entry.type(),
                entry.codec(),
                NeoForgePayloadRegistration::handleServerbound,
                NeoForgePayloadRegistration::handleClientbound);
        //?}
        //? if < 1.21.8 {
        /*registrar.playBidirectional(
                entry.type(),
                entry.codec(),
                NeoForgePayloadRegistration::handleEitherDirection);
        *///?}
    }

    //? if < 1.21.8 {
    /*/^*
     * The single handler the pre-21.8 {@code playBidirectional} takes, dispatching on the direction the
     * payload actually arrived from. {@code IPayloadContext.flow()} is documented as "the flow of the
     * received payload", so SERVERBOUND means the server received it from a client.
     ^/
    private static void handleEitherDirection(EunomiaPayload payload, IPayloadContext context) {
        if (context.flow() == PacketFlow.SERVERBOUND) {
            handleServerbound(payload, context);
        } else {
            handleClientbound(payload, context);
        }
    }
    *///?}

    /**
     * Serverbound dispatch, mirroring {@code ServerGamePacketListenerMixin} exactly: drop an undecodable
     * payload silently (the codec's {@code PayloadDecodeGuard} has already logged why, and throwing from
     * the network thread would disconnect the sender), otherwise hand the POJO to the
     * {@link CommunicationManager} on the main thread so handlers may touch world/server state.
     */
    private static void handleServerbound(EunomiaPayload payload, IPayloadContext context) {
        if (payload.data() == null) {
            return;
        }
        Player player = context.player();
        if (!(player instanceof ServerPlayer sender)) {
            // Should be unreachable: this handler is only ever registered for the PLAY phase on the
            // serverbound side. Bail rather than risk a ClassCastException on a network thread.
            return;
        }
        // ServerPlayer#server is private on 26.x, so take the server the same way McServerTransport does.
        MinecraftServer server = ServerHolder.get();
        if (server == null) {
            return;
        }
        String channelKey = payload.type().id().toString();
        // enqueueWork, not server.execute: it is NeoForge's own main-thread hop and is the documented way
        // to leave the netty thread from a payload handler.
        context.enqueueWork(() -> CommunicationManager.dispatchServerbound(
                channelKey, payload.data(), new McServerContext(sender, server)));
    }

    /**
     * Clientbound dispatch, mirroring {@code ClientPacketListenerMixin}. The actual work lives in
     * {@link NeoForgeClientPayloadDispatch} and is reached only through a plain method call: this class is
     * loaded on dedicated servers too, where {@code Minecraft}/{@code McClientContext} do not exist, and a
     * call site is resolved lazily on first execution whereas a method reference would resolve the class
     * when the handler is built.
     */
    private static void handleClientbound(EunomiaPayload payload, IPayloadContext context) {
        if (payload.data() == null) {
            return;
        }
        context.enqueueWork(() -> NeoForgeClientPayloadDispatch.dispatch(payload));
    }

    /**
     * Complains, loudly, about a channel declared after {@link #onRegisterPayloadHandlers} has run. Such a
     * channel is accepted by the {@link CommunicationManager} and will never reach the wire on NeoForge,
     * and this warning is the only trace it would otherwise leave.
     *
     * <p>Installing the listener re-plays every already-declared type into it, which is why the check is
     * against {@link #REGISTERED_CHANNELS} rather than a plain "we are past the event" flag.</p>
     */
    private static void warnAboutLateRegistration(PacketType<?> type) {
        if (REGISTERED_CHANNELS.contains(type.channelKey())) {
            return;
        }
        Eunomia.LOGGER.warn(
                "Packet channel {} was declared after NeoForge's payload registration phase closed, so it "
                        + "could not be wired into the network. Packets on it will be dropped (safely - see "
                        + "LoaderNetwork's send guard), but they will never be delivered. Fix: call "
                        + "CommunicationManager.register({}) - or onServerReceive/onClientReceive for it - "
                        + "from your mod's constructor or a setup event, i.e. before mod loading completes. "
                        + "Declaring a send-only channel up front is exactly what register(..) is for.",
                type.channelKey(), type.channelKey());
    }

    /** Indexes entries by their channel id. Insertion-ordered so the two maps are walked consistently. */
    private static Map<Identifier, PayloadEntry> index(Collection<PayloadEntry> entries) {
        Map<Identifier, PayloadEntry> byId = new LinkedHashMap<>();
        for (PayloadEntry entry : entries) {
            byId.put(entry.type().id(), entry);
        }
        return byId;
    }
}
