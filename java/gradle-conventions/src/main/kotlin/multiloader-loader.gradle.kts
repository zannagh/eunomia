plugins {
    id("java")
    id("multiloader-common")
}

val sc = project.stonecutterBuild
sc.constants["fabric"] = sc.current.project.contains("fabric")
sc.constants["neoforge"] = sc.current.project.contains("neoforge")

val commonNode = sc.node.sibling("common")
    ?: error("Could not find common branch sibling for ${sc.current.project}")
val commonPath = commonNode.hierarchy.toString()

// Ensure common project is fully evaluated before accessing its source sets
evaluationDependsOn(commonPath)

val commonProject = project(commonPath)
val commonSourceSets = commonProject.extensions.getByType(SourceSetContainer::class.java)

// Expose common source sets and project for loader build scripts that need additional wiring
extra["commonSourceSets"] = commonSourceSets
extra["commonProject"] = commonProject

// Compile-only dependencies needed when the loader project compiles common's (unremapped) sources.
// Compat-mod dependencies (armor-hider et al.) are NOT declared here anymore - a consuming mod
// opts into those from its own build via ModCompat.declareCompatMods(..., remapped = false).
dependencies {
    compileOnly("org.jspecify:jspecify:1.0.0")
    // LuckPerms API - compileOnly and NEVER bundled/shaded. The loader recompiles common's sources,
    // so it needs the same soft dependency on its compile classpath as the common branch (see the
    // compileOnly in multiloader-loom).
    compileOnly("net.luckperms:api:5.4")
}

// Include common's sources in the loader's source sets for IntelliJ
sourceSets.main {
    java { commonSourceSets["main"].java.srcDirs.forEach { srcDir(it) } }
    resources { commonSourceSets["main"].resources.srcDirs.forEach { srcDir(it) } }
}

// Source sets to be available in loader specific projects
sourceSets.matching { it.name == "client" }.configureEach {
    java { commonSourceSets["client"].java.srcDirs.forEach { srcDir(it) } }
    resources { commonSourceSets["client"].resources.srcDirs.forEach { srcDir(it) } }
}

// Declare dependency on common's Stonecutter generation tasks so sources are ready
val commonStonecutterGenerate = commonProject.tasks.named("stonecutterGenerate")
val commonStonecutterGenerateClient = commonProject.tasks.named("stonecutterGenerateClient")

// All tasks that consume common's source/resource dirs must depend on Stonecutter generation
val commonStonecutterTasks = listOf(commonStonecutterGenerate, commonStonecutterGenerateClient)

tasks {
    compileJava { dependsOn(commonStonecutterTasks) }
    processResources { dependsOn(commonStonecutterTasks) }
    named("sourcesJar") { dependsOn(commonStonecutterTasks) }

    // When a client source set exists, its tasks also need common's Stonecutter output
    matching { it.name in listOf("compileClientJava", "processClientResources") }.configureEach {
        dependsOn(commonStonecutterTasks)
    }

    jar {
        inputs.property("archivesName", base.archivesName)
        // Bundle the MC-free :core classes directly into the shipped loader mod jar so it is
        // self-contained. Without this the mod jar carries only the loader glue and crashes on init
        // with NoClassDefFoundError for de.zannagh.eunomia.networking.* / configuration.* the moment
        // it runs as a standalone jar (dev/smoke runs never caught it - the dev classpath has :core).
        // Shaded rather than jar-in-jar'd because :core is a plain MC-free library (no fabric.mod.json),
        // which Fabric's nested-jar loader would not pick up; its classes are MC-free so remapJar
        // passes them through untouched on the remapped variants.
        val coreMain = project(":core").extensions
            .getByType(org.gradle.api.tasks.SourceSetContainer::class.java)
            .getByName("main")
        dependsOn(project(":core").tasks.named("classes"))
        from(coreMain.output)
    }
}

// ── Mixin-config sanity guard ────────────────────────────────────────────────────
// Every name statically listed in a *.mixins.json must resolve, ON THIS VARIANT, to a COMPILED
// class that actually carries @Mixin. A stonecutter-stubbed class without the annotation makes
// mixin abort during PREPARE and the game dies at boot ("... is missing an @Mixin annotation") -
// a class of bug invisible to every JVM-only test, which only shows up once a real client boots.
//
// WHY IT LIVES HERE (and not in multiloader-loom, where it started): the guard has to span BOTH
// loaders. It was originally registered inside multiloader-loom's `branch == "fabric"` block, so
// `:neoforge:<variant>:checkMixinConfigs` simply did not exist - yet the bug that motivated the
// guard (PackRepositoryMixin compiling to an annotation-less stub) hit EVERY NeoForge variant.
// NeoForge never applies fabric-loom at all (it builds on net.neoforged.moddev), which makes
// multiloader-loader the only convention plugin both loader branches share, and therefore the
// narrowest scope that actually covers both. The `common` branch is deliberately not covered:
// it ships nothing, and its classes are recompiled by each loader anyway.
//
// The check keys off the *.mixins.json files found in the source sets rather than off a loader
// manifest, because the manifest that lists them differs per loader (fabric.mod.json vs
// META-INF/neoforge.mods.toml, the latter templated by processResources) - the configs themselves
// (eunomia.mixins.json / eunomia.client.mixins.json) are shared by both.
//
// The check reads the class file (looking for the annotation descriptor in its constant pool)
// rather than the source: on a non-active variant the source still contains the text "@Mixin"
// inside the stonecutter-commented branch, so a source scan would happily pass the very stub it
// is meant to catch. Names added dynamically by an IMixinConfigPlugin are deliberately NOT
// checked - gating those by version is exactly the supported fix.
val mixinGuardSourceSets = project.extensions.getByType(SourceSetContainer::class.java)
// Resolved at execution time, not here: the `client` source set is created later than this
// convention plugin on BOTH loaders (Fabric via loom's splitEnvironmentSourceSets(), NeoForge in
// neoforge/build.gradle.kts), so an eager flatMap would only ever see `main` and would silently
// stop checking the client config.
val mixinGuardResourceDirs = project.provider { mixinGuardSourceSets.flatMap { it.resources.srcDirs } }
val mixinGuardClassDirs = project.provider { mixinGuardSourceSets.flatMap { it.output.classesDirs.files } }
val checkMixinConfigs = tasks.register("checkMixinConfigs") {
    group = "verification"
    description = "Fail if a statically listed mixin in a *.mixins.json has no @Mixin class on this variant"
    // Matched by name instead of tasks.named(...) for the same late-creation reason: `clientClasses`
    // does not exist yet at this point, and depending on it eagerly would break configuration.
    dependsOn(tasks.matching { it.name == "classes" || it.name == "clientClasses" })
    doLast {
        val configs = mixinGuardResourceDirs.get().filter { it.isDirectory }.flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.name.endsWith(".mixins.json") }.toList()
        }.distinct()
        val classDirs = mixinGuardClassDirs.get().filter { it.isDirectory }
        val problems = mutableListOf<String>()
        for (config in configs) {
            val text = config.readText()
            val pkg = Regex(""""package"\s*:\s*"([^"]+)"""").find(text)?.groupValues?.get(1) ?: continue
            val array = Regex(""""mixins"\s*:\s*\[([^]]*)]""").find(text)?.groupValues?.get(1) ?: continue
            for (name in Regex(""""([^"]+)"""").findAll(array).map { it.groupValues[1] }) {
                val relative = (pkg + "." + name).replace('.', '/') + ".class"
                // `File`, not `java.io.File`: inside this plugin the java-plugin `java` extension
                // accessor shadows the package name, so the fully qualified form does not compile.
                val classFile = classDirs.map { dir -> File(dir, relative) }.firstOrNull { it.isFile }
                if (classFile == null) {
                    problems += "${config.name}: '$name' has no compiled class ($relative)"
                    continue
                }
                val bytes = classFile.readBytes().toString(Charsets.ISO_8859_1)
                if (!bytes.contains("Lorg/spongepowered/asm/mixin/Mixin;")) {
                    problems += "${config.name}: '$name' compiles to a class with no @Mixin on " +
                        "${sc.current.project} - move it into an IMixinConfigPlugin's getMixins() " +
                        "behind the same stonecutter gate as the class"
                }
            }
        }
        check(problems.isEmpty()) { "Invalid mixin configuration:\n" + problems.joinToString("\n") { "  - $it" } }
    }
}
tasks.matching { it.name == "check" }.configureEach { dependsOn(checkMixinConfigs) }
