package de.zannagh.eunomia.paper.perm;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Hermetic test of the Paper-side permission ladder (op -> superperms -> LuckPerms). No server and,
 * deliberately, no LuckPerms API on the classpath - which is also the soft-dependency proof: every
 * case below runs to completion with {@code net.luckperms.*} entirely absent.
 */
class PermissionResolverTest {

    private static final Logger LOGGER = Logger.getLogger(PermissionResolverTest.class.getName());

    private PermissionResolver resolver(boolean luckPermsPresent) {
        return new PermissionResolver(LOGGER, () -> luckPermsPresent);
    }

    private Player player(boolean op, boolean hasNode) {
        Player player = mock(Player.class);
        lenient().when(player.isOp()).thenReturn(op);
        lenient().when(player.hasPermission(PermissionResolver.ADMIN_PERMISSION)).thenReturn(hasNode);
        lenient().when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }

    @Test
    void operatorsShortCircuitToAdminLevelWithoutConsultingPermissions() {
        Player player = player(true, false);

        assertEquals(PermissionResolver.ADMIN_LEVEL, resolver(false).getPermissionLevel(player));
        assertTrue(resolver(false).isAdmin(player));
        verify(player, never()).hasPermission(PermissionResolver.ADMIN_PERMISSION);
    }

    @Test
    void superpermsNodeGrantsAdminLevel() {
        Player player = player(false, true);

        assertEquals(PermissionResolver.ADMIN_LEVEL, resolver(false).getPermissionLevel(player));
        // Answered by Bukkit alone, so LuckPerms is never reached even when it is installed.
        assertEquals(PermissionResolver.ADMIN_LEVEL, resolver(true).getPermissionLevel(player));
    }

    @Test
    void plainPlayerIsNotAnAdminAndNeverTouchesTheLuckPermsHook() {
        Player player = player(false, false);
        PermissionResolver resolver = resolver(false);

        assertEquals(0, resolver.getPermissionLevel(player));
        assertFalse(resolver.isAdmin(player));
        assertFalse(resolver.isLuckPermsPresent());
        // The guarded branch was not taken, so no net.luckperms class was ever needed.
        verify(player, never()).getUniqueId();
    }

    @Test
    void nullPlayerIsNeverAnAdmin() {
        assertEquals(0, resolver(true).getPermissionLevel(null));
        assertFalse(resolver(true).isAdmin(null));
    }

    // region Soft-dependency proof

    @Test
    void luckPermsIsGenuinelyAbsentFromThisTestClasspath() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("net.luckperms.api.LuckPerms"));
    }

    /**
     * Structural invariant behind the split: {@link PermissionResolver} - the class every join goes
     * through - must not itself mention the LuckPerms package. All such references live in
     * {@link LuckPermsHook}, which is only reached from the guarded branch.
     */
    @Test
    void resolverCarriesNoLuckPermsApiReference() throws IOException {
        String resource = PermissionResolver.class.getName().replace('.', '/') + ".class";
        try (InputStream in = PermissionResolver.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in);
            String bytecode = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
            assertFalse(bytecode.contains("net/luckperms"),
                    "PermissionResolver must not reference net.luckperms - that belongs in LuckPermsHook");
        }
    }

    // endregion
}
