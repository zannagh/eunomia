# Contributing

Feel free to fork and PR or create a branch within the repo and create a PR into main.

The main branch is protected against direct pushes - any changes should be PR'd.

Since this project is under MIT, feel free to take the code and do as you please with it as long as you're referencing
this repository and the authors.

## Repo layout

The Gradle build - every subproject (`core`, `common`, `fabric`, `neoforge`, `paper`, `smoke`,
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

## Multi-Version Development

This project uses [Stonecutter](https://stonecutter.kikugie.dev/) to build for multiple Minecraft versions from a
single codebase. Version-specific code uses Stonecutter's conditional syntax:

```java
//? if >= 1.21.9
useNewApi();
//? if < 1.21.9
/*useOldApi();*/
```

All versions are built from the `main` branch - there are no separate version branches.

## Versioning

[GitVersion](https://gitversion.net/) handles semantic versioning automatically (see `../buildSrc/GitVersion.yml`).

- Prereleases use the format `x.x.x-pre.N`
- Version bumps are controlled via commit messages: `+semver: major`, `+semver: minor`, `+semver: patch`
- Commits prefixed with `ci:`, `docs:`, `build:`, or `chore:` do not trigger releases

## Community

Join the [Discord server](https://discord.gg/AMwbYqdmQb) for discussion and support.
