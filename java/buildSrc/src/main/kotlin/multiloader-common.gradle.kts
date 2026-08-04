plugins {
    id("java")
    id("java-library")
}

repositories {
    maven("https://api.modrinth.com/maven") {
        content { includeGroup("maven.modrinth") }
    }
}

dependencies {
    // The MC-free networking core. A plain-Java library (not a mod / not remapped), shared with
    // :paper. Applied here in multiloader-common so it lands on the compile + dev-runtime classpath
    // of common and BOTH loader flavours (loom-based fabric and moddev-based neoforge both apply
    // this plugin), which srcDir common's sources and compile against it directly.
    "implementation"(project(":core"))
}

val sc = project.stonecutterBuild
val loader = sc.branch.id
sc.constants["fabric"] = sc.current.project.contains("fabric")
sc.constants["neoforge"] = sc.current.project.contains("neoforge")

// Register the MC version part as a property tag so version-shared sections
// in stonecutter.properties.toml (e.g. ["1.20.1"]) resolve correctly.
sc.properties.tags(sc.current.project.substringAfter('-'))

val javaVersion = findProperty("java.version")?.toString() ?: error("No Java version specified")
val displayVersion = findProperty("display_version")?.toString() ?: error("No display version specified")

val isPreRelease = findProperty("prerelease")?.toString()?.lowercase() != "false"
val semVer = findProperty("semVer")?.toString()?.takeIf { it.isNotEmpty() } ?: "0.0.1-preview.0"

version = "$semVer+$displayVersion"
group = property("maven_group").toString()

base {
    archivesName.set("${property("archives_base_name")}-$loader")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
    withSourcesJar()
}

tasks.jar {
    includeLicense(base.archivesName.get())
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
    // only run tests once
    enabled = sc.current.isActive
}
