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
        add("testImplementation", platform("org.junit:junit-bom:6.0.1"))
        add("testImplementation", "org.junit.jupiter:junit-jupiter")
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
        // FCGT: the individual fabric-client-gametest-api-v1 module on the client compile classpath so
        // the stonecutter-gated NetworkingSmokeTest in common/src/client compiles on variants that pin
        // fabricapi.semver. Uses the flat module jar (works with plain configs in the deobf variants).
        if (hasProperty("fabricapi.semver")) {
            val fabricApiExt = extensions.getByType(net.fabricmc.loom.api.fabricapi.FabricApiExtension::class.java)
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
        // FCGT networking smoke: fabric-api plus the individual FCGT module on the client
        // compile+runtime classpath so the gametest runner's mixin plugin loads and NetworkingSmokeTest
        // can drive a real client. The umbrella from fabric maven carries the FCGT module (the
        // Modrinth-distributed one strips it), and the flat module jar covers the deobf variants.
        if (hasProperty("fabricapi.semver")) {
            val fabricApiExt = extensions.getByType(net.fabricmc.loom.api.fabricapi.FabricApiExtension::class.java)
            val semver = findProperty("fabricapi.semver")!!.toString()
            add(if (isDeobf) "clientCompileOnly" else "modClientCompileOnly",
                fabricApiExt.module("fabric-client-gametest-api-v1", semver))
            add("modClientRuntimeOnly", "net.fabricmc.fabric-api:fabric-api:$semver")
            add("modClientRuntimeOnly", fabricApiExt.module("fabric-client-gametest-api-v1", semver))
        }
    }

    // Registered FCGT entrypoints for this variant: the networking smoke where the module is available,
    // an empty (but valid JSON) array everywhere else so fabric-loader simply ignores the entrypoint.
    val fcgtEntries = if (hasProperty("fabricapi.semver"))
        "[\"de.zannagh.eunomia.smoke.NetworkingSmokeTest\"]"
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
                runDirectory.dir("run")
                jvmArguments.add("-Dfabric.client.gametest=true")
                jvmArguments.add("-Dfabric.client.gametest.modid=eunomia")
                jvmArguments.add("-Dfabric.client.gametest.disableNetworkSynchronizer=true")
            }
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
