pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases/")
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.1"
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
    id("com.gradle.develocity") version("4.3.2")
}

// Build-scan publishing is OPT-IN: only when EUNOMIA_BUILD_SCAN_PUBLISH=true is set do we agree to
// the Gradle Terms of Use and upload the scan. This keeps a plain clone from ever publishing scans
// (and silences the ToU warning on ordinary local builds); CI sets it on the runner.
val publishBuildScan = "true".equals(System.getenv("EUNOMIA_BUILD_SCAN_PUBLISH"), ignoreCase = true)
develocity {
    buildScan {
        termsOfUseUrl = "https://gradle.com/help/legal-terms-of-use"
        if (publishBuildScan) {
            termsOfUseAgree = "yes"
        }
        publishing.onlyIf { publishBuildScan }
    }
}

stonecutter {
    kotlinController = true
    centralScript = "build.gradle.kts"

    create(rootProject, file("versions.json5"))
}

// MC-free, plain-Java networking core shared by the loaders (:common) and the Bukkit-family
// plugin (:paper). Holds the game-version-agnostic packet model + JSON payload resolution, so
// the Paper side reuses these definitions instead of hand-written JSON. Sibling subproject, not
// a stonecutter branch - one artifact covers every game version.
include(":core")

// PaperMC/Bukkit server-side plugin. Reuses :core for packet definitions and payload resolution
// (no typed-out JSON), talking the wire protocol over Bukkit plugin-messaging channels. One jar
// covers 1.20.1 through 26.x. Sibling subproject, not a stonecutter branch.
include(":paper")

// FCGT smoke suite - IDE-visible JUnit suite (its own `smokeTest` task, kept out of `check`) that
// forks runClientGametest for each FCGT-enabled Fabric variant to exercise the networking handshake
// end to end on a real client. Skips when no variant pins fabricapi.semver. Sibling subproject.
include(":smoke")
