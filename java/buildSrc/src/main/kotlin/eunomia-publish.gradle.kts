plugins {
    id("maven-publish")
}

// Shared publishing targets for every eunomia maven artifact (:core, :common, :paper). Each module
// declares its own `publications { }` (the component differs - a plain java lib, a per-variant
// stonecutter jar, a shaded plugin jar); this convention only wires up WHERE they go so the repo
// config lives in one place.
//
// Local development publishes to mavenLocal (~/.m2) so a sibling mod (armor-hider) can consume
// eunomia via `mavenLocal()` with no credentials. CI additionally publishes to GitHub Packages,
// but ONLY when credentials are present - a plain clone that runs `publish` simply skips the remote
// repo instead of failing. Credentials come from `-Pgpr.user`/`-Pgpr.token` or the standard
// GITHUB_ACTOR/GITHUB_TOKEN env vars set on the Actions runner.
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
