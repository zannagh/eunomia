package de.zannagh.eunomia.client;

import de.zannagh.eunomia.Eunomia;
import de.zannagh.eunomia.client.configuration.EunomiaClientConfiguration;
import de.zannagh.eunomia.client.examples.ExampleClientHandlers;
import de.zannagh.eunomia.client.gui.screens.EunomiaSettingsEntryPoint;
import de.zannagh.eunomia.client.networking.ClientConnectionEvents;
import de.zannagh.eunomia.client.networking.ClientTransportSelector;
import de.zannagh.eunomia.client.settings.ServerSettingsClient;
import de.zannagh.eunomia.client.toast.diagnostics.SyncDiagnosticToasts;
import de.zannagh.eunomia.networking.comms.CommunicationManager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Shared client-side entry point for the Eunomia library. The per-loader client initializers
 * delegate to {@link #init()}.
 */
public final class EunomiaClient {

    /** How long to wait for a HELLO_ACK before concluding the server does not run Eunomia. */
    private static final long HANDSHAKE_TIMEOUT_SECONDS = 5;

    private EunomiaClient() {
    }

    /**
     * Opens the fluent client-side configuration chain. A superset of {@code Eunomia.configure()}: it adds
     * the settings that need a client type, namely which {@code Screen} eunomia's entry button attaches to
     * and what it is labelled.
     *
     * <pre>{@code
     * EunomiaClient.configure()
     *         .settingsButton(MyModOptionsScreen.class)
     *         .settingsButtonLabel(Component.translatable("mymod.options.sync"))
     *         .toasts(false)
     *         .apply();
     * }</pre>
     *
     * <p>Order-independent with respect to {@link #init()} - the entry button reconciles its registration
     * against whatever the builder last said, so configuring after init moves or removes an already
     * installed button rather than being ignored.</p>
     *
     * @return a fresh, single-use client configuration builder.
     */
    public static EunomiaClientConfiguration configure() {
        return new EunomiaClientConfiguration();
    }

    public static void init() {
        // Install the client send path (Minecraft transport by default; the selector swaps to the external
        // relay after the probe resolves if the server lacks Eunomia and the fallback is opted in + reachable).
        ClientTransportSelector.init();
        ExampleClientHandlers.register();

        // Capability handshake: probe the server on join, and if no ACK arrives, conclude it does not
        // run Eunomia - the point where a consuming mod would offer a custom communications server.
        CommunicationManager.enableClientHandshake();
        // Diagnostic toast for "this server has no Eunomia": registered once, here, because the capability
        // view keeps its listeners across reconnects and fires them again on each new resolution - so a single
        // registration is exactly one evaluation per join. It is purely informational; the transport decision
        // is the selector's business above and is entirely unaffected by this listener.
        CommunicationManager.serverCapabilities().onResolved(SyncDiagnosticToasts::onCapabilitiesResolved);
        ClientConnectionEvents.registerJoin((handler, client) -> {
            CommunicationManager.beginServerProbe();
            CompletableFuture.delayedExecutor(HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .execute(CommunicationManager::markServerProbeTimedOut);
        });
        // eunomia's own entry button into its settings screen, registered once here rather than lazily from
        // the screen-init mixin. Honours the consumer's suppression flag and keeps honouring it afterwards:
        // this call also subscribes the entry point to EunomiaClientOptions, so a mod that suppresses or
        // moves the button after init still gets the right result.
        EunomiaSettingsEntryPoint.install();
        ClientConnectionEvents.registerDisconnect(client -> {
            ClientTransportSelector.onDisconnect();
            CommunicationManager.onClientDisconnect();
            // Per-connection too, and easy to forget because nothing breaks loudly without it: the admin
            // screen's cached view is the *previous* server's settings, so joining server B and opening the
            // panel would show server A's relay address. The controls stay disabled until B answers, so this
            // is never a privilege problem - it just tells the player the wrong destination for their data.
            // It also fails every still-pending callback as unavailable instead of leaving them hanging.
            ServerSettingsClient.reset();
        });

        Eunomia.LOGGER.info("Eunomia client initialized");
    }
}
