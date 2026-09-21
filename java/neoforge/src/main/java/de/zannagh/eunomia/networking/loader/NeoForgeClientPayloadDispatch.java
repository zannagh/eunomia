package de.zannagh.eunomia.networking.loader;

import de.zannagh.eunomia.client.networking.McClientContext;
import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.payloads.EunomiaPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/**
 * The client half of {@link NeoForgePayloadRegistration}'s dispatch, kept in its own class purely so the
 * client-only types it names ({@code Minecraft}, {@code ClientPacketListener}, {@code McClientContext})
 * are never resolved on a dedicated server. {@code NeoForgePayloadRegistration} itself is loaded on both
 * sides and reaches this class through an ordinary call, which the JVM resolves on first execution -
 * i.e. never, server-side.
 *
 * <p>Semantically identical to the {@code handleCustomPayload} inject in {@code ClientPacketListenerMixin}
 * (which is Fabric-only for exactly this reason): same context, same channel key, same manager call.</p>
 */
final class NeoForgeClientPayloadDispatch {

    private NeoForgeClientPayloadDispatch() {
    }

    static void dispatch(EunomiaPayload payload) {
        Minecraft client = Minecraft.getInstance();
        ClientPacketListener handler = client.getConnection();
        if (handler == null) {
            // Torn down between the packet arriving on the network thread and this running on the main
            // thread. Nothing to hand a handler; the client is already leaving the server.
            return;
        }
        CommunicationManager.dispatchClientbound(
                payload.type().id().toString(), payload.data(), new McClientContext(handler, client));
    }
}
