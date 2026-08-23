package de.zannagh.eunomia.compatibility.known;

import de.zannagh.eunomia.common.SemanticVersion;
import de.zannagh.eunomia.compatibility.CompatManager;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the mixin-safe metadata {@link LuckPermsCompat} exposes, and - most importantly - proves the
 * soft dependency actually holds: the flag class is fully loadable and usable with the LuckPerms API
 * absent from the classpath, which is exactly the situation on the vast majority of servers.
 *
 * <p>The API calls live in {@code LuckPermsHook} and are deliberately NOT exercised here: that class
 * cannot even be named from this test module, because {@code net.luckperms:api} is a {@code compileOnly}
 * dependency of the main source set only. That inability is the point of the split.</p>
 */
class LuckPermsCompatTest {

    /** Concrete stand-in proving consumers can still subclass the flag and pin their own {@code since()}. */
    private static final class TestLuckPerms extends LuckPermsCompat {
        @Override
        public SemanticVersion since() {
            return new SemanticVersion(1, 0, 0, null);
        }
    }

    private final LuckPermsCompat compat = new TestLuckPerms();

    @Test
    void classNamesTargetTheLuckPermsApiEntryPoint() {
        assertThat(compat.classNames()).containsExactly("net.luckperms.api.LuckPerms");
        assertThat(LuckPermsCompat.INSTANCE.classNames()).containsExactly(LuckPermsCompat.PROBE_CLASS);
    }

    @Test
    void doesNotRequireInitializationAndHasNoDependencies() {
        assertThat(compat.needsInitialization()).isFalse();
        assertThat(compat.dependencies()).isEmpty();
    }

    @Test
    void adminPermissionConstantIsTheEunomiaAdminNode() {
        assertThat(LuckPermsCompat.ADMIN_PERMISSION).isEqualTo("eunomia.admin");
    }

    @Test
    void defaultInstanceDeclaresTheApiVersionEunomiaCompilesAgainst() {
        assertThat(LuckPermsCompat.INSTANCE.since()).isEqualTo(new SemanticVersion(5, 4, 0, null));
    }

    // region Soft-dependency proof

    @Test
    void luckPermsIsGenuinelyAbsentFromThisTestClasspath() {
        assertThatThrownBy(() -> Class.forName(LuckPermsCompat.PROBE_CLASS))
                .isInstanceOf(ClassNotFoundException.class);
    }

    /**
     * The whole reason the flag and the hook are separate classes: loading, initialising and calling
     * every member of the flag must never resolve a {@code net.luckperms} type. With the API genuinely
     * absent here (see the test above), folding the API calls back into {@link LuckPermsCompat} makes
     * this redden with {@link NoClassDefFoundError} instead of only failing in production.
     */
    @Test
    void flagIsFullyUsableWithTheLuckPermsApiAbsent() {
        assertThatCode(() -> {
            LuckPermsCompat flag = LuckPermsCompat.INSTANCE;
            flag.since();
            flag.classNames();
            flag.needsInitialization();
            flag.dependencies();
            CompatManager.registerCompatFlag(flag);
            CompatManager.setCompatFlagByResourceProbing(LuckPermsCompat.class.getClassLoader());
        }).doesNotThrowAnyException();
    }

    /** Structural backstop: the compiled flag class must not even mention the LuckPerms package. */
    @Test
    void compiledFlagClassCarriesNoLuckPermsApiReference() throws IOException {
        String resource = LuckPermsCompat.class.getName().replace('.', '/') + ".class";
        try (InputStream in = LuckPermsCompat.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(in).isNotNull();
            String bytecode = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
            // The probe-class constant is the one legitimate mention, and it is dotted, not slashed -
            // a real type reference would appear as "net/luckperms/...".
            assertThat(bytecode).doesNotContain("net/luckperms");
        }
    }

    /** End to end through the manager: probing for LuckPerms is safe and negative without the API. */
    @Test
    void compatManagerProbeIsNegativeWithoutTheApi() {
        assertThat(CompatManager.classExists(LuckPermsCompat.PROBE_CLASS)).isFalse();
    }

    // endregion
}
