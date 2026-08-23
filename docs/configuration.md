# Configuring Eunomia

Eunomia works with no configuration at all: a plain install talks over the in-game transport, sends
nothing off-box, and puts a small "Eunomia Settings" button on vanilla's online options screen. This
page is about the cases where that is not what your mod wants.

Everything here is fluent and chainable, and there are exactly two entry points:

| Chain | Source set | Covers |
| --- | --- | --- |
| `Eunomia.configure()` | common | client-side sync defaults, toasts on/off, settings button on/off |
| `EunomiaClient.configure()` | client | all of the above, plus the settings button's target `Screen` and label |

`EunomiaClient.configure()` is a superset — it delegates to the common builder internally — so a
client-only initializer can express everything in one chain.

## Configure from your mod initializer

Nothing is written until `apply()`. Build the chain, then commit it:

```java
public final class MyMod {
    public static void init() {
        Eunomia.configure()
                .externalFallback(true)
                .externalServerAddress("https://sync.mymod.example")
                .toasts(false)
                .apply();
    }
}
```

**Order does not matter.** Call `configure()` before or after `Eunomia.init()` / `EunomiaClient.init()`
— every value lands in a holder that is read at the point of use, never snapshotted during init. The
settings button reconciles its own registration, so even moving or suppressing it after the client
already installed it does the right thing.

A builder is single-use. Calling a setter on a chain you already applied throws
`IllegalStateException` rather than silently doing nothing, so the one mistake this shape invites —
forgetting `apply()` and later "fixing" it by adding another call — fails loudly instead.

## Move or rename the settings button

Eunomia's entry button lives on `OnlineOptionsScreen` by default. Point it at your own options screen
and give it your own name:

```java
public final class MyModClient {
    public static void init() {
        EunomiaClient.configure()
                .settingsButton(MyModOptionsScreen.class)
                .settingsButtonLabel(Component.translatable("mymod.options.sync"))
                .apply();
    }
}
```

Screen matching is on the **exact runtime class**, so pass the concrete class the player actually
opens — registering a base class does not pick up its subclasses.

Eunomia's own lang file covers only Eunomia's own strings, so the label component must resolve in your own
namespace.

## Suppress the built-in button and place your own

If your mod already has an entry point into Eunomia's settings, turn the built-in one off and open the
screen yourself:

```java
EunomiaClient.configure().suppressSettingsButton().apply();

// wherever your own widget is pressed - open the screen with whatever your target
// version's Minecraft screen setter is called:
Minecraft minecraft = Minecraft.getInstance();
minecraft.setScreen(new EunomiaSettingsScreen(parent, minecraft.options));
```

`Eunomia.configure().settingsButton(false)` does the same thing from a common initializer, for a mod
that keeps all of its configuration in one place. Suppression is honoured whenever it is set: before
client init the button is simply never installed, after client init it is unregistered again.

## Turn Eunomia's toasts off

One switch covers every toast Eunomia raises, its own included:

```java
Eunomia.configure().toasts(false).apply();
```

This is the same flag as `EunomiaToasts.setEnabled(false)` — the fluent call is just the version you
can make from a common initializer. See [`toasts.md`](toasts.md) for the toast API itself.

## Ship sync defaults with your mod pack

The three external-transport settings resolve through a **four-level precedence chain**, highest first:

1. **The player's override** — a non-null field in `config/eunomia-client.json`, i.e. a deliberate
   choice the player made in the settings screen. Nothing overrides a player who spoke up.
2. **The server-advertised policy** — what the joined server sent in the capability handshake, from its
   `config/eunomia-server.json`. An operator's policy beats a mod's preference.
3. **Your mod's default** — what `Eunomia.configure()` writes. This is the rung a mod pack ships on.
4. **The framework default** — `enableExternalFallback = false`, `preferExternalTransport = false`,
   `externalServerAddress = https://eunomia.zannagh.me`.

Rungs one to three all default to "no opinion", so a plain install lands on rung four and nothing
leaves the machine — the relay is never contacted unless somebody in the chain switched the fallback on.

The chain is resolved **per setting, not per rung**: a server that advertises only an address leaves the
fallback switch to your mod, and vice versa.

```java
Eunomia.configure()
        .externalFallback(true)                                 // opt the pack into the relay
        .externalServerAddress("https://sync.mymod.example")    // ...and point it at your own
        .preferExternalTransport(false)                         // but still prefer the in-game path
        .apply();
```

Pass `null` to any of the three to clear an opinion again, or chain `resetSyncDefaults()` first to wipe
every mod-level opinion before this chain's values land.

Never read these settings off a config record yourself: a field there is only ever *one* of the four
sources. `EunomiaSyncSettings.externalFallbackEnabled()`,
`EunomiaSyncSettings.externalServerAddress()` and `EunomiaSyncSettings.preferExternalTransport()` are
the resolved values, and the only correct thing to read.

## Know when the server actually outranks you

Rung two is the one worth being precise about, because "the server" is not a single answer.

`EunomiaServerConfig` is at schema `1.1.0` and its three knobs are boxed and default to `null` — "this
operator never said anything about it". A server whose `config/eunomia-server.json` has never been touched
therefore advertises `ServerSyncPolicy.UNKNOWN`, which carries no opinion at all and is indistinguishable
from a server too old to have the fields. Rung two is simply absent for that connection and **your rung-three
defaults apply**, then the framework defaults. This is the whole point of the shape: before it existed an
untouched server file advertised `fallback=false` plus the framework address, which silently erased rung three
for every client on every Eunomia server in the world.

A server whose operator *has* configured it does advertise, and then it outranks you:

- an operator who deliberately switched Cloud Sync **off** beats your `externalFallback(true)`. That is not a
  bug to work around — it is a server owner saying player data does not leave their box, and there is no API
  for a mod to override it. Only the player, at rung one, can;
- an operator who set an address beats yours, for the same reason.

There is no way for your mod to tell the two cases apart, and it should not need to: read the resolved value
and act on it.

An advertised address is **validated on arrival** by `EunomiaSyncSettings`, with the same `RelayAddresses`
check the server applies before storing one. An address that could never be dialled reads exactly as "the
server said nothing" and falls through to your default — it is not a hard failure. A server with a typo in its
config is a reason to ignore that one value, not a reason to take a player's sync away.

## Upgrade a player's config from schema 1.0.0

`EunomiaConfig` (the client file, `config/eunomia-client.json`) is at `1.1.0`, and its `1.0.0 -> 1.1.0`
migration decides *intent* for keys that never recorded any:

- a recorded `enableExternalFallback: true` survives as a real override. Nothing in 1.0.0 switched Cloud Sync
  on by itself, so a `true` can only have come from a player who went looking for the setting;
- a recorded `enableExternalFallback: false` becomes **inherit** (`null`), not a permanent "no". `false` was
  the 1.0.0 *default* — it is what every file said the moment it was created, whether or not its owner had
  ever opened the settings screen. Treating that as a decision would freeze every early adopter out of reach
  of their mod pack's and their server's defaults forever;
- a blank address collapses to inherit; the new `preferExternalTransport` knob has no 1.0.0 counterpart and
  starts unset.

**Be aware of what the second bullet can do.** A player whose 1.0.0 file said `false` and who genuinely meant
it can find Cloud Sync switched *on* the next time they join a server, or run a mod pack, whose defaults ask
for it — their data starting to leave their machine without them touching anything. That is a deliberate
trade, and the mitigation is the "new cloud sync relay" toast: the same join records a relay host the client
has never used, so the toast fires, names the host and points at the settings screen. A player who still wants
"no" sets it again, and this time it is recorded as a real override that no server can outrank. Do not silence
Eunomia's toasts in a mod pack that also ships `externalFallback(true)` — that combination removes the only
notice the player gets.

## Why the API is split across two classes

`Eunomia` is loaded on dedicated servers, which have no `net.minecraft.client.*` classes at all.
A configuration method there that named a `Screen` or a `Component` — even only in its signature —
would put a client type in `Eunomia`'s constant pool, and the JVM would fail to resolve it the moment
anything touched that member. NeoForge defers client init to `FMLClientSetupEvent` for the same reason.

So the split follows the source-set boundary rather than being a matter of discipline:

- `Eunomia.configure()` writes plain values into side-agnostic holders (`EunomiaSyncDefaults`,
  `EunomiaClientOptions`) and names no client type anywhere. A dedicated server can run the exact same
  initializer code as a client.
- `EunomiaClient.configure()` lives in the client source set and is the only place a `Screen` or a
  `Component` appears.

There is one flag per setting, not two: the client-side `EunomiaToasts.setEnabled` /
`EunomiaSettingsEntryPoint` seams are views onto the same holders the common builder writes.

## Initialisation, unchanged

Consumers still bootstrap Eunomia exactly as before — the loader entry points call `Eunomia.init()`
and, on the client, `EunomiaClient.init()`. `configure()` is additive and optional; a mod that never
calls it behaves identically to one written before this API existed.
