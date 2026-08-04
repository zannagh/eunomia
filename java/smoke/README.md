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

## Enabling and running

The harness is **wired but dormant by default**. To turn it on for a game version, add its fabric-api
version under that version's block in `stonecutter.properties.toml`:

```toml
["1.21.8"]
# ...
fabricapi.semver = "0.136.1+1.21.8"
```

Presence of `fabricapi.semver` flips the `fcgt` stonecutter constant, the FCGT dependencies, the
`fabric-client-gametest` entrypoint (`fcgt_entries` in `multiloader-loom.gradle.kts`) and the
`runClientGametest` run task on. Then, with that version active:

```bash
./gradlew :fabric:<variant>:runClientGametest
```

FCGT swaps the client main loop for the test driver. The run config passes the three properties FCGT
needs, including `-Dfabric.client.gametest.disableNetworkSynchronizer=true` — **required**, because our
codec-injection mixin interfaces with packets at a low level that FCGT otherwise hard-asserts on.

## Known caveat

Enable it on a **non-deobfuscated** variant (the plain `1.21.x` buckets, not the `26.x` ones that use
`build.deobfuscated.gradle.kts`). In deobf mode loom does not register the `mod*` configurations and
registers `FabricApiExtension` late, so `fabricApi.module("fabric-client-gametest-api-v1", …)` cannot
be resolved at configuration time. The wiring uses the loom-standard `fabricApi.module(...)` /
`modClientRuntimeOnly` path, which resolves cleanly on the non-deobf variants.
