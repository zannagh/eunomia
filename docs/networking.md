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

## Store a per-player config server-side

A mod's per-player settings object (Armor Hider's `PlayerConfig`) is just a `ConfigurationItem` that also
carries the owning player's UUID — a `PlayerLinkedConfigurationItem`. Extend `PlayerLinkedConfigurationItemBase`
to get the id + change-flag plumbing for free, then let Eunomia keep the server-side map for you:

```java
public final class MyConfig extends PlayerLinkedConfigurationItemBase<MyConfig> {
    public int level;
    public MyConfig() {}
    public MyConfig(UUID id, int level) { super(id); this.level = level; }
    // ... the remaining ConfigurationItem methods (value/default, schema, migrate, codec) ...
}

public static final PacketType<MyConfig> SYNC =
        PacketType.serverbound("mymod", "config", MyConfig.class);

// One store, one wiring call: every SYNC a client sends is stored under its authenticated UUID.
var store = new ServerSidePlayerConfigStorage<>(MyConfig.class, id -> new MyConfig(id, 0))
        .handleOn(SYNC);
store.loadFrom(Path.of("config", "mymod-players.json"));   // survives restarts; empty if absent
```

Lookups are UUID-only — there is no name index. The store never returns `null` for a good query
(`getOrCreate` / `getOrDefault` fall back through the factory), and both `put` and load-time healing re-stamp
each config's own id to match the map key it lives under, so a client cannot store settings under someone
else's id. `toJson` / `applyJson` / `saveTo` / `loadFrom` round-trip through the same Eunomia `Gson` (and the
same config type adapters) that serialize the payload on the wire.

## Let an admin edit the server's Cloud Sync settings

Eunomia ships a ready-made channel for the one server-side thing an operator changes in game: the Cloud
Sync policy (`enableExternalFallback`, `externalServerAddress`, `preferExternalTransport`) that every
joining client reads off the capability handshake. The client half is UI-free, so a screen just calls it:

```java
// Read: the server answers with its current policy AND its own verdict on whether you may change it.
ServerSettingsClient.request(view -> {
    boolean readOnly = !view.editable();
    render(view.enableExternalFallback(), view.externalServerAddress(), readOnly);
});

// Write: the outcome is the server's, not yours.
ServerSettingsClient.submit(true, "https://sync.mymod.example", false, result -> {
    if (result.applied()) {
        toast("Saved");
    } else {
        toast(result.detail());   // DENIED, INVALID_ADDRESS, or UNAVAILABLE (no answer)
    }
});
```

Callbacks fire exactly once — on the network thread, or on the timeout scheduler if the server never
answers — so hop to the render thread before touching anything Minecraft owns.

### Permission checks belong on the server

`view.editable()` is a **rendering hint and nothing else**. The server re-runs its own permission check on
every request and again, independently, on every write — a write is never admitted because an earlier read
succeeded, since nothing stops a client from sending a write as its first packet. The serverbound payloads
carry no identity and no permission claim at all, so a forged admin flag is not "checked and rejected", it
is structurally unable to reach a decision: the acting player is whoever the authenticated connection says
they are (`ctx.senderId()`), and only that.

Write your own privileged packets the same way:

```java
CommunicationManager.onServerReceive(MyPackets.SET_MOTD, (payload, ctx) -> {
    // On the loaders: ServerUtil.isAdmin(player, server). On Paper: PermissionResolver#isAdmin.
    if (!myAuthority.isAdministrator(ctx)) {
        LOGGER.warn("Refused a MOTD change from {} ({}): not an administrator",
                ctx.senderName(), ctx.senderId());
        ctx.reply(MyPackets.RESULT, MyResult.denied());
        return;
    }
    // Validate the values here too. A check in the settings screen is a courtesy to the person typing;
    // this handler is reachable from any client, modified or not.
    ...
});
```

Non-admins get a genuine read-only snapshot rather than a refusal, because those three values are already
advertised unsolicited to every joining client in the handshake — withholding them here would buy nothing
but a worse screen. Writes, by contrast, are refused loudly and every accepted and refused write is logged
with the acting player.

Addresses are validated server-side with `RelayAddresses`, which delegates to the same normalisation
`RelayEndpoints` uses at dial time: an absolute `http`/`https` URL or a bare host (stored qualified with
`http://` — never `https://` — and stripped of trailing slashes), blank meaning "no relay". The scheme
comparison is case-insensitive, so `HTTPS://relay.example` is accepted and normalised rather than mistaken
for a foreign scheme; anything else carrying a `://` is refused before normalisation, since prefixing
`ftp://relay.example` with `http://` would otherwise produce a parseable address nobody meant.

A query string or a fragment is **refused, not stripped**. A relay base is never used whole — `RelayEndpoints`
concatenates `/health`, `/api/v<x.y>/…` and `/ws?…` onto it — so a base of `http://relay.example?a=b` would be
dialled as `http://relay.example?a=b/health`, with the path swallowed by the query. A fragment is worse:
`http://relay.example#@evil.example` reads to a human as if it addressed `evil.example`. Silently stripping
either would store an address that is not the one the operator typed. Whitespace anywhere, and anything over
`RelayAddresses.MAX_LENGTH` (256) characters, is refused for the same reason.

Plain `http` is allowed on purpose — a relay on a LAN or behind a TLS-terminating proxy has always been
dialable, so refusing it would reject addresses the transport is perfectly happy with.

The **client** runs the identical check on the address a server advertises in the handshake, in
`EunomiaSyncSettings`. An advertised address that fails it reads as "the server said nothing" and falls
through the precedence chain — see [`configuration.md`](configuration.md). One normalisation, three call
sites, so a value refused in one place is refused in all three.

Both serverbound packets go out with `SendOptions.ALWAYS` rather than the handshake-gated default: a
settings screen is opened deliberately, by one player, on a server they administer, and the client-side
timeout (`ServerSettingsClient.RESPONSE_TIMEOUT_SECONDS`) reports `UNAVAILABLE` instead of leaving a
callback that never fires.

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
- `:core` — `ServerSettingsExchangeSecurityTest`, `RelayAddressesTest`: the admin settings channel's
  security properties (non-admin writes refused, forged admin flags ignored, addresses validated, an
  accepted write persisted *and* re-advertised to the next handshake), all through real wire bytes.
- `:core` — `EunomiaServerConfigTest`, `ServerHelloPayloadCompatibilityTest`: an untouched server config
  advertises no opinion, and a protocol-v1 server still decodes.
- `:common` — `EunomiaSyncSettingsTest`, `EunomiaConfigMigrationTest`, `SyncDiagnosticsTest`: the four-rung
  precedence chain (including an invalid advertised address falling through), the `1.0.0 -> 1.1.0` client
  migration, and the toast suppression predicates.
- `:common` — `DedicatedServerSafetyTest`: walks the whole compiled `:common` main and `:core` output and
  asserts no class a dedicated server loads names a client-only type. Runs on the active stonecutter variant.
- `:paper` — `PaperWireContractTest`: the plugin speaks the same channels and format as the loaders;
  `PaperAdminSettingsSecurityTest`: the same admin rules hold on the Bukkit side;
  `PaperSyncPolicyAdvertisementTest`: the Bukkit side advertises the same opinion-shaped policy.
- `:smoke` — the FCGT client gametest for live end-to-end validation: boots a real Minecraft client
  on every Fabric variant that pins `fabricapi.semver` (see `java/smoke/README.md`).

> The Gradle build lives under `java/` (all subprojects, the wrapper and the build config). Run it from
> there: `cd java && ./gradlew :core:test`. Gradle module paths (`:core`, `:paper`, …) are unaffected by
> the folder; only filesystem paths gain the `java/` prefix.
