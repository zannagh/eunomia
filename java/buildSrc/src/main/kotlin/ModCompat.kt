import org.gradle.api.Project

/**
 * A single compile-time compatibility target: another mod that a consumer of these
 * convention plugins writes Stonecutter-gated compat code against.
 *
 * The dependency is only added when the consuming build supplies a Modrinth file id via
 * `-P<key>.version=<hash>` (or the equivalent line in a `["fabric-<mc>"]` / `["neoforge-<mc>"]`
 * section of stonecutter.properties.toml), so a variant that omits the property simply compiles
 * without that compat mod - exactly how the per-variant compat matrix used to be expressed inline.
 */
data class CompatMod(
    /** Property prefix. The Modrinth file id is read from `<key>.version`. */
    val key: String,
    /** Modrinth project slug, e.g. "geckolib" or "wavey-capes". */
    val slug: String,
    /** True if the mod only needs to be visible to client-side sources. */
    val clientOnly: Boolean = false,
)

/**
 * Declares the compile-time compat dependencies a mod opts into.
 *
 * This is the reusable extraction of the per-mod compat wiring that used to live inline in the
 * convention plugins. Eunomia (a bare shared library) declares none; a consuming mod such as
 * armor-hider calls this from its own `common` / loader build once these convention plugins are
 * published as a binary Gradle plugin:
 *
 * ```
 * declareCompatMods(
 *     remapped = true,          // loom `modXxx` configs (a real loader build)
 *     listOf(
 *         CompatMod("geckolib", "geckolib"),
 *         CompatMod("gender", "female-gender", clientOnly = true),
 *     ),
 * )
 * ```
 *
 * @param remapped when true the deps go to loom's `modCompileOnly` / `modClientCompileOnly` (they
 *   get remapped to the active mappings); when false they are added as plain `compileOnly` - the
 *   form the loader project needs when it compiles common's sources unremapped.
 */
public fun Project.declareCompatMods(mods: List<CompatMod>, remapped: Boolean) {
    val commonConfig = if (remapped) "modCompileOnly" else "compileOnly"
    val clientConfig = if (remapped) "modClientCompileOnly" else "compileOnly"
    for (mod in mods) {
        val hash = findProperty("${mod.key}.version")?.toString() ?: continue
        val config = if (mod.clientOnly) clientConfig else commonConfig
        dependencies.add(config, "maven.modrinth:${mod.slug}:$hash")
    }
}
