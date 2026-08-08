# eunomia
A Java library for common methodology in Minecraft mods for configurations and configurations - mod loader agnostic and without other dependencies.

## Repo layout

The Gradle build — every subproject (`core`, `common`, `fabric`, `neoforge`, `paper`, `smoke`,
`buildSrc`, `gradle-conventions`), the wrapper and all build config — lives under [`java/`](java/).
Build and test from there:

```bash
cd java
./gradlew build                # all loaders + Paper
./gradlew test                 # all unit tests (core + paper + the active common variant)
./gradlew aggregatedCoverage   # unit tests + one merged JaCoCo report (build/reports/jacoco/aggregate)
./gradlew smokeTest            # Tier-3 FCGT client smoke (skips unless a variant enables fabricapi.semver)
./gradlew stageArtifacts       # build every loader variant into staging/ + a versions.json
```

## Consuming eunomia

eunomia publishes maven artifacts so another mod can depend on it:

- `de.zannagh.eunomia:eunomia-core` — the MC-free networking/framework library
- `de.zannagh.eunomia:eunomia-common` — the active game-version's common jar
- `de.zannagh.eunomia:eunomia-paper` — the shaded Paper plugin

```bash
cd java
./gradlew publish                              # -> mavenLocal (+ GitHub Packages when credentials are set)
./gradlew -p gradle-conventions publish        # the shared multiloader/stonecutter convention plugins
```

The shared build logic (the `multiloader-*` stonecutter convention plugins) is republished from
[`java/gradle-conventions`](java/gradle-conventions) as `de.zannagh.eunomia:eunomia-gradle-conventions`
so a consuming mod can reuse the multiloader setup instead of copying `buildSrc`.

See [`docs/networking.md`](docs/networking.md) for the networking framework guide.
