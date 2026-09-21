package de.zannagh.eunomia;

import de.zannagh.eunomia.client.EunomiaClientMixinPlugin;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Both mixin config plugins must return {@code null} from {@link IMixinConfigPlugin#getRefMapperConfig()}.
 *
 * <p><strong>Do not "tidy" these to {@code ""}.</strong> Mixin asks the plugin for a refmap only when the
 * config JSON carries no {@code "refmap"} field, and it then uses a NON-null answer <em>verbatim</em> as the
 * resource path handed to {@code ReferenceMapper.read}. {@code ""} is therefore not "no preference" - it is
 * "read the refmap from the resource named &lt;empty string&gt;", which never resolves, silently degrades to
 * {@code ReferenceMapper.DEFAULT_MAPPER}, and leaves every obfuscated target unmapped. On Fabric/NeoForge
 * (Mojang-mapped at runtime) that is invisible, and every dev run stays green - but in a REOBFUSCATED
 * classic-Forge jar it turns every injection into a silent no-op. {@code null} is the correct answer: Mixin
 * falls back to {@code ReferenceMapper.DEFAULT_RESOURCE} and sets its suppress-warning flag, so a
 * deobfuscated dev run without a refmap also stays quiet.</p>
 *
 * <p>The failure mode is invisible at runtime on the loaders anyone develops on, so it gets a unit test
 * rather than relying on a reviewer remembering why the method looks pointless.</p>
 */
class MixinPluginRefMapperConfigTest {

    @Test
    void commonPluginReturnsNullRefMapperConfig() {
        IMixinConfigPlugin plugin = new EunomiaMixinPlugin();

        assertThat(plugin.getRefMapperConfig())
                .as("EunomiaMixinPlugin.getRefMapperConfig() must be null, never \"\" - an empty string is "
                        + "used verbatim as a resource path and silently disables refmap resolution")
                .isNull();
    }

    @Test
    void clientPluginReturnsNullRefMapperConfig() {
        IMixinConfigPlugin plugin = new EunomiaClientMixinPlugin();

        assertThat(plugin.getRefMapperConfig())
                .as("EunomiaClientMixinPlugin.getRefMapperConfig() must be null, never \"\" - an empty string "
                        + "is used verbatim as a resource path and silently disables refmap resolution")
                .isNull();
    }
}
