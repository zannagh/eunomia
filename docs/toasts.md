# Eunomia toasts

A game-version-agnostic API for the small notification cards that slide in at the top right of the
screen. Your mod supplies `Component`s; Eunomia handles the vanilla differences across 1.20.1 → 26.x
(`ToastComponent` → `ToastManager` in 1.21.2, the `SystemToastIds` enum → the open `SystemToastId`
class in 1.21, and the manager moving off `Minecraft` onto `Gui` in 26.2), on Fabric and NeoForge.

Eunomia ships no lang entries for *your* toasts: pass components your own mod resolved, so the text stays in
your namespace and your resource pack. Eunomia's own `assets/eunomia/lang/en_us.json` covers only Eunomia's
own strings — the settings screen and the three diagnostic toasts described below — so a key of yours will
never resolve out of it.

## Show a toast

```java
EunomiaToasts.show(
        Component.translatable("mymod.toast.saved.title"),
        Component.translatable("mymod.toast.saved.description"));
```

Safe from **any** thread. Netty read threads and background workers are the usual callers, so the call
marshals onto the client thread itself — you never need `Minecraft.getInstance().execute(...)` around
it. It is also safe with no client present (dedicated server, gametests, before the client has a render
context): the call degrades to a no-op instead of throwing.

The second line is optional — pass `null` for a title-only toast.

## Replace a toast instead of stacking one

Give the toast an id and ask for replacement. A toast already on screen under that id is rewritten with
the new text and its timer restarted, rather than a second card stacking below it — the right shape for
status that supersedes itself:

```java
private static final ToastId LINK_STATUS = ToastId.of("mymod:link_status");

EunomiaToasts.toast(Component.translatable("mymod.toast.link.title"))
        .description(Component.translatable("mymod.toast.link.connecting"))
        .id(LINK_STATUS)
        .replaceExisting(true)
        .show();
```

`ToastId.of(...)` interns by key, so it is equally fine in a `static final` field or looked up ad hoc.
Use a namespaced key so ids from different mods cannot collide.

> On **1.20.1 only**, vanilla's toast token is a closed enum that mods cannot extend, so every Eunomia
> toast necessarily shares one token there. Stacking (the default) is unaffected; only
> `replaceExisting(true)` collapses across different ids on that one version.

## Turn all Eunomia toasts off

One global kill switch covers every toast raised through this API, including Eunomia's own. Wire it to
your own setting if you want users to control it:

```java
EunomiaToasts.setEnabled(false);
boolean showing = EunomiaToasts.isEnabled();   // default: true
```

Disabling drops toasts at the point of the call; nothing is queued to appear later when re-enabled.

## Eunomia's own three diagnostic toasts

Eunomia raises exactly three toasts of its own, all purely informational: nothing about them influences the
transport decision or the send gate, and a failure to raise one is swallowed. Unlike everything else in this
API they *do* ship translation keys, in Eunomia's own `assets/eunomia/lang/` — they are the framework talking
to the player, not your mod.

| Toast | Key prefix | Raised when |
| --- | --- | --- |
| Server sync unavailable | `eunomia.toast.sync.unavailable` | the capability probe resolved and the joined server did **not** answer it |
| Cloud sync unreachable | `eunomia.toast.sync.relay_unreachable` | the configured relay failed its health probe |
| New cloud sync relay | `eunomia.toast.sync.new_relay_host` | the joined server has just put this client's data on a relay host it has never used before |

The second is given the effective relay address as a format argument, so the player can tell a typo from an
outage; the third is given the relay *host*, because "your data is going somewhere" is only useful if it says
where.

All three go through `EunomiaToasts` and therefore honour the global kill switch by construction. They stack
rather than replace: they carry distinct `ToastId`s, but on 1.20.1 the vanilla toast token is a closed enum, so
a replace-in-place would collapse across unrelated ids there and nowhere else.

### When they are suppressed

The rules for the first two are pure predicates in `de.zannagh.eunomia.diagnostics.SyncDiagnostics`, evaluated
over an immutable `ClientSyncState` snapshot. Both are suppressed unless **all** of their conditions hold.

**Server sync unavailable** (`shouldWarnMissingServerSync`) needs:

- the capability probe has *resolved* — an in-flight probe is not yet bad news;
- the server did **not** answer the handshake — if it did, sync works and there is nothing to report;
- this is a real multiplayer connection — never in singleplayer, never on a LAN world;
- the relay is **not** already usable (fallback enabled *and* an address configured). Telling a player to
  "enable Cloud Sync" when they already did would be insulting; if that relay then turns out to be down, the
  next toast covers it with accurate copy.

**Cloud sync unreachable** (`shouldWarnRelayUnreachable`) needs:

- a relay is actually configured — there is nothing to be unreachable otherwise;
- this is a real multiplayer connection — the relay is scoped per server address;
- the joined server does **not** speak Eunomia. When it does (the `preferExternalTransport` case), losing the
  relay is a demotion rather than an outage — the client lands back on a working in-game transport, so saying
  "nothing will be synchronised" would simply be false.

**New cloud sync relay** is decided elsewhere, by
`EunomiaSyncSettings.recordRelayHostAndReportIfNewlyServerChosen()` in `java/common/src/main`, because the
"have I said this already" bookkeeping has to be persisted in the client config and has to be a single atomic
test-and-set. It needs:

- the capability probe has resolved and this is a real multiplayer connection — the two facts the client-side
  call site checks before asking at all;
- the effective settings describe a usable relay, so data really is about to leave the machine;
- the joined server is the *reason* — it advertised `enableExternalFallback = true`, or advertised a usable
  address, or both. A host the player chose themselves, or that their mod pack shipped, is recorded silently:
  they chose it, and telling them about their own choice is nagging;
- the host has not been recorded before. Every usable host is recorded on sight, including the ones that go
  unannounced, so a host a player was already using is not announced later just because a server started
  advertising it too.

Hosts, not URLs, are recorded: an operator who appends a path, changes the port or moves from `http` to
`https` has not moved anyone's data to a new party.

### Why no two of them can fire together

The first two are disjoint from each other because one requires the relay to be **unusable** and the other
requires it to be **usable**.

The third is disjoint from both by a different fact: it can only fire when the joined server *did* answer the
handshake, because a server has to have advertised a policy for it to have steered anybody anywhere, and an
unanswered handshake yields `ServerSyncPolicy.UNKNOWN`. Both of the others require the opposite — that the
server did **not** answer. So no connection can produce two of these cards, but do not read that as one rule:
it is two independent ones, and changing either predicate can break it.

### Silencing them

They go through the same entry point as every other toast, so the global kill switch covers them:

```java
EunomiaToasts.setEnabled(false);
```

That now delegates to `EunomiaClientOptions`, which is also what the fluent configuration writes — use
whichever side you are already on:

```java
// From common code, in your mod initializer:
Eunomia.configure().toasts(false).apply();

// From client code, if you are already holding the client builder:
EunomiaClient.configure().toasts(false).apply();
```

### Why the third one exists

It is the mitigation for the client-config `1.0.0 -> 1.1.0` migration, which maps a recorded
`enableExternalFallback: false` to "inherit" rather than to a permanent "no" — see
[`configuration.md`](configuration.md). A player who had that `false` and meant it can find Cloud Sync
switched on again by a server's or a mod pack's default, and this toast is what makes that visible at the
moment it happens, naming the host and pointing at the settings screen. It notifies rather than blocks:
the transport decision is already taken by the time it runs, and a library that halted a join on a policy
question would be answering a question that is the player's to answer.

## What is deliberately not exposed

Only knobs that behave identically on every supported version are part of the API. Vanilla's per-version
extras — custom icons, explicit display durations, the `multiline` helper that was dropped in 26.2 — are
absent on purpose: a knob that silently does nothing on half the version matrix is worse than no knob.

The implementation builds on vanilla's `SystemToast` rather than a custom `Toast`. The `Toast` interface
changed shape three times across the matrix (`render` returning `Visibility` → `getWantedVisibility` +
`update` + `render` → `extractRenderState`), while the static `SystemToast.add` / `addOrUpdate` entry
points kept an identical four-argument shape from 1.20.1 through 26.3.
