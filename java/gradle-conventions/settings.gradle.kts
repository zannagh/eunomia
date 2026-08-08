pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases/")
        mavenCentral()
        gradlePluginPortal()
    }
}

// Standalone build (NOT included in the main eunomia build): it exists solely to publish a maven
// copy of the multiloader/stonecutter convention plugins that live in ../buildSrc, so a sibling mod
// can consume them as a resolved plugin. Invoke it with the main wrapper via
//   ./gradlew -p gradle-conventions publishToMavenLocal   (or :publish for GitHub Packages)
rootProject.name = "eunomia-gradle-conventions"
