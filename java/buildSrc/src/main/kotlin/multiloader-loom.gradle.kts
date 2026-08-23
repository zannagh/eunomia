val isDeobf = extra.has("loom.deobf") && extra.get("loom.deobf") as Boolean
val sc = project.stonecutterBuild
val branch = sc.branch.id
val mcVersion = sc.current.project.substringAfter('-')

// ── Base setup ──
if (branch == "common") {
    apply(plugin = "multiloader-common")
} else {
    apply(plugin = "multiloader-loader")
}

// ── Loom ──
if (isDeobf) {
    extra.set("fabric.loom.disableObfuscation", "true")
}
apply(plugin = "fabric-loom")

val loom = the<net.fabricmc.loom.api.LoomGradleExtensionAPI>()

dependencies {
    "minecraft"("com.mojang:minecraft:$mcVersion")
    if (isDeobf) {
        "implementation"("net.fabricmc:fabric-loader:${property("loader_version")}")
    } else {
        "mappings"(loom.officialMojangMappings())
    }
}

repositories {
    // Modrinth maven - kept so a consuming mod can declare compat dependencies
    // (see ModCompat.declareCompatMods) without re-adding the repository.
    maven("https://api.modrinth.com/maven") {
        content { includeGroup("maven.modrinth") }
    }
}

// ── Stonecutter constants ──
with(sc) {
    constants["fabric"] = current.project.contains("fabric")
    constants["neoforge"] = current.project.contains("neoforge")
    // `fcgt` gates the FCGT (fabric-client-gametest-api-v1) networking smoke: on for Fabric variants
    // that pin `fabricapi.semver` (currently fabric-26.2), off everywhere else so the test class and
    // its entrypoint stay commented out where the module is not available.
    constants["fcgt"] = hasProperty("fabricapi.semver") && current.project.contains("fabric")
}

// ── Common branch ──
if (branch == "common") {
    loom.apply {
        splitEnvironmentSourceSets()
        mixin { useLegacyMixinAp = false }
        runConfigs.configureEach { runDirectory.dir("run") }
    }

    dependencies {
        if (!isDeobf) {
            add("modCompileOnly", "net.fabricmc:fabric-loader:${property("loader_version")}")
        }
        add("compileOnly", "org.jspecify:jspecify:1.0.0")
        // LuckPerms API - compileOnly and NEVER bundled/shaded: on a server running LuckPerms the
        // classes come from LuckPerms itself, and on one that does not, the only class referencing
        // them (LuckPermsHook) is never loaded because the CompatManager probe gates it.
        add("compileOnly", "net.luckperms:api:5.4")
        add("testImplementation", platform("org.junit:junit-bom:6.0.1"))
        add("testImplementation", "org.junit.jupiter:junit-jupiter")
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
        // FCGT: the individual fabric-client-gametest-api-v1 module on the client compile classpath so
        // the stonecutter-gated NetworkingSmokeTest in common/src/client compiles on variants that pin
        // fabricapi.semver. Uses the flat module jar (works with plain configs in the deobf variants).
        if (hasProperty("fabricapi.semver")) {
            // NOTE: `project.extensions`, not `extensions` - inside a `dependencies { }` block the
            // implicit receiver is the DependencyHandler, whose (ExtensionAware) container only holds
            // ExtraPropertiesExtension. Resolving it there fails with "Extension of type
            // 'FabricApiExtension' does not exist", which is what was previously misdiagnosed as
            // "loom registers FabricApiExtension late in deobf mode".
            val fabricApiExt = project.extensions.getByType(net.fabricmc.loom.api.fabricapi.FabricApiExtension::class.java)
            val semver = findProperty("fabricapi.semver")!!.toString()
            add(if (isDeobf) "clientCompileOnly" else "modClientCompileOnly",
                fabricApiExt.module("fabric-client-gametest-api-v1", semver))
        }
    }

    val javaVersionStr = findProperty("java.version")?.toString() ?: error("No Java version specified")
    val javaVersionProp = mapOf("java_version" to javaVersionStr)

    tasks.named<ProcessResources>("processResources") {
        inputs.properties(javaVersionProp)
        filesMatching("**/*.mixins.json", ExpandPropertiesAction(javaVersionProp))
    }
    tasks.named<ProcessResources>("processClientResources") {
        inputs.properties(javaVersionProp)
        filesMatching("**/*.mixins.json", ExpandPropertiesAction(javaVersionProp))
    }
}

// ── Fabric branch ──
if (branch == "fabric") {
    val fabricVersion = findProperty("fabric.minecraft_version")?.toString()
        ?: error("No Fabric version mapping for Minecraft $mcVersion")

    val shouldLoadDevProfile = !gradle.startParameter.isOffline && gradle.startParameter.taskNames.any { taskName ->
        val simple = taskName.substringAfterLast(':')
        simple.startsWith("run") || simple == "genIntellijRuns"
    }
    val devProfile = if (shouldLoadDevProfile) loadDevProfile() else null

    loom.apply {
        splitEnvironmentSourceSets()
        mods {
            register("eunomia") {
                sourceSet(project.extensions.getByType(SourceSetContainer::class.java).getByName("main"))
                sourceSet(project.extensions.getByType(SourceSetContainer::class.java).getByName("client"))
            }
        }
        runConfigs.configureEach {
            runDirectory.dir("run")
            generateRunConfig
            if (isDeobf) {
                jvmArguments.add("-Dfabric.gameVersion=${fabricVersion}")
            }
            // Dev skin: when dev-profile.properties supplies a username, launch the dev client
            // as that account (and its resolved skin) so first-person / render work can be tested
            // against a real skin. Absent profile = normal offline dev client.
            if (devProfile != null) {
                programArguments.add("--username ${devProfile.username}")
                programArguments.add("--uuid ${devProfile.uuid}")
                if (devProfile.skinTexturesValue != null) {
                    programArguments.add("-Deunomia.dev.skin.textures=${devProfile.skinTexturesValue}")
                }
                if (devProfile.skinTexturesSignature != null) {
                    programArguments.add("-Deunomia.dev.skin.signature=${devProfile.skinTexturesSignature}")
                }
            }
        }
    }

    dependencies {
        if (!isDeobf) {
            add("modImplementation", "net.fabricmc:fabric-loader:${property("loader_version")}")
        }
        // FCGT networking smoke: the individual fabric-client-gametest-api-v1 module on the client
        // COMPILE classpath so NetworkingSmokeTest compiles. Runtime provisioning is deliberately NOT
        // done here - see the `copyFcgtToMods` block below for why `modClientRuntimeOnly` is the wrong
        // tool (it does not exist in deobf mode, and it did not reliably put the module where
        // fabric-loader looks for it, which produced a task that "passed" having run nothing).
        if (hasProperty("fabricapi.semver")) {
            // NOTE: `project.extensions`, not `extensions` - inside a `dependencies { }` block the
            // implicit receiver is the DependencyHandler, whose (ExtensionAware) container only holds
            // ExtraPropertiesExtension. Resolving it there fails with "Extension of type
            // 'FabricApiExtension' does not exist", which is what was previously misdiagnosed as
            // "loom registers FabricApiExtension late in deobf mode".
            val fabricApiExt = project.extensions.getByType(net.fabricmc.loom.api.fabricapi.FabricApiExtension::class.java)
            val semver = findProperty("fabricapi.semver")!!.toString()
            add(if (isDeobf) "clientCompileOnly" else "modClientCompileOnly",
                fabricApiExt.module("fabric-client-gametest-api-v1", semver))
        }
    }

    // Registered FCGT entrypoints for this variant, as (short id, class name) pairs. The short id is
    // what `-Psmoke.fcgt.only=a,b` selects on, so a row can be narrowed to a single test by a stable,
    // typo-proof name instead of an FQCN. Variants without `fabricapi.semver` emit "[]" - still valid
    // JSON, so fabric-loader simply ignores the entrypoint.
    //
    // CONTRACT: every conditional `add(...)` guard here MUST mirror the corresponding test class's own
    // `//? if fcgt && ...` stonecutter gate exactly. If the guard is wider than the class gate,
    // fabric-loader tries to resolve a class stonecutter has commented out and the whole launch dies.
    val fcgtTestCatalog = buildList {
        // Every class below is gated on plain `//? if fcgt`, so they register on every FCGT variant.
        add("networking" to "de.zannagh.eunomia.smoke.NetworkingSmokeTest")
        add("settings-ui" to "de.zannagh.eunomia.smoke.SettingsUiSmokeTest")
        add("toasts" to "de.zannagh.eunomia.smoke.ToastSmokeTest")
    }

    // `runClientGametest` runs EVERY registered entrypoint in ONE client launch, so an unrelated
    // sibling failure reds the whole run. `-Psmoke.fcgt.only=a,b` narrows the registered set, which
    // lets a single test report on its own merits (and run far faster). Filtering here rather than
    // self-skipping inside each test keeps the knowledge in one place.
    val fcgtOnly = findProperty("smoke.fcgt.only")?.toString()
        ?.split(",")?.map(String::trim)?.filter(String::isNotEmpty)?.toSet()
    if (fcgtOnly != null) {
        val known = fcgtTestCatalog.map { it.first }.toSet()
        val unknown = fcgtOnly - known
        // A typo would otherwise silently register nothing and "pass" - fail loudly instead.
        require(unknown.isEmpty()) {
            "-Psmoke.fcgt.only contains unknown test id(s) $unknown; known ids on " +
                "${sc.current.project}: $known"
        }
    }
    val fcgtEntries = if (hasProperty("fabricapi.semver"))
        fcgtTestCatalog
            .filter { fcgtOnly == null || it.first in fcgtOnly }
            .joinToString(", ", "[", "]") { "\"${it.second}\"" }
    else
        "[]"

    // The `runClientGametest` run config on variants that pin fabricapi.semver. FCGT swaps the main
    // loop for the test driver via these properties; disableNetworkSynchronizer is required because our
    // codec-injection mixin interfaces with packets at a low level, which FCGT otherwise hard-asserts on.
    if (hasProperty("fabricapi.semver")) {
        loom.apply {
            runConfigs.create("clientGametest") {
                client()
                ideConfigGenerated(true)
                runDirectory.set(layout.projectDirectory.dir("run"))
                displayName.set("Client GameTest")
                generateRunConfig.set(true)
                // FCGT activates via TWO properties:
                //  - `fabric.client.gametest` (any value) -> ClientGameTestMixinConfigPlugin applies the
                //    lifecycle/threading mixins that hand control to the runner.
                //  - `fabric.client.gametest.modid` -> FabricClientGameTestRunner uses this to filter
                //    `fabric-client-gametest` entrypoints to dispatch. Without it the mixins fire but no
                //    test class runs and MC just sits at the title screen.
                jvmArguments.add("-Dfabric.client.gametest=true")
                jvmArguments.add("-Dfabric.client.gametest.modid=eunomia")
                // The mod injects its payload types directly into the ClientboundCustomPayloadPacket
                // codec from a netty thread, which FCGT's NetworkSynchronizer detects as "interfacing
                // with packets at a lower level" and turns into a hard AssertionError the moment we
                // connect to a server. Mandatory for any gametest that joins one - the codec injection
                // is load-bearing and cannot be dropped.
                jvmArguments.add("-Dfabric.client.gametest.disableNetworkSynchronizer=true")
                // Keep the gametest window from stealing focus on macOS (it otherwise pops to the
                // foreground and kicks the developer out of any fullscreen app every FCGT loop). 26.3
                // uses SDL, which reads these hint env vars before creating the window; "0" tells it not
                // to activate/raise-to-front on show. Harmless off macOS / when SDL isn't in use.
                environmentVariable("SDL_WINDOW_ACTIVATE_WHEN_SHOWN", "0")
                environmentVariable("SDL_WINDOW_ACTIVATE_WHEN_RAISED", "0")
                // Where the UI smokes write their screenshots. FCGT's default is <runDir>/screenshots,
                // i.e. one directory per variant buried under fabric/versions/<variant>/run - which makes
                // "look at the new UI on every game version" a scavenger hunt. Pointing every variant at
                // one collected tree under the root build directory keeps the whole matrix in one place.
                // SmokeShots empties this directory once per launch, so it only ever holds this run.
                jvmArguments.add("-Deunomia.smoke.screenshotDir="
                    + rootProject.layout.buildDirectory.get().asFile
                        .resolve("ui-screenshots")
                        .resolve(sc.current.project).absolutePath)
            }
        }

        // ── FCGT runtime provisioning ────────────────────────────────────────────────────
        // Resolve the FCGT module artifact through a dedicated PLAIN configuration so we can copy the
        // resolved jar into run/mods. Two reasons this is not loom's `modClientRuntimeOnly`:
        //  1. Plain configurations exist in deobf mode; loom's `mod*` configurations do not. This is
        //     exactly what unblocks the 26.x deobf variants (the old "loom registers FabricApiExtension
        //     late" caveat in smoke/README.md was wrong - the problem was runtime provisioning).
        //  2. fabric-api's umbrella jar does not ship FCGT (it is experimental upstream), so putting the
        //     module physically in run/mods is the only path that reliably gets its mixin plugin loaded.
        val fcgtRuntimeMod = configurations.create("fcgtRuntimeMod") {
            isCanBeResolved = true
            isCanBeConsumed = false
            isVisible = false
        }
        val fabricApiExt = project.extensions.getByType(net.fabricmc.loom.api.fabricapi.FabricApiExtension::class.java)
        val fabricApiSemver = findProperty("fabricapi.semver")!!.toString()
        dependencies.add(
            "fcgtRuntimeMod",
            fabricApiExt.module("fabric-client-gametest-api-v1", fabricApiSemver)
        )
        // FCGT hard-depends on the fabric resource loader, but that is a RUNTIME (fabric.mod.json)
        // dependency, not a Gradle-transitive one: copying only the FCGT module leaves it missing and
        // fabric-loader aborts at boot with "requires fabric-resource-loader-v0, which is missing"
        // (verified on fabric-1.21.8 / FCGT 4.2.5). The module was renamed v0 -> v1 across the
        // fabric-api lines, and fabricApiExt.module resolves the submodule version from the PINNED
        // fabric-api's module list, so asking for the wrong one throws "Failed to find module version".
        // Try both and keep whichever this line actually publishes - hence runCatching on each.
        // Fabric-loader deduplicates against any umbrella-bundled copy, so provisioning it is safe.
        for (resourceLoader in listOf("fabric-resource-loader-v0", "fabric-resource-loader-v1")) {
            runCatching {
                dependencies.add("fcgtRuntimeMod", fabricApiExt.module(resourceLoader, fabricApiSemver))
            }
        }
        val copyFcgtToMods = tasks.register<Copy>("copyFcgtToMods") {
            group = "verification"
            description = "Drop the FCGT module jar into run/mods/ so its mixin plugin loads at runtime"
            from(fcgtRuntimeMod)
            into(project.layout.projectDirectory.dir("run/mods"))
            // Never cache: we want the FCGT jar to land every time runClientGametest fires, so a wiped
            // or hand-edited run/mods can never strand a later run with nothing to load.
            outputs.upToDateWhen { false }
        }

        tasks.named("runClientGametest") {
            // NOT gated on -Psmoke or anything else. The FCGT module jar is what makes this task do
            // anything at all: without it in run/mods, fabric-loader never loads FCGT's mixin plugin,
            // Minecraft boots VANILLA, idles at the title screen and exits ZERO. The task therefore
            // reports success while having run no test - a false green. This one line is the whole
            // difference between a real end-to-end smoke and a very slow no-op; do not make it
            // conditional.
            dependsOn(copyFcgtToMods)
        }
    }

    val expandProps = mapOf(
        "version" to project.version,
        "java_version" to (findProperty("java.version")?.toString() ?: error("No Java version")),
        "fabric_minecraft_version" to (findProperty("fabric.minecraft_version_range")?.toString() ?: error("No Fabric version range")),
        "fcgt_entries" to fcgtEntries
    )

    tasks.named<ProcessResources>("processResources") {
        inputs.properties(expandProps)
        filesMatching(listOf("fabric.mod.json", "**/*.mixins.json"), ExpandPropertiesAction(expandProps))
    }
    tasks.named<ProcessResources>("processClientResources") {
        inputs.properties(expandProps)
        filesMatching("**/*.mixins.json", ExpandPropertiesAction(expandProps))
    }

    val expandTask = registerExpandResourcesForIdea(
        tasks.named<ProcessResources>("processResources") to "out/production/resources",
        tasks.named<ProcessResources>("processClientResources") to "out/client/resources"
    )
    expandTask.configure { dependsOn(tasks.named("classes"), tasks.named("clientClasses")) }
    patchLoomIdeRunConfigs(expandTask)
}
