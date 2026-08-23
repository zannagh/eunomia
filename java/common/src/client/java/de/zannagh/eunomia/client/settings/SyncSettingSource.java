package de.zannagh.eunomia.client.settings;

import de.zannagh.eunomia.Eunomia;
import de.zannagh.eunomia.configuration.EunomiaConfig;
import de.zannagh.eunomia.configuration.EunomiaSyncDefaults;
import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.handshake.ServerSyncPolicy;
import net.minecraft.network.chat.Component;

/**
 * Which rung of the four-level precedence chain an effective sync setting is currently coming from.
 *
 * <p>{@code EunomiaSyncSettings} answers "what is the value"; this answers "and who decided it". The
 * layered design is otherwise entirely invisible in a UI: a toggle showing OFF looks identical whether
 * the player switched it off, the server asked for it off, or nobody ever expressed an opinion - and
 * those three are very different things, because only the first survives joining a different server.
 *
 * <p>The chain is re-walked here rather than exposed by the resolver, because the resolver returns
 * values, not provenance. It reads the same three sources in the same order. The one deliberate
 * simplification is the server rung: the resolver allows its policy source to be re-bound, and there
 * is no way to read that binding back, so this reads the live capability view - which is exactly what
 * the resolver's default binding uses, and is what a player on a real connection sees.
 */
public enum SyncSettingSource {

    /** The player set this explicitly on the settings screen; it outranks everything, on every server. */
    PLAYER("player"),

    /** The joined server advertised it in the capability handshake; it follows the player around. */
    SERVER("server"),

    /** The mod or modpack embedding eunomia shipped this default. */
    MOD("mod"),

    /** Nobody expressed an opinion, so the framework default applies. */
    FRAMEWORK("framework");

    private final String key;

    SyncSettingSource(String key) {
        this.key = key;
    }

    /** Whether this is a decision the player actually made (as opposed to an inherited value). */
    public boolean isPlayerOverride() {
        return this == PLAYER;
    }

    /** The short, human-readable label shown next to the effective value. */
    public Component label() {
        return Component.translatable("eunomia.settings.source." + key);
    }

    /** Where the effective "Cloud Sync enabled" value is coming from right now. */
    public static SyncSettingSource forExternalFallback() {
        if (config().enableExternalFallbackOverride() != null) {
            return PLAYER;
        }
        if (policy().enableExternalFallback() != null) {
            return SERVER;
        }
        return EunomiaSyncDefaults.enableExternalFallback() != null ? MOD : FRAMEWORK;
    }

    /** Where the effective relay address is coming from right now. */
    public static SyncSettingSource forExternalServerAddress() {
        if (config().externalServerAddressOverride() != null) {
            return PLAYER;
        }
        if (policy().externalServerAddress() != null) {
            return SERVER;
        }
        return EunomiaSyncDefaults.externalServerAddress() != null ? MOD : FRAMEWORK;
    }

    /** Where the effective "prefer the relay" value is coming from right now. */
    public static SyncSettingSource forPreferExternalTransport() {
        if (config().preferExternalTransportOverride() != null) {
            return PLAYER;
        }
        if (policy().preferExternalTransport() != null) {
            return SERVER;
        }
        return EunomiaSyncDefaults.preferExternalTransport() != null ? MOD : FRAMEWORK;
    }

    private static EunomiaConfig config() {
        return Eunomia.getConfig();
    }

    private static ServerSyncPolicy policy() {
        try {
            ServerSyncPolicy advertised = CommunicationManager.serverCapabilities().syncPolicy();
            return advertised == null ? ServerSyncPolicy.UNKNOWN : advertised;
        } catch (Exception e) {
            // Provenance is decoration: a half-initialised capability view must never take a screen down.
            return ServerSyncPolicy.UNKNOWN;
        }
    }
}
