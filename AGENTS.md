# AGENTS.md

Instructions for AI agents working in this repository. Humans: start with
[CONTRIBUTING.md](.github/CONTRIBUTING.md) — repo layout, the Gradle commands, Stonecutter's
multi-version syntax and how GitVersion decides releases. Everything there applies here too; this file
only adds what an agent has to actively watch for.

## The one rule that cannot be inferred from the code

**eunomia ships two halves that are deployed separately**: the Minecraft mod (Java, `java/`) and the
relay server (C#, `csharp/`, deployed as `ghcr.io/zannagh/eunomia/eunomia-server`). Server operators
update those on their own schedule. Nothing in either build checks that they agree.

So:

> **If a change means a deployed relay has to be upgraded, the MINOR version must be bumped, and the
> change must say so out loud.**

Bump it the way GitVersion expects — put `+semver: minor` in the commit message (see
[CONTRIBUTING.md § Versioning](.github/CONTRIBUTING.md#versioning)).

### Agents: you must tell the human

Whenever you make a change that requires a server upgrade, **say so explicitly in your final summary**,
in as many words: *"this needs a relay upgrade — operators must update their servers."* Do not bury it
in a commit body. The person reading your summary is usually the person who has to redeploy, and they
have no other way to find out: see *How an operator finds out* below, which is "they don't".

State the opposite too. "No relay upgrade needed" is a useful thing to hear, and it is cheap to check.

## When IS a server upgrade required?

The relay's typed surface is small. Deserialised by C#, so a change is a contract change:

| What | Java | C# |
|---|---|---|
| REST body, client → relay | `core/.../clients/PacketEnvelope.java` | `Api/Models.V0_3/PacketEnvelope.cs` |
| WS frame wrapper, relay → client | `core/.../clients/WsFrame.java` | `Core/Communication/WsFrame.cs` |
| Snapshot on connect | `core/.../keyed/StoreSyncPayload.java` | `Core/Storage/StoreSyncPayload.cs` |
| Handshake query `id` / `scope` / `name` / `v` | `core/.../clients/RelayEndpoints.java` | `Api/Middlewares/WebSocketMiddleware.cs` |

**Upgrade required** when you change any of those, or the API version path (below), or add/change a
route the client calls.

**No upgrade required** for anything that only travels between mod instances — client ↔ Minecraft
server. That is all of `networking/handshake/*` (`ClientHelloPayload`, `ServerHelloPayload`),
all of `networking/admin/*` (`ServerSettings*Payload`), `ServerSyncPolicy`, and `networking/examples/*`.
The relay never sees these types. Worked example: `feat/enforceable-cloud-sync` added fields to
`ServerHelloPayload`, `ServerSettingsPayload` and `ServerSettingsWritePayload` and a component to
`ServerSyncPolicy` — **no relay upgrade**, zero C# files touched, `PROTOCOL_VERSION` unchanged.

One nuance: a *serverbound* mod packet can physically reach the relay inside
`PacketEnvelope.payload`, but that field is opaque (`JsonElement`, stored verbatim). Adding a field to
such a type is carried losslessly and never inspected, so it is not a contract change.

### The API version path

`semVer`'s **major.minor** becomes the upstream path segment: `/api/v<major>.<minor>/...`
(`gradle.properties` → `BuildInfo.VERSION` → `ApiVersion.CURRENT` → `RelayEndpoints`). The C# side
serves versions **hardcoded in `[ApiVersion]` attributes** on its controllers — they are *not* derived
from the assembly version.

**Therefore any minor bump is itself a server upgrade**, whether or not you touched a DTO: the client
starts calling `/api/v<new>` and the relay must have a controller serving it. When you bump the minor,
check `csharp/src/Eunomia.Server.Api/Controllers.V<ver>/` and
`Api/Versioning/EunomiaApiVersions.cs` and add the version there in the same change.

## Nothing will catch you

There is **no CI check** tying the two halves together. `build.yml` (Gradle) and `csharp-ci.yml`
(`dotnet test`) share no artifact and no schema step. The near-misses do not qualify: the C#
controller tests construct `PacketEnvelope` as a C# object rather than parsing Java-produced JSON, and
the Java fallback E2E drives a Java mock relay, not the real server. The format-compat tests read
committed fixtures that nothing regenerates from the Java build.

A Java rename of `PacketEnvelope.scope` ships green through both pipelines and breaks every deployed
relay. The contract is held by comments and by review. Treat it accordingly.

## How an operator finds out their relay is stale — they don't

Worth knowing, because it is why the rule above is strict rather than advisory:

- `/health` returns a bare `"ok"` — no version, no headers.
- A mod on a newer API version still passes the health probe, then gets its WebSocket closed with
  **4001**. The client logs a **WARN** and silently falls back to the in-game transport. No toast, no
  player-visible error. Cloud Sync just stops working.
- The dashboard does show the running relay version (top bar of `MainLayout`, every page), but nothing
  compares it to anything. The operator must already know which mod version they need.

Unknown JSON fields are ignored on both sides (System.Text.Json and Gson defaults, pinned by
`ServerHelloPayloadCompatibilityTest`). Additive changes are therefore usually safe; renames, removals
and type changes are not.

## Working style

Match the surrounding code. This repo comments the **why**, not the what — keep that up, especially
for anything version-gated or protocol-related. Braces on every control-flow statement, plain camelCase
private fields with no underscore prefix, imports outside the namespace, and no file over 300 lines.

Run `cd java && ./gradlew build test` before claiming a change works, and say which variants you
actually verified — `common` is built per Minecraft version and a green 26.2 says nothing about 1.20.1.
