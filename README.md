# eunomia
A Java library for common methodology in Minecraft mods for configurations and configurations - mod loader agnostic and without other dependencies.

## Repo layout

The Gradle build — every subproject (`core`, `common`, `fabric`, `neoforge`, `paper`, `smoke`,
`buildSrc`), the wrapper and all build config — lives under [`java/`](java/). Build and test from there:

```bash
cd java
./gradlew build          # all loaders + Paper
./gradlew :core:test     # the networking framework tests
```

See [`docs/networking.md`](docs/networking.md) for the networking framework guide.
