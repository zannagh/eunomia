package de.zannagh.eunomia.configuration;

import de.zannagh.eunomia.common.SemanticVersion;
import de.zannagh.eunomia.networking.handshake.ServerSyncPolicy;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * The server-side Eunomia configuration, persisted to {@code config/eunomia-server.json}. It is the operator's
 * say over the external ("Cloud Sync") transport for everyone who joins: whether the relay may be used at all,
 * which relay, and whether clients should be steered onto it even though this server speaks Eunomia natively
 * (useful when the relay, not the game server, owns the durable state).
 * <p>
 * <strong>Every knob is an opinion, not a value.</strong> The fields are boxed and default to {@code null},
 * which means "this operator never said anything about it". That distinction is the whole point of the class:
 * whatever the server does express is advertised to clients in the capability handshake as a
 * {@link ServerSyncPolicy} and lands at rung two of the precedence chain - above the consuming mod's defaults
 * ({@link EunomiaSyncDefaults}) and the framework defaults ({@link EunomiaDefaults}), below anything the player
 * explicitly chose. A server that cannot say "no opinion" therefore <em>erases</em> rung three for every client
 * that joins it: before this shape existed, an untouched {@code eunomia-server.json} advertised
 * {@code fallback=false} and the framework relay address, so a mod that had shipped
 * {@code Eunomia.configure().externalFallback(true).externalServerAddress(...)} had both of its defaults
 * silently replaced on every Eunomia server in the world. Rungs one to three are all "no opinion by default",
 * and this class is rung two's half of that contract.
 * <p>
 * The accessors come in two flavours and the difference matters. The {@code ...Opinion()} accessors return the
 * raw nullable field and are what {@link #toSyncPolicy()} advertises. The older effective accessors
 * ({@link #externalFallbackEnabled()}, {@link #externalServerAddress()},
 * {@link #preferExternalTransport()}) fold an absent opinion onto the framework default, because the operator
 * screen and the admin settings exchange need a concrete value to render and there is no third state to render
 * with. Never route the handshake through those.
 * <p>
 * It lives in the Minecraft-free {@code :core} rather than next to the client config in {@code :common} because
 * the Bukkit-family plugin ({@code :paper}) has to advertise the very same policy and does not depend on
 * {@code :common}. Everything this class needs - {@link ConfigurationItem}, {@link EunomiaDefaults},
 * {@link FileConfigurationProvider} - already lives here, so a single shape serves both server implementations
 * instead of two that would drift apart.
 *
 * @since 0.3.0
 */
public class EunomiaServerConfig implements ConfigurationItem<EunomiaServerConfig> {

    /** The pre-opinion shape: three primitive knobs materialised onto the framework defaults on first write. */
    public static final SemanticVersion SCHEMA_1_0_0 = new SemanticVersion(1, 0, 0, null);

    /** The opinion shape: three nullable knobs, where {@code null} means "the operator said nothing". */
    public static final SemanticVersion SCHEMA_1_1_0 = new SemanticVersion(1, 1, 0, null);

    /** Whether this server permits the external relay at all, or {@code null} to advertise no opinion. */
    public @Nullable Boolean enableExternalFallback;

    /**
     * The relay this server points its clients at, or {@code null} to advertise no opinion. Blank is a
     * distinct, deliberate state: it is what an administrator who cleared the field wrote, and it advertises
     * "this server points at no relay" rather than "decide it yourself".
     */
    public @Nullable String externalServerAddress;

    /** Whether clients should route through the relay anyway, or {@code null} to advertise no opinion. */
    public @Nullable Boolean preferExternalTransport;

    /**
     * The schema this document was written with, persisted so migrations can be detected at all.
     * <p>
     * Deliberately <em>not</em> initialised by the no-argument constructor, exactly as on the client config:
     * Gson instantiates through that constructor and only then overwrites the keys the JSON actually contains,
     * so a field initialiser would stamp every 1.0.0 document as current and the migration would never run.
     * Absent therefore means 1.0.0.
     */
    public @Nullable String schemaVersion;

    private transient boolean changed;

    /** Creates an all-inherit config. Reports schema 1.0.0 until stamped (see {@link #schemaVersion}). */
    public EunomiaServerConfig() {
    }

    /**
     * The operator-facing constructor: every argument becomes an explicit opinion this server advertises. It
     * is what the administrative settings exchange builds from a submitted write, which is why a write is
     * still a decision even when it happens to submit the framework defaults - an admin who deliberately
     * switched Cloud Sync off for their server means it, and clients must be told.
     */
    public EunomiaServerConfig(
            boolean enableExternalFallback,
            @Nullable String externalServerAddress,
            boolean preferExternalTransport) {
        this(Boolean.valueOf(enableExternalFallback), externalServerAddress,
                Boolean.valueOf(preferExternalTransport));
    }

    /** The opinion constructor: {@code null} for any knob the operator has not expressed. */
    public EunomiaServerConfig(
            @Nullable Boolean enableExternalFallback,
            @Nullable String externalServerAddress,
            @Nullable Boolean preferExternalTransport) {
        this.enableExternalFallback = enableExternalFallback;
        this.externalServerAddress = externalServerAddress;
        this.preferExternalTransport = preferExternalTransport;
        this.schemaVersion = SCHEMA_1_1_0.toString();
    }

    /** An all-opinion-free config already stamped with the current schema, so loading it needs no migration. */
    public static EunomiaServerConfig untouched() {
        return new EunomiaServerConfig(null, null, null);
    }

    // ── Opinions (nullable = the operator said nothing) ─────────────────────────────────────────

    /** The operator's explicit fallback decision, or {@code null} when they never expressed one. */
    public @Nullable Boolean externalFallbackOpinion() {
        return enableExternalFallback;
    }

    /** The operator's explicit relay address, or {@code null} when they never expressed one. */
    public @Nullable String externalServerAddressOpinion() {
        return externalServerAddress;
    }

    /** The operator's explicit "prefer the relay" decision, or {@code null} when they never expressed one. */
    public @Nullable Boolean preferExternalTransportOpinion() {
        return preferExternalTransport;
    }

    // ── Effective view (for rendering and for the admin exchange) ───────────────────────────────

    /** Whether the external relay fallback is enabled server-side, folding "no opinion" onto the default. */
    public boolean externalFallbackEnabled() {
        return enableExternalFallback == null
                ? EunomiaDefaults.DEFAULT_ENABLE_EXTERNAL_FALLBACK
                : enableExternalFallback;
    }

    /** Whether a non-blank relay address results from the effective view. */
    public boolean hasExternalServerAddress() {
        String address = externalServerAddress();
        return address != null && !address.isBlank();
    }

    /**
     * The relay address to show an operator. An absent opinion reads as the framework address, because that
     * is what a client with nothing else to go on would end up dialling; an explicit blank stays blank,
     * because that is an administrator having deliberately cleared the field.
     */
    public @Nullable String externalServerAddress() {
        return externalServerAddress == null
                ? EunomiaDefaults.DEFAULT_EXTERNAL_SERVER_ADDRESS
                : externalServerAddress;
    }

    /** Whether this server wants clients on the relay, folding "no opinion" onto the default. */
    public boolean preferExternalTransport() {
        return preferExternalTransport == null
                ? EunomiaDefaults.DEFAULT_PREFER_EXTERNAL_TRANSPORT
                : preferExternalTransport;
    }

    /**
     * This config as the policy advertised over the handshake - the raw opinions, never the effective view.
     * An untouched server therefore advertises {@link ServerSyncPolicy#UNKNOWN} and is indistinguishable from
     * a protocol-1 server, which is exactly right: it has nothing to say, so the consuming mod's defaults and
     * then the framework defaults decide. A blank address is dropped to {@code null} by
     * {@link ServerSyncPolicy} itself, so an operator who cleared the address advertises "no address" rather
     * than an empty one that would later fail a reachability probe.
     */
    public ServerSyncPolicy toSyncPolicy() {
        return new ServerSyncPolicy(enableExternalFallback, externalServerAddress, preferExternalTransport);
    }

    @Override
    public EunomiaServerConfig getValue() {
        return this;
    }

    @Override
    public void setValue(EunomiaServerConfig newValue) {
        this.enableExternalFallback = newValue.enableExternalFallback;
        this.externalServerAddress = newValue.externalServerAddress;
        this.preferExternalTransport = newValue.preferExternalTransport;
        this.schemaVersion = newValue.schemaVersion;
    }

    @Override
    public EunomiaServerConfig getDefaultValue() {
        return untouched();
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
     * Maps a 1.0.0 document onto the opinion shape. 1.0.0 materialised all three keys onto the framework
     * defaults the moment the file was created, so an operator who never touched the file still has a document
     * that <em>looks</em> like a full set of decisions. Reading that literally is precisely the bug this schema
     * exists to fix, so a value that equals the framework default collapses to "no opinion" and only a value
     * that differs survives as one.
     * <p>
     * That is lossy in exactly one direction and knowingly so: an operator who had deliberately set
     * {@code enableExternalFallback: false} on 1.0.0 is indistinguishable from one who never opened the file,
     * because 1.0.0 had no way to record the difference. Guessing "decision" there would keep rung three dead
     * for everyone; guessing "no opinion" hands the choice to the consuming mod, which is the rung the operator
     * would have had to override deliberately anyway. A blank address - which never carried information in
     * 1.0.0 either - also collapses to "no opinion".
     */
    @Override
    public EunomiaServerConfig migrateFrom(EunomiaServerConfig old) {
        return new EunomiaServerConfig(
                opinionOrNone(old.enableExternalFallback, EunomiaDefaults.DEFAULT_ENABLE_EXTERNAL_FALLBACK),
                addressOpinionOrNone(old.externalServerAddress),
                opinionOrNone(old.preferExternalTransport, EunomiaDefaults.DEFAULT_PREFER_EXTERNAL_TRANSPORT));
    }

    /** A legacy boolean that merely repeats the framework default carries no information; drop it. */
    private static @Nullable Boolean opinionOrNone(@Nullable Boolean value, boolean frameworkDefault) {
        if (value == null || value.booleanValue() == frameworkDefault) {
            return null;
        }
        return value;
    }

    /** A legacy address that is blank, or that merely repeats the framework relay, carries no information. */
    private static @Nullable String addressOpinionOrNone(@Nullable String value) {
        if (value == null || value.isBlank()
                || Objects.equals(value.trim(), EunomiaDefaults.DEFAULT_EXTERNAL_SERVER_ADDRESS)) {
            return null;
        }
        return value;
    }
}
