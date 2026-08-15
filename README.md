# eunomia
A Java library for common methodology in Minecraft mods for configurations and networking - mod loader agnostic and without other dependencies. Easy to develop against by adding a Maven dependency for IDEs and a mod dependency for runtime.

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
