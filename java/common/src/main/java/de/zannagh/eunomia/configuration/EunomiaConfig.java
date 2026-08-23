package de.zannagh.eunomia.configuration;

import de.zannagh.eunomia.common.SemanticVersion;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The client-side Eunomia configuration - the player's personal say over the external ("Cloud Sync") transport.
 * Three knobs: whether to fall back to the external relay (the C# server), that relay's address, and whether to
 * prefer the relay even when the joined Minecraft server speaks Eunomia.
 * <p>
 * <strong>Every knob is an override, not a value.</strong> The fields are boxed and default to {@code null},
 * which means "inherit" - the effective setting is then decided by {@link EunomiaSyncSettings} from the server's
 * advertised policy, the consuming mod's defaults and finally {@link EunomiaDefaults}. Only a non-null field is
 * a decision the player actually made, and only such a field outranks the server. Nothing here should be read
 * directly by feature code: go through the resolver, or a player who never opened the settings screen silently
 * beats the server that told the client where to sync.
 * <p>
 * Implements {@link ConfigurationItem} directly (not via {@link ConfigurationItemBase}), so it round-trips
 * through plain reflective Gson - the {@link ConfigurationItemSerializer} adapter only intercepts
 * {@code ConfigurationItemBase} subclasses.
 *
 * @since 0.1.0
 */
public class EunomiaConfig implements ConfigurationItem<EunomiaConfig> {

    /** The pre-override shape: two primitive knobs, no persisted schema marker. */
    public static final SemanticVersion SCHEMA_1_0_0 = new SemanticVersion(1, 0, 0, null);

    /** The override shape: three nullable knobs plus the persisted {@link #schemaVersion} marker. */
    public static final SemanticVersion SCHEMA_1_1_0 = new SemanticVersion(1, 1, 0, null);

    /**
     * Whether the external relay fallback is opted into, or {@code null} to inherit. Boxed on purpose - see the
     * class doc; {@code false} here is an explicit "no", not an absence.
     */
    public @Nullable Boolean enableExternalFallback;

    /** The relay address to use, or {@code null} (or blank) to inherit. */
    public @Nullable String externalServerAddress;

    /** Whether to prefer the relay over the in-game transport, or {@code null} to inherit. */
    public @Nullable Boolean preferExternalTransport;

    /**
     * Every relay host this client has already sent data through, lower-cased, in the order first seen.
     * <p>
     * Not a setting: nothing reads it to decide anything, and clearing it changes no behaviour beyond making
     * the "your data is now going somewhere new" notification fire once more. It is here rather than in a
     * separate file because it is per-player, must survive a restart, and is already covered by the config
     * provider's load/save/migrate plumbing.
     * <p>
     * Hosts, not URLs, on purpose: an operator who appends a path, changes the port or moves from
     * {@code http} to {@code https} has not moved anyone's data to a new party, and a notification that fired
     * on each of those would be nagging rather than informing.
     */
    public @Nullable List<String> seenRelayHosts;

    /**
     * The schema this document was written with, persisted so migrations can be detected at all.
     * <p>
     * Deliberately <em>not</em> initialised by the no-argument constructor: Gson instantiates through that
     * constructor and only then overwrites the keys the JSON actually contains, so a field initialiser would
     * stamp every 1.0.0 document as current and the migration would never run. Absent therefore means 1.0.0.
     * Programmatic constructors do stamp it, and so does {@link #migrateFrom}.
     */
    public @Nullable String schemaVersion;

    private transient boolean changed;

    /** Creates an all-inherit config. Reports schema 1.0.0 until stamped (see {@link #schemaVersion}). */
    public EunomiaConfig() {
    }

    /** Legacy two-knob constructor, kept source-compatible; both arguments become explicit overrides. */
    public EunomiaConfig(boolean enableExternalFallback, @Nullable String externalServerAddress) {
        this(Boolean.valueOf(enableExternalFallback), externalServerAddress, null);
    }

    public EunomiaConfig(
            @Nullable Boolean enableExternalFallback,
            @Nullable String externalServerAddress,
            @Nullable Boolean preferExternalTransport) {
        this.enableExternalFallback = enableExternalFallback;
        this.externalServerAddress = externalServerAddress;
        this.preferExternalTransport = preferExternalTransport;
        this.schemaVersion = SCHEMA_1_1_0.toString();
    }

    /** An all-inherit config already stamped with the current schema, so loading it needs no migration. */
    public static EunomiaConfig withCurrentSchema() {
        return new EunomiaConfig(null, null, null);
    }

    // ── Overrides (nullable = inherit) ──────────────────────────────────────────────────────────

    /** The player's explicit fallback decision, or {@code null} to inherit. */
    public @Nullable Boolean enableExternalFallbackOverride() {
        return enableExternalFallback;
    }

    /** The player's explicit relay address, or {@code null} to inherit. Blank is normalised to {@code null}. */
    public @Nullable String externalServerAddressOverride() {
        return externalServerAddress == null || externalServerAddress.isBlank() ? null : externalServerAddress;
    }

    /** The player's explicit "prefer the relay" decision, or {@code null} to inherit. */
    public @Nullable Boolean preferExternalTransportOverride() {
        return preferExternalTransport;
    }

    /** Sets or clears ({@code null}) the fallback override and marks the config dirty. */
    public void setEnableExternalFallback(@Nullable Boolean value) {
        this.enableExternalFallback = value;
        setHasChangedFromSerializedContent();
    }

    /** Sets or clears ({@code null}) the relay address override and marks the config dirty. */
    public void setExternalServerAddress(@Nullable String value) {
        this.externalServerAddress = value;
        setHasChangedFromSerializedContent();
    }

    /** Sets or clears ({@code null}) the "prefer the relay" override and marks the config dirty. */
    public void setPreferExternalTransport(@Nullable Boolean value) {
        this.preferExternalTransport = value;
        setHasChangedFromSerializedContent();
    }

    // ── Relay hosts already used (see {@link #seenRelayHosts}) ─────────────────────────────────

    /** Whether {@code host} has already been recorded as a relay this client sent data through. */
    public synchronized boolean hasSeenRelayHost(@Nullable String host) {
        String normalized = normalizeHost(host);
        return normalized != null && seenRelayHosts != null && seenRelayHosts.contains(normalized);
    }

    /**
     * Records {@code host} as a relay this client has now used, and reports whether that was news.
     * <p>
     * Test-and-set in one synchronized step on purpose. The caller is a diagnostic that must fire exactly
     * once per newly seen host, and it runs on whichever thread resolved the capability handshake - a
     * separate {@code hasSeen} then {@code remember} would let two joins racing through the same host each
     * see "new" and each raise a notification.
     *
     * @param host the relay host, in any case; {@code null} and blank are ignored.
     * @return {@code true} when this call was the first to record {@code host}.
     */
    public synchronized boolean rememberRelayHost(@Nullable String host) {
        String normalized = normalizeHost(host);
        if (normalized == null) {
            return false;
        }
        if (seenRelayHosts == null) {
            seenRelayHosts = new ArrayList<>();
        }
        if (seenRelayHosts.contains(normalized)) {
            return false;
        }
        seenRelayHosts.add(normalized);
        setHasChangedFromSerializedContent();
        return true;
    }

    /** Forgets every recorded relay host, so the next one is announced again. Tests, and a privacy reset. */
    public synchronized void forgetSeenRelayHosts() {
        if (seenRelayHosts != null && !seenRelayHosts.isEmpty()) {
            seenRelayHosts.clear();
            setHasChangedFromSerializedContent();
        }
    }

    private static @Nullable String normalizeHost(@Nullable String host) {
        if (host == null || host.isBlank()) {
            return null;
        }
        return host.trim().toLowerCase(Locale.ROOT);
    }

    // ── Legacy accessors (client-local view only) ───────────────────────────────────────────────

    /**
     * Whether the player explicitly opted into the relay. This is the override alone - an unset knob reads
     * {@code false} here even when the server advertises the relay. Prefer
     * {@link EunomiaSyncSettings#externalFallbackEnabled()}.
     */
    public boolean externalFallbackEnabled() {
        return Boolean.TRUE.equals(enableExternalFallback);
    }

    /** Whether the player configured a non-blank relay address of their own. */
    public boolean hasExternalServerAddress() {
        return externalServerAddressOverride() != null;
    }

    /** The player's relay address override verbatim (may be {@code null} or blank). */
    public @Nullable String externalServerAddress() {
        return externalServerAddress;
    }

    // ── ConfigurationItem ───────────────────────────────────────────────────────────────────────

    @Override
    public EunomiaConfig getValue() {
        return this;
    }

    @Override
    public void setValue(EunomiaConfig newValue) {
        this.enableExternalFallback = newValue.enableExternalFallback;
        this.externalServerAddress = newValue.externalServerAddress;
        this.preferExternalTransport = newValue.preferExternalTransport;
        this.seenRelayHosts = newValue.seenRelayHosts;
        this.schemaVersion = newValue.schemaVersion;
    }

    @Override
    public EunomiaConfig getDefaultValue() {
        return withCurrentSchema();
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
        SemanticVersion parsed = SemanticVersion.parse(schemaVersion);
        return parsed == null ? SCHEMA_1_0_0 : parsed;
    }

    @Override
    public SemanticVersion getCurrentSchemaVersion() {
        return SCHEMA_1_1_0;
    }

    /**
     * Maps a 1.0.0 document onto the override shape. The two legacy keys carry the same names and compatible
     * types, so Gson has already parsed them into the boxed fields; what migration decides is <em>intent</em>,
     * and 1.0.0 simply did not record any.
     * <p>
     * A recorded {@code true} survives as an explicit override: nothing in 1.0.0 switched Cloud Sync on by
     * itself, so a {@code true} can only have come from a player who went looking for the setting.
     * <p>
     * <strong>A recorded {@code false} migrates to "inherit" ({@code null}), not to a permanent "no".</strong>
     * {@code false} was the 1.0.0 <em>default</em> - it is what every config file said the moment it was
     * created, whether or not its owner had ever opened the settings screen. Treating that as a decision froze
     * every early adopter out of reach of their mod pack's and their server's defaults forever, which is the
     * opposite of what the four-rung chain exists for.
     * <p>
     * <strong>Be aware of what this can do:</strong> for a player whose 1.0.0 file said {@code false} and who
     * genuinely meant it, this migration can result in Cloud Sync switching <em>on</em> the next time they join
     * a server (or run a mod pack) whose defaults ask for it - that is, their mod data starting to leave their
     * machine without them touching anything. That is a deliberate trade, and the mitigation is that the same
     * join raises the "your data is now going to &lt;host&gt;" notification driven by {@link #seenRelayHosts},
     * naming the relay and pointing at the setting. A player who then still wants "no" sets it again, and this
     * time it is recorded as a real override that no server can outrank.
     * <p>
     * A blank address - which never carried information in 1.0.0 either, {@code hasExternalServerAddress()}
     * already treated it as absent - collapses to "inherit". The new {@code preferExternalTransport} knob has
     * no 1.0.0 counterpart and starts unset.
     */
    @Override
    public EunomiaConfig migrateFrom(EunomiaConfig old) {
        EunomiaConfig migrated = new EunomiaConfig(
                Boolean.TRUE.equals(old.enableExternalFallback) ? Boolean.TRUE : null,
                old.externalServerAddressOverride(),
                old.preferExternalTransport);
        migrated.seenRelayHosts = old.seenRelayHosts;
        return migrated;
    }
}
