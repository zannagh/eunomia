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
        // Compare EVERY file, not just `*.kt`. The original check filtered on `.kt`, which silently
        // excluded the `*.gradle.kts` precompiled script plugins - i.e. every convention plugin this
        // build exists to publish (multiloader-common/-loader/-loom, eunomia-publish). The guard could
        // therefore never see drift in the very files it was written to protect, and drift did in fact
        // accumulate unnoticed (the `:core` shading block in multiloader-loader and the whole Tier-1
        // test-dependency block + mavenCentral() in multiloader-common were missing from the copy).
        // `syncConventions` always copied the whole tree, so an unfiltered comparison is the correct
        // mirror of what the Sync task actually does - and it stays correct if a new file type
        // (.java, .properties, a resource) is ever added to buildSrc.
        // Compared as bytes so line-ending or encoding drift cannot slip past a text comparison.
        fun scan(root: java.io.File) = root.walkTopDown()
            .filter { it.isFile && it.name != ".DS_Store" }
            .map { it.relativeTo(root).path }
            .toSortedSet()

        val missingOrChanged = scan(src).filter { rel ->
            val c = copy.resolve(rel)
            !c.exists() || !c.readBytes().contentEquals(src.resolve(rel).readBytes())
        }
        // Sync deletes extraneous files on the copy side, so a file that exists ONLY in the copy is
        // drift too - and was likewise invisible to the old check.
        val extraneous = scan(copy).filterNot { src.resolve(it).exists() }

        if (missingOrChanged.isNotEmpty() || extraneous.isNotEmpty()) {
            val details = buildString {
                if (missingOrChanged.isNotEmpty()) {
                    append("\n  differs from / missing in the published copy: ")
                    append(missingOrChanged.joinToString(", "))
                }
                if (extraneous.isNotEmpty()) {
                    append("\n  present only in the published copy: ")
                    append(extraneous.joinToString(", "))
                }
            }
            error("gradle-conventions is out of sync with ../buildSrc:$details\n" +
                    "../buildSrc is the source of truth. " +
                    "Run `./gradlew -p gradle-conventions syncConventions`.")
        }
    }
}

tasks.named("check") { dependsOn(checkConventionsInSync) }

// ...but nothing in CI runs `-p gradle-conventions check`: the publish-maven workflow invokes
// `-p gradle-conventions publish` directly, and `publish` has no path to `check`. So the guard was
// wired only to a task nobody called, which is the second half of how the drift survived. Gate every
// publish on it as well - shipping a copy that does not match the plugins eunomia itself builds with
// is exactly the failure this task exists to prevent.
tasks.withType<AbstractPublishToMaven>().configureEach { dependsOn(checkConventionsInSync) }

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
