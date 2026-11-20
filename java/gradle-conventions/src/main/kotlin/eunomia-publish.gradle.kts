plugins {
    id("maven-publish")
}

// Shared publishing targets for every eunomia maven artifact (:core, :common, :paper). Each module
// declares its own `publications { }` (the component differs - a plain java lib, a per-variant
// stonecutter jar, a shaded plugin jar); this convention only wires up WHERE they go so the repo
// config lives in one place.
//
// Local development publishes to mavenLocal (~/.m2) so a sibling mod (armor-hider) can test against an
// unreleased eunomia via `mavenLocal()`. CI additionally publishes to the self-hosted Reposilite
// (https://maven.zannagh.me/releases, anonymous read), but ONLY when credentials are present - a plain
// clone that runs `publish` simply skips the remote repo instead of failing. Credentials come from the
// MAVEN_USERNAME / MAVEN_PASSWORD environment variables (GitHub Actions secrets of the same name);
// they are deliberately not Gradle properties so they never land in a gradle.properties file.
publishing {
    repositories {
        mavenLocal()

        val mavenUser = providers.environmentVariable("MAVEN_USERNAME")
        val mavenPassword = providers.environmentVariable("MAVEN_PASSWORD")

        if (mavenUser.isPresent && mavenPassword.isPresent) {
            maven {
                name = "Reposilite"
                url = uri("https://maven.zannagh.me/releases")
                credentials {
                    username = mavenUser.get()
                    password = mavenPassword.get()
                }
            }
        }
    }
}
