package de.zannagh.eunomia.configuration;

import org.jspecify.annotations.Nullable;

/**
 * The fluent, side-agnostic configuration surface for a mod embedding eunomia. Obtained from
 * {@code Eunomia.configure()}, chained, and committed with {@link #apply()}:
 *
 * <pre>{@code
 * Eunomia.configure()
 *         .externalFallback(true)
 *         .externalServerAddress("https://sync.mymod.example")
 *         .toasts(false)
 *         .apply();
 * }</pre>
 *
 * <p><b>Nothing happens until {@link #apply()} is called.</b> The setters only stage values, so a chain that
 * is built and dropped changes nothing. Calling a setter after {@code apply()} throws rather than silently
 * doing nothing, which is the one misuse this shape is prone to.</p>
 *
 * <p><b>When to call it.</b> Before or after {@code Eunomia.init()} - it does not matter. Every value this
 * builder writes lands in a holder that is read at the point of use ({@link EunomiaSyncDefaults} at settings
 * resolution, {@link EunomiaClientOptions} when a toast is raised or when the button registration is
 * reconciled), never snapshotted at init time. The natural place is still your mod initializer, so the values
 * are in place before the player can reach a screen or join a server.</p>
 *
 * <p><b>Client-only settings.</b> The two presentation switches here ({@link #toasts(boolean)} and
 * {@link #settingsButton(boolean)}) are deliberately expressed as plain booleans, because this class lives in
 * the common source set and a dedicated server must never class-load a {@code net.minecraft.client.*} type.
 * Anything needing a client type - nominating a different target {@code Screen} for the settings button, or
 * relabelling it with a {@code Component} - lives on the client-side builder obtained from
 * {@code EunomiaClient.configure()} instead.</p>
 *
 * @since 0.3.0
 */
public final class EunomiaConfiguration {

    private @Nullable Boolean externalFallback;

    private @Nullable String externalServerAddress;

    private @Nullable Boolean preferExternalTransport;

    private boolean externalFallbackSet;

    private boolean externalServerAddressSet;

    private boolean preferExternalTransportSet;

    private @Nullable Boolean toasts;

    private @Nullable Boolean settingsButton;

    private boolean clearSyncDefaults;

    private boolean applied;

    /** Use {@code Eunomia.configure()}. */
    public EunomiaConfiguration() {
    }

    /**
     * Your mod's default for the external ("Cloud Sync") relay opt-in - rung three of the precedence chain,
     * so the player's own override and the joined server's advertised policy both still win over it.
     * @param value the default to ship, or {@code null} to clear a previously expressed opinion.
     * @return this builder.
     */
    public EunomiaConfiguration externalFallback(@Nullable Boolean value) {
        checkNotApplied();
        externalFallbackSet = true;
        externalFallback = value;
        return this;
    }

    /**
     * Your mod's default relay address. Only ever contacted when the fallback is enabled somewhere in the
     * chain; leaving it unset means eunomia's own {@code https://eunomia.zannagh.me}.
     * @param value the relay base address, or {@code null}/blank to clear a previously expressed opinion.
     * @return this builder.
     */
    public EunomiaConfiguration externalServerAddress(@Nullable String value) {
        checkNotApplied();
        externalServerAddressSet = true;
        externalServerAddress = value;
        return this;
    }

    /**
     * Your mod's default for preferring the relay even when the joined Minecraft server speaks eunomia.
     * @param value the default to ship, or {@code null} to clear a previously expressed opinion.
     * @return this builder.
     */
    public EunomiaConfiguration preferExternalTransport(@Nullable Boolean value) {
        checkNotApplied();
        preferExternalTransportSet = true;
        preferExternalTransport = value;
        return this;
    }

    /**
     * Drops every mod-level sync opinion on {@link #apply()}, before this chain's own values are written.
     * Chain it first when you want the mod defaults to be exactly what this chain says and nothing else.
     * @return this builder.
     */
    public EunomiaConfiguration resetSyncDefaults() {
        checkNotApplied();
        clearSyncDefaults = true;
        return this;
    }

    /**
     * Whether eunomia may raise toasts. Covers every toast raised through eunomia's toast API, its own
     * included. Read at the moment a toast is raised, so this works before or after client init.
     * @param enabled {@code false} to suppress eunomia's toasts wholesale.
     * @return this builder.
     */
    public EunomiaConfiguration toasts(boolean enabled) {
        checkNotApplied();
        toasts = enabled;
        return this;
    }

    /**
     * Whether eunomia installs its own settings button. Pass {@code false} when your mod places its own
     * entry point into eunomia's settings screen and does not want a second button. To keep the button but
     * move or rename it, use {@code EunomiaClient.configure()} instead.
     * @param enabled {@code false} to suppress the built-in button.
     * @return this builder.
     */
    public EunomiaConfiguration settingsButton(boolean enabled) {
        checkNotApplied();
        settingsButton = enabled;
        return this;
    }

    /**
     * Commits every staged value. Idempotent in effect but single-shot by contract: this builder is spent
     * afterwards, and any further setter call throws.
     */
    public void apply() {
        checkNotApplied();
        applied = true;
        if (clearSyncDefaults) {
            EunomiaSyncDefaults.reset();
        }
        if (externalFallbackSet) {
            EunomiaSyncDefaults.setEnableExternalFallback(externalFallback);
        }
        if (externalServerAddressSet) {
            EunomiaSyncDefaults.setExternalServerAddress(externalServerAddress);
        }
        if (preferExternalTransportSet) {
            EunomiaSyncDefaults.setPreferExternalTransport(preferExternalTransport);
        }
        if (toasts != null) {
            EunomiaClientOptions.setToastsEnabled(toasts);
        }
        if (settingsButton != null) {
            EunomiaClientOptions.setSettingsButtonEnabled(settingsButton);
        }
    }

    private void checkNotApplied() {
        if (applied) {
            throw new IllegalStateException(
                    "This Eunomia configuration was already applied. Start a new chain with Eunomia.configure().");
        }
    }
}
