plugins {
    `kotlin-dsl`
    `maven-publish`
}

// Published as `de.zannagh.eunomia:eunomia-gradle-conventions`; each precompiled script plugin also
// gets a `<id>` plugin-marker artifact (multiloader-common / multiloader-loader / multiloader-loom /
// eunomia-publish) so a consumer applies them by id after adding this build to its pluginManagement.
group = "de.zannagh.eunomia"
version = providers.gradleProperty("semVer").orNull?.takeIf { it.isNotEmpty() } ?: "0.0.1-preview.0"

// Same repositories buildSrc needs to resolve stonecutter/loom/gson at compile time.
repositories {
    maven("https://maven.fabricmc.net/")
    maven("https://maven.neoforged.net/releases/")
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("dev.kikugie:stonecutter:0.9.1")
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("net.fabricmc:fabric-loom:${property("loom_version")}")
}

// ../buildSrc is the source of truth for the convention plugins (it drives eunomia's own build); this
// build republishes a COPY of them under src/main/kotlin. The copy is required because kotlin-dsl only
// discovers precompiled `*.gradle.kts` plugins from the conventional src/main/kotlin at configuration
// time - an external srcDir is silently ignored (the .gradle.kts never become plugins). Re-sync the
// copy after editing buildSrc with `./gradlew -p gradle-conventions syncConventions`; `check` verifies
// the two are identical so drift fails the build.
val buildSrcKotlin = layout.projectDirectory.dir("../buildSrc/src/main/kotlin")
val conventionSources = layout.projectDirectory.dir("src/main/kotlin")

val syncConventions by tasks.registering(Sync::class) {
    group = "build setup"
    description = "Refresh the published copy of the convention plugins from ../buildSrc."
    from(buildSrcKotlin)
    into(conventionSources)
}

val checkConventionsInSync by tasks.registering {
    group = "verification"
    description = "Fail if the published convention-plugin copy has drifted from ../buildSrc."
    doLast {
        val src = buildSrcKotlin.asFile
        val copy = conventionSources.asFile
        val drift = src.walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }.mapNotNull { s ->
            val rel = s.relativeTo(src).path
            val c = copy.resolve(rel)
            if (!c.exists() || c.readText() != s.readText()) rel else null
        }.toList()
        if (drift.isNotEmpty()) {
            error("gradle-conventions is out of sync with ../buildSrc for: $drift. " +
                    "Run `./gradlew -p gradle-conventions syncConventions`.")
        }
    }
}

tasks.named("check") { dependsOn(checkConventionsInSync) }

publishing {
    repositories {
        mavenLocal()

        val gprUser = providers.gradleProperty("gpr.user")
            .orElse(providers.environmentVariable("GITHUB_ACTOR"))
        val gprToken = providers.gradleProperty("gpr.token")
            .orElse(providers.environmentVariable("GITHUB_TOKEN"))

        if (gprUser.isPresent && gprToken.isPresent) {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/zannagh/eunomia")
                credentials {
                    username = gprUser.get()
                    password = gprToken.get()
                }
            }
        }
    }
}
