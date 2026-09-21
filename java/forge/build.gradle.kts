plugins {
    id("multiloader-loader")
    id("net.neoforged.moddev.legacyforge")
}

val sc = project.stonecutterBuild

// :core must be evaluated before its source sets can be read below.
evaluationDependsOn(":core")
val coreSourceSets = project(":core").extensions.getByType(SourceSetContainer::class.java)
// Hoisted out of the `legacyForge { runs { .. } }` block on purpose: inside a RunModel the
// `project(..)` accessor is the dependency factory (RunModel implements Dependencies), not Project.
val coreClasses = project(":core").tasks.named("classes")

val forgeVersion = findProperty("forge.version")?.toString()
    ?: error("No forge.version for ${sc.current.project}")
val forgeVersionRange = findProperty("forge.minecraft_version_range")?.toString()
    ?: error("No forge.minecraft_version_range for ${sc.current.project}")

val javaVersion = findProperty("java.version")?.toString()
    ?: error("No java.version for ${sc.current.project}")

val clientSourceSet = sourceSets.create("client") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath
}

// Classic Forge doesn't split environments - main has the full MC jar, so common client sources
// must also be in main for Forge-specific code that references common client classes. Same shape
// as the NeoForge script; see there for the full rationale.
val commonSourceSets = extra["commonSourceSets"] as SourceSetContainer
sourceSets.main {
    java { commonSourceSets["client"].java.srcDirs.forEach { srcDir(it) } }
    resources { commonSourceSets["client"].resources.srcDirs.forEach { srcDir(it) } }
}

stonecutter {
    constants["forge"] = true
    // `fcgt` is defined in multiloader-loom, which classic Forge never applies (it builds on
    // moddev-gradle). Stonecutter hard-errors on an UNKNOWN constant the moment a source file it
    // generates uses `//? if fcgt` - and common's client sources do, in :common's smoke package.
    // Declared false here so the gate resolves rather than exploding: FCGT is Fabric-only.
    constants["fcgt"] = false
}

legacyForge {
    version = forgeVersion

    runs {
        register("client") {
            client()
            taskBefore(coreClasses)
            // Launch via the Gradle run task, which forks the game on the project's Java 17
            // toolchain. The daemon must stay on Java 21 for Stonecutter 0.9.1, so we cannot rely
            // on the IDE's Gradle JVM; MDG's generated IntelliJ run config pins no JRE and would
            // otherwise inherit the 21 daemon and crash MC 1.20.1 (LWJGL unsupported JNI version).
            disableIdeRun()
        }
        register("server") {
            server()
            taskBefore(coreClasses)
            disableIdeRun()
        }
    }

    mods {
        register("eunomia") {
            sourceSet(sourceSets.main.get())
            sourceSet(clientSourceSet)
            // :core is the MC-free networking/configuration library. The shipped jar gets it by
            // shading (see multiloader-loader.gradle.kts's `jar` block), but a dev run loads the mod
            // from these source-set output folders instead, and a mod may only see classes that are
            // part of ITS mod - without this a dev run dies at boot with NoClassDefFoundError for
            // de/zannagh/eunomia/configuration/ConfigurationProvider.
            sourceSet(coreSourceSets["main"])
        }
    }
}

// Mixins for classic Forge. MDG's `config(...)` only registers the config for dev runs (as
// `--mixin.config` launch args) and does NOT emit anything into the shipped jar: it neither writes
// the `MixinConfigs` manifest attribute nor generates a refmap. Three things are therefore wired
// explicitly so the REOBFUSCATED production jar actually applies the mixins:
//   1. `add(sourceSet, refmapName)` runs the Mixin annotation processor over that source set,
//      generating the SRG refmap and bundling it into the jar. Without it every @Inject target
//      written against Mojang names fails to resolve in a reobf'd jar - silently on a dev run,
//      fatally (or, worse, as a no-op) in production.
//   2. The `MixinConfigs` manifest attribute on `tasks.jar` below - classic Forge registers mixin
//      configs from that manifest entry, since a 1.20.1 mods.toml has no [[mixins]] section.
//   3. The `"refmap"` field stamped into the packaged configs by InjectMixinRefmapAction below.
// Every mixin class ends up in the main source set (common main AND common client sources are
// srcDir'd into main above), so a single main refmap covers both configs.
mixin {
    config("eunomia.mixins.json")
    config("eunomia.client.mixins.json")
    add(sourceSets.main.get(), "eunomia.refmap.json")
}

// The Mixin annotation processor that generates the refmap `mixin.add(...)` wires into the jar. MDG
// sets the compiler ARGS for it but does not put the processor itself on the classpath, so add it
// here. Forge 1.20.1 ships Mixin 0.8.5; the `:processor` classifier is the fat AP jar.
dependencies {
    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")

    // MixinExtras, COMPILE-ONLY. Fabric Loader and NeoForge both ship it, so the other two loaders
    // get it transitively and never name it; classic Forge 1.20.1 ships neither the library nor a
    // bootstrap for it, hence the explicit compile-time stand-in.
    //
    // Exactly one class in :common uses it - the dev-only client.mixins.DevSkinMixin - and that
    // mixin is NEVER registered on a Forge runtime: EunomiaClientMixinPlugin only adds it when the
    // process looks like a development launch, which it decides from `fabric.development` (set by
    // Loom) or the dev-skin system properties the Fabric/NeoForge run configs pass. A Forge run sets
    // none of them, so the class is never loaded and the absent runtime library is never reached.
    // If DevSkinMixin ever becomes reachable on Forge, this must become a bundled (jar-in-jar)
    // mixinextras-forge plus a MixinExtrasBootstrap.init() call - compileOnly would then be a lie.
    compileOnly("io.github.llamalad7:mixinextras-common:0.5.5")
    annotationProcessor("io.github.llamalad7:mixinextras-common:0.5.5")
}

tasks.jar {
    from(clientSourceSet.output)
    // Common client sources are in both main and client (main needs them for compile visibility,
    // client gets them from multiloader-loader). Exclude duplicates in the jar.
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    // Classic Forge loads mixin configs from this manifest attribute. MDG does not add it, so do it
    // here. The reobfuscation (RemapJar) step copies this manifest through to the shipped jar.
    manifest {
        attributes("MixinConfigs" to "eunomia.mixins.json,eunomia.client.mixins.json")
    }
}

val expandProps = mapOf(
    "version" to project.version,
    "minecraft_version" to forgeVersionRange,
    "forge_version" to forgeVersion,
    "java_version" to javaVersion
)

// Must match the name passed to `mixin.add(...)` above so the packaged configs point Mixin at the
// generated SRG refmap. Fabric and NeoForge are untouched - they keep the clean shared config.
val mixinRefmap = "eunomia.refmap.json"

tasks.processResources {
    inputs.properties(expandProps)
    inputs.property("mixinRefmap", mixinRefmap)
    filesMatching(listOf("META-INF/mods.toml", "**/*.mixins.json"), ExpandPropertiesAction(expandProps))
    filesMatching("**/*.mixins.json", InjectMixinRefmapAction(mixinRefmap))
}

tasks.named<ProcessResources>("processClientResources") {
    inputs.properties(expandProps)
    inputs.property("mixinRefmap", mixinRefmap)
    filesMatching(listOf("META-INF/mods.toml", "**/*.mixins.json"), ExpandPropertiesAction(expandProps))
    filesMatching("**/*.mixins.json", InjectMixinRefmapAction(mixinRefmap))
}
