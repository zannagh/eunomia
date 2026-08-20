package de.zannagh.eunomia.compatibility.known;

import de.zannagh.eunomia.common.SemanticVersion;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the mixin-safe metadata {@link LuckPermsCompat} exposes without pulling the real LuckPerms API:
 * its probe class name, no-initialization / no-dependency contract, the admin permission constant, and the
 * currently-stubbed {@link LuckPermsCompat#getPermissionLevel(UUID)} accessor.
 */
class LuckPermsCompatTest {

    /** Concrete stand-in; {@code since()} is the only member {@link LuckPermsCompat} leaves abstract. */
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
    void getPermissionLevelIsStubbedToZeroUntilTheApiIsWiredUp() {
        assertThat(LuckPermsCompat.getPermissionLevel(UUID.randomUUID())).isZero();
        assertThat(LuckPermsCompat.getPermissionLevel(null)).isZero();
    }
}
