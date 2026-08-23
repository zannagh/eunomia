package de.zannagh.eunomia.compatibility.known;

import de.zannagh.eunomia.common.SemanticVersion;
import de.zannagh.eunomia.compatibility.CompatFlag;

import java.util.List;

/**
 * The {@link CompatFlag} describing LuckPerms' presence.
 *
 * <p><b>This class must never import or reference anything from {@code net.luckperms}.</b> It is the
 * class the presence probe itself loads - it is class-loaded on <em>every</em> server, including the
 * overwhelming majority that do not run LuckPerms. A single {@code net.luckperms.*} reference in this
 * file would make the JVM resolve that type while verifying the flag class and blow up with
 * {@link NoClassDefFoundError} exactly on the servers the soft dependency exists to protect.</p>
 *
 * <p>The actual API calls therefore live in {@link LuckPermsHook}, which is only ever touched from
 * inside a branch guarded by {@link de.zannagh.eunomia.compatibility.CompatManager#requiresCompatTo}
 * (see {@code ServerUtil#getPermissionLevelForPlayer}).</p>
 */
public class LuckPermsCompat implements CompatFlag {

    /**
     * The permission node that grants server-wide Eunomia administration.
     *
     * <p>Deliberately declared here rather than on {@link LuckPermsHook}: callers need the node name
     * for log/help output even when LuckPerms is absent, and reading it must not drag the API class
     * onto the classpath.</p>
     */
    public static final String ADMIN_PERMISSION = "eunomia.admin";

    /** The class the presence probe looks for; the LuckPerms API entry point. */
    public static final String PROBE_CLASS = "net.luckperms.api.LuckPerms";

    /**
     * The flag instance eunomia itself registers. Consumers may register this directly instead of
     * subclassing; {@link de.zannagh.eunomia.compatibility.CompatManager#requiresCompatTo(Class)}
     * matches any instance of this type, so both routes answer the same query.
     */
    public static final LuckPermsCompat INSTANCE = new LuckPermsCompat();

    /** The LuckPerms API version eunomia compiles and reasons against. */
    @Override
    public SemanticVersion since() {
        return new SemanticVersion(5, 4, 0, null);
    }

    @Override
    public List<String> classNames() {
        return List.of(PROBE_CLASS);
    }

    @Override
    public boolean needsInitialization() {
        return false;
    }

    @Override
    public List<CompatFlag> dependencies() {
        return List.of();
    }
}
