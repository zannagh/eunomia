package de.zannagh.eunomia.configuration;

import de.zannagh.eunomia.common.SemanticVersion;

/**
 * The client-side Eunomia configuration. Two knobs, both opt-in: whether to fall back to the external relay (the
 * C# server) when the joined Minecraft server does not run Eunomia, and that relay's address. The fallback never
 * engages unless {@link #enableExternalFallback} is {@code true} - a player who does not want their data leaving
 * for a third-party server simply leaves it off.
 * <p>
 * Implements {@link ConfigurationItem} directly (not via {@link ConfigurationItemBase}), so it round-trips through
 * plain reflective Gson - the {@link ConfigurationItemSerializer} adapter only intercepts {@code ConfigurationItemBase}
 * subclasses.
 *
 * @since 0.1.0
 */
public class EunomiaConfig implements ConfigurationItem<EunomiaConfig> {

    private static final SemanticVersion VERSION = new SemanticVersion(1, 0, 0, null);

    public boolean enableExternalFallback = false;

    public String externalServerAddress = "";

    private transient boolean changed;

    public EunomiaConfig() {
    }

    public EunomiaConfig(boolean enableExternalFallback, String externalServerAddress) {
        this.enableExternalFallback = enableExternalFallback;
        this.externalServerAddress = externalServerAddress;
    }

    /** Whether the external relay fallback is opted into. */
    public boolean externalFallbackEnabled() {
        return enableExternalFallback;
    }

    /** Whether a non-blank relay address is configured. */
    public boolean hasExternalServerAddress() {
        return externalServerAddress != null && !externalServerAddress.isBlank();
    }

    public String externalServerAddress() {
        return externalServerAddress;
    }

    @Override
    public EunomiaConfig getValue() {
        return this;
    }

    @Override
    public void setValue(EunomiaConfig newValue) {
        this.enableExternalFallback = newValue.enableExternalFallback;
        this.externalServerAddress = newValue.externalServerAddress;
    }

    @Override
    public EunomiaConfig getDefaultValue() {
        return new EunomiaConfig();
    }

    @Override
    public boolean hasChangedFromSerializedContent() {
        return changed;
    }

    @Override
    public void setHasChangedFromSerializedContent() {
        this.changed = true;
    }

    @Override
    public SemanticVersion getSchemaVersion() {
        return VERSION;
    }

    @Override
    public SemanticVersion getCurrentSchemaVersion() {
        return VERSION;
    }

    @Override
    public EunomiaConfig migrateFrom(EunomiaConfig old) {
        return old;
    }
}
