package de.zannagh.eunomia.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerNameUtilTest {

    // Only the non-Player guard paths are exercised here: they need no live Minecraft player,
    // just the (compile-classpath) Player type for the instanceof check. The actual name-extraction
    // branch requires a real Player and is covered by in-game/integration coverage instead.

    @Test
    void nullEntityYieldsEmptyName() {
        assertThat(PlayerNameUtil.getPlayerName(null)).isEmpty();
    }

    @Test
    void nonPlayerEntityYieldsEmptyName() {
        assertThat(PlayerNameUtil.getPlayerName("not a player")).isEmpty();
    }

    @Test
    void arbitraryObjectThatIsNotAPlayerYieldsEmptyName() {
        assertThat(PlayerNameUtil.getPlayerName(new Object())).isEmpty();
    }
}
