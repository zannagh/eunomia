# Eunomia networking

A game-version-agnostic, loader-agnostic packet framework. You define a packet as a plain Java class
and register a handler; Eunomia does the rest — on Fabric, NeoForge and Paper/Bukkit/Purpur, from
Minecraft 1.20.1 to 26.x, with **no Fabric API dependency** and no `CustomPacketPayload`/`StreamCodec`
boilerplate in your code.

## Define a packet

A packet payload is just a POJO (public fields, a no-arg constructor). Declare a `PacketType` for it:

```java
public class SyncPayload {
    public String key;
    public int value;
    public SyncPayload() {}
    public SyncPayload(String key, int value) { this.key = key; this.value = value; }
}

public static final PacketType<SyncPayload> SYNC =
        PacketType.serverbound("mymod", "sync", SyncPayload.class);   // or .clientbound / .bidirectional
```

The `namespace:path` is the channel identity, shared verbatim by every platform.

## Handle it

```java
// Server side (runs in Eunomia.init on the loaders, onEnable on Paper):
CommunicationManager.onServerReceive(SYNC, (payload, ctx) -> {
    myStore.put(ctx.senderId(), payload);
    ctx.reply(ACK, new AckPayload("stored"));           // reply to just the sender
    CommunicationManager.broadcastExcept(ctx.senderId(), SYNC, payload);   // fan out to everyone else
});

// Client side (runs in EunomiaClient.init):
CommunicationManager.onClientReceive(ACK, (ack, ctx) -> applyAck(ack));
```

That is the whole surface for adding a packet + handler — one call each. See
`ExampleServerHandlers` / `ExampleClientHandlers` and the `eunomia:example_ping` / `example_pong` /
`permission` packets for a working reference.

## Send it

```java
CommunicationManager.sendToServer(SYNC, new SyncPayload("hp", 20));   // client -> server
CommunicationManager.sendToPlayer(uuid, ACK, new AckPayload("hi"));   // server -> one client
CommunicationManager.broadcast(ACK, snapshot);                        // server -> all
```

Direction is enforced: sending a `serverbound` packet to a client (or vice-versa) throws, catching the
mistake at the call site.

## Detect whether the server speaks Eunomia

A client can ask, per connection, whether the server it joined runs Eunomia and whether it has a
receiver for a specific packet — the decision point for falling back to a custom communications server:

```java
CommunicationManager.serverCapabilities().onResolved(caps -> {
    if (!caps.isPresent()) {
        // The MC server does not run Eunomia at all.
    } else if (!caps.supports(MyPackets.SYNC)) {
        // Eunomia is present but this mod's server half is not installed / has no SYNC handler.
    }
});
```

The handshake runs automatically on join (a `eunomia:hello` probe answered by `eunomia:hello_ack`
carrying the server's receiver channels); "resolved" means an ACK arrived or the probe timed out.

## Architecture

```
:core   (plain Java, no Minecraft)   PacketType, CommunicationManager, PayloadCodec (gzip+json),
                                     the handshake, the example packets. One artifact, every version.
  │
  ├── :common / :fabric / :neoforge  loader adapter: EunomiaPayload + StreamCodec, the payload-packet
  │                                  codec-injection mixins, the dispatch mixins, MC transports.
  │
  └── :paper                         Bukkit plugin: plugin-messaging transport, force-subscribe on
                                     join, reusing :core for the exact same definitions + resolution.
```

The single `CommunicationManager` owns registration, routing and dispatch and is transport-agnostic:
platforms install a registration listener and a transport, and feed inbound bytes to `dispatch*Raw`.
That is exactly the seam a future **non-game (HTTP) server** plugs into — it registers the same
`PacketType`s, feeds request bodies to `dispatchServerboundRaw`, and installs its own transport, so a
client whose MC server lacks the mod can be pointed at it instead.

One wire format everywhere (`PayloadCodec` = `gzip(json)`), so a payload a Fabric client puts on the
wire is byte-for-byte what the Paper plugin decodes.

## Tests

- `:core` — `PayloadCodecTest`, `CommunicationManagerTest`, `LoopbackHandshakeTest`,
  `ServerHandshakeTest`: the full routing/codec/handler/handshake logic through real wire bytes.
- `:paper` — `PaperWireContractTest`: the plugin speaks the same channels and format as the loaders.
- `:smoke` — the FCGT client gametest for live end-to-end validation (see `java/smoke/README.md`).

> The Gradle build lives under `java/` (all subprojects, the wrapper and the build config). Run it from
> there: `cd java && ./gradlew :core:test`. Gradle module paths (`:core`, `:paper`, …) are unaffected by
> the folder; only filesystem paths gain the `java/` prefix.
