<p align="center">
  <img src="images/eunomia-logo.png" alt="Eunomia logo" width="160" />
</p>

# eunomia
A Java library for common methodology in Minecraft mods for configurations and networking - mod loader agnostic and without other dependencies. Easy to develop against by adding a Maven dependency for IDEs and a mod dependency for runtime.

## Consuming eunomia

eunomia publishes maven artifacts so another mod can depend on it:

- `de.zannagh.eunomia:eunomia-core` - the MC-free networking/framework library
- `de.zannagh.eunomia:eunomia-common` - the active game-version's common jar
- `de.zannagh.eunomia:eunomia-paper` - the shaded Paper plugin

`eunomia-common` is game-version-specific: its version is `<semVer>+<display_version>` and consumers
take the `dev` classifier (Mojang-mapped, so it compiles on every variant). `eunomia-core` is
MC-free and version-independent, so it is the same artifact everywhere.

```kotlin
// build.gradle.kts, on the consuming mod
dependencies {
    implementation("de.zannagh.eunomia:eunomia-core:<ver>")
    // Fabric / NeoForge 1.20.1-era:   ...:eunomia-common:<ver>+mc-1.20.0-1:dev
    // classic Forge 1.20.1:
    implementation("de.zannagh.eunomia:eunomia-common:<ver>+mc-1.20.1-forge:dev")
}
```

The artifacts are hosted on a self-hosted Maven repository, **anonymous read - no token needed**:

```kotlin
repositories {
    maven("https://maven.zannagh.me/releases") {
        content { includeGroup("de.zannagh.eunomia") }
    }
}
```

Coordinates are `de.zannagh.eunomia:eunomia-core:<ver>` and
`de.zannagh.eunomia:eunomia-common:<ver>+<display_version>:dev` (plus `eunomia-paper` and
`eunomia-gradle-conventions`). Building eunomia locally and running `./gradlew publish` also puts the
same artifacts in `mavenLocal()`, which is handy for testing an unreleased change against a consumer.

Publishing (maintainers): the `Publish (Maven)` workflow runs on every published GitHub release and
reads the `MAVEN_USERNAME` / `MAVEN_PASSWORD` repository secrets. To backfill an existing release, run
it manually (Actions > Publish (Maven) > Run workflow) with the `tag` input, e.g. `v0.3.15`.

### Loader support

| Loader | Minecraft | Notes |
| --- | --- | --- |
| Fabric | 1.20.1 - 26.x | |
| NeoForge | 1.21.1 - 26.x | |
| Forge (classic / LexForge 47.x) | **1.20.1 only, by design** | 1.20.1 is the last release where classic Forge is the loader people run; 1.20.2+ is NeoForge's. See [`docs/networking.md`](docs/networking.md#classic-forge-1201-only). |
| Paper/Bukkit/Purpur | 1.20.1 - 26.x | one version-agnostic plugin jar |

```bash
cd java
./gradlew publish                              # -> mavenLocal (+ maven.zannagh.me/releases when MAVEN_USERNAME/MAVEN_PASSWORD are set)
./gradlew -p gradle-conventions publish        # the shared multiloader/stonecutter convention plugins
```

The shared build logic (the `multiloader-*` stonecutter convention plugins) is republished from
[`java/gradle-conventions`](java/gradle-conventions) as `de.zannagh.eunomia:eunomia-gradle-conventions`
so a consuming mod can reuse the multiloader setup instead of copying `buildSrc`.

See [`docs/networking.md`](docs/networking.md) for the networking framework guide.
See [`docs/toasts.md`](docs/toasts.md) for the client toast API guide.
See [`docs/configuration.md`](docs/configuration.md) for the fluent configuration API and the sync
settings precedence chain.
