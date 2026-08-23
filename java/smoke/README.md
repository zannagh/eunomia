# :smoke — FCGT networking gametest

End-to-end validation of Eunomia networking on a **real Minecraft client** via the Fabric Client
Gametest API (`fabric-client-gametest-api-v1`, "FCGT"). It boots a client into a singleplayer world
(an in-process integrated server) and asserts the full packet path over the actual Minecraft wire:
the mixin-injected codecs, the dispatch mixins, and both transports.

The gametest itself lives at
`common/src/client/java/de/zannagh/eunomia/smoke/NetworkingSmokeTest.java` (stonecutter-gated behind
the `fcgt` constant). It asserts:

- **S2C on join** — the `PERMISSION` packet the server pushes on join was received.
- **C2S → S2C round trip** — the client's `PING` was answered with a `PONG`.
- **Capability handshake** — the client detected the server runs Eunomia and reports it receives the
  `PING` channel (see `ServerCapabilities`).

Because the example packets are themselves "additional packets defined with minimal code", a green run
proves the public add-a-packet API works across a live network — complementing the deterministic
`LoopbackHandshakeTest` / `ServerHandshakeTest` in `:core`, which already exercise the same routing,
codec and handler logic through real `gzip(json)` wire bytes without a client.

## Enabled versions

FCGT is on for every Fabric variant whose `["fabric-<mc>"]` section in `stonecutter.properties.toml`
pins `fabricapi.semver`. That is currently all seven versions whose fabric-api line ships
`fabric-client-gametest-api-v1`:

| variant | fabric-api pin | FCGT |
| --- | --- | --- |
| `fabric-1.21.4` | `0.119.4+1.21.4` | 4.x |
| `fabric-1.21.8` | `0.136.1+1.21.8` | 4.x |
| `fabric-1.21.10` | `0.138.4+1.21.10` | 4.x |
| `fabric-1.21.11` | `0.141.4+1.21.11` | 4.x |
| `fabric-26.1.2` (deobf) | `0.153.0+26.1.2` | 5.x |
| `fabric-26.2` (deobf) | `0.153.0+26.2` | 5.x |
| `fabric-26.3-snapshot-9` (deobf) | `0.158.0+26.3` | 6.x |

`fabric-1.20.1` / `1.21.1` / `1.21.2` / `1.21.3` are deliberately **not** pinned: their fabric-api
lines predate the module. Each of those sections carries a comment saying so - do not "helpfully"
add a pin there.

The presence of `fabricapi.semver` flips the `fcgt` stonecutter constant, the FCGT compile
dependency, the `fabric-client-gametest` entrypoint list (`fcgt_entries`), the `copyFcgtToMods`
run/mods provisioning and the `runClientGametest` task on.

> Changing that property changes a **stonecutter constant**. The active (vcs) variant -
> `fabric-26.2` - builds straight from the working-tree sources, so after adding or removing a pin
> you must run `./gradlew "Refresh active project"` or its sources keep the stale comment state and
> the gated test class stays commented out (fabric-loader then dies with
> `ClassNotFoundException: de.zannagh.eunomia.smoke.NetworkingSmokeTest`).

## Running

```bash
./gradlew :fabric:<variant>:runClientGametest      # e.g. :fabric:fabric-1.21.8:runClientGametest
```

Note the gradle path repeats the prefix: the stonecutter subproject is named after the whole TOML
section, so it is `:fabric:fabric-1.21.8`, not `:fabric:1.21.8`.

`runClientGametest` runs **every** registered entrypoint in one client launch. Narrow it with the
short ids from the `fcgtTestCatalog` in `multiloader-loom.gradle.kts`:

```bash
./gradlew :fabric:fabric-1.21.8:runClientGametest -Psmoke.fcgt.only=networking
```

An unknown id fails the build loudly rather than silently registering nothing.

Or drive the whole enabled matrix through the Tier-3 JUnit driver:

```bash
./gradlew :smoke:smokeTest                          # every enabled variant
./gradlew :smoke:smokeTest -Dsmoke.variant=fabric-26.2
```

FCGT swaps the client main loop for the test driver. The run config passes the three properties FCGT
needs, including `-Dfabric.client.gametest.disableNetworkSynchronizer=true` - **required**, because our
codec-injection mixin interfaces with packets at a low level that FCGT otherwise hard-asserts on. It
also sets `SDL_WINDOW_ACTIVATE_WHEN_SHOWN/RAISED=0` so the client window does not steal focus on macOS.

## The false-green trap (do not reintroduce)

FCGT is not a library you can merely put on the runtime classpath: fabric-loader has to *load it as a
mod* so its mixin config plugin runs. If the module jar is not physically in `<variant>/run/mods/`,
loader never applies those mixins, Minecraft boots **vanilla**, idles at the title screen and exits
**0** - `runClientGametest` reports success having tested nothing.

That is why `multiloader-loom.gradle.kts` resolves the module through a plain `fcgtRuntimeMod`
configuration and copies it into `run/mods` with `copyFcgtToMods`, and why

```kotlin
tasks.named("runClientGametest") { dependsOn(copyFcgtToMods) }
```

is **unconditional** - not gated behind `-Psmoke` or anything else. A plain configuration is used
rather than loom's `modClientRuntimeOnly` because plain configurations exist in deobf mode and loom's
`mod*` ones do not; that is what lets the 26.x deobf variants run FCGT at all.

`copyFcgtToMods` also provisions the fabric resource loader (`fabric-resource-loader-v0`, plus `-v1`
where the line publishes it). FCGT declares it as a *runtime* `fabric.mod.json` dependency, not a
Gradle-transitive one, so without it loader refuses to start:
`requires any version of fabric-resource-loader-v0, which is missing`.

## Proving a run actually ran

Exit 0 is not evidence. Check that:

- `<variant>/run/mods/` contains `fabric-client-gametest-api-v1-*.jar`, and
- the run log contains the test's own line
  `[smoke/fcgt] networking smoke PASSED: permission=..., pong='...', receivers=[...]`
  (or an `AssertionError` thrown from `NetworkingSmokeTest.runTest` - both prove FCGT dispatched the
  entrypoint).

A run that neither logs nor throws from `NetworkingSmokeTest` did not execute the test.
