import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

plugins {
    id("java")
    id("java-library")
    id("jacoco")
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
val semVer = resolveSemVer()

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
    // Emit JaCoCo coverage (XML for CI/PR comment, HTML for humans + the IDE) right after the tests.
    finalizedBy(tasks.named("jacocoTestReport"))
}

// Coverage is only meaningful on the active variant (the only one whose `test` runs); the inactive
// branches have no exec data, so skip their reports rather than emit empty ones.
tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.test)
    onlyIf { sc.current.isActive }
    // Mixins only execute inside a live client/server (Tier 2/3), never in the JVM unit tests, so they
    // would sit at 0% and drag the denominator down. Excluded from coverage - kept in sync with the
    // aggregate exclude in stonecutter.gradle.kts. Rebuilt from the source set (rather than filtering
    // the convention value) so ordering with the jacoco plugin's own wiring can't clobber it.
    classDirectories.setFrom(
        sourceSets["main"].output.classesDirs.asFileTree.matching { exclude("**/mixins/**") }
    )
    doFirst { delete(reports.html.outputLocation) }
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

// Maven publishing applies ONLY to the `common` branch (the loader variants produce remapped mod
// jars for the platforms / jar-in-jar, not maven libraries). Published as `eunomia-common` under the
// per-variant version (`semVer+display_version`) so a consumer can pin the game version it needs.
// Only the ACTIVE variant registers a publication - the inactive ones aren't built, and running
// `publish` while switching the active variant is how the whole matrix reaches the repo.
if (loader == "common") {
    apply(plugin = "eunomia-publish")
    if (sc.current.isActive) {
        configure<PublishingExtension> {
            publications {
                create<MavenPublication>("maven") {
                    artifactId = "eunomia-common"
                    from(components["java"])
                }
            }
        }
    }
}
