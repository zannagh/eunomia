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
