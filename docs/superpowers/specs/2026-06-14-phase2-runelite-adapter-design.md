# Good Rune Tracker — Phase 2: RuneLite Adapter Design

**Date:** 2026-06-14
**Status:** Approved (brainstorming)
**Builds on:** Phase 1 domain core (`com.goodrunetracker.core.*`) and the overall
design at `2026-06-14-good-rune-tracker-design.md`.

## Overview

Phase 2 turns the tested, RuneLite-free domain core into a **working, installable
RuneLite plugin**. It wires live game events into the `TripLedger`, values items
via the Grand Exchange, persists sessions per-account as JSON, and ships a
**minimal functional panel** so the tracker can be driven and verified in the
real client. The polished tabbed UI (Now / Sessions / Stats) remains Phase 3.

## Scope

### In scope (Phase 2)

- A RuneLite plugin (`@PluginDescriptor`) that subscribes to the relevant game
  events and drives the Phase 1 core.
- Session/trip lifecycle: manual session start/stop, automatic bank-triggered
  trip boundaries, manual End/Discard, empty-trip suppression, death handling.
- Item valuation: live GE pricing (incl. per-dose potion pricing) and
  **capture-at-trip-end** unit prices so historical sessions stay stable.
- Per-account JSON persistence (load on startup, write on changes).
- A minimal `PluginPanel`: Start/Stop tracking, End/Discard trip, a live current-
  trip readout, and the death keep/discard prompt.
- Headless unit tests for everything not bound to the live client, behind
  interfaces.

### Out of scope (later)

- The polished tabbed Now/Sessions/Stats panel, session history browsing, and
  edit/recategorize UI — **Phase 3**.
- Charged-item tracking, in-game overlay, export, multi-account views — per the
  overall spec's deferred list.

### Decisions carried in from brainstorming

- **Minimal functional panel** in Phase 2 (not "no UI"), so manual End/Discard
  and the death prompt are exercisable in-client.
- **Historical valuation = captured at trip time** (overrides the original
  spec's "current prices" default). Each finished trip stores the unit prices in
  effect at trip end; history never drifts with the market.
- **Always exactly one active trip while a session is running** — simpler than
  detecting "entering combat", and captures supplies used before the first kill.

## Architecture

```
RuneLite events ─┐
 NpcLootReceived  │
 ItemContainerChanged (→ mark inventory dirty)
 GameTick ────────┼─→  TrackingService  ──→  TripLedger (core)
 StatChanged      │      (session+trip      ──→ Trip / Session (core)
 WidgetLoaded(bank)│      state machine)          │
 ActorDeath       ─┘            │                 ▼
                                │           SessionStore (Gson JSON,
   LiveItemValuer ◄─────────────┤            per-accountHash dir)
   (ItemManager prices          │                  ▲
    + PotionRegistry)           └──→ MinimalPanel ──┘
```

All RuneLite-specific code lives in a new `com.goodrunetracker.adapter` package.
The Phase 1 core is not modified. The seam between them is narrow: the adapter
feeds the core normalized `ItemKey` data and implements the core's `ItemValuer`.

## Components

### `GoodRuneTrackerPlugin`

The RuneLite entry point. Holds `@Inject` references to `Client`, `ItemManager`,
`ClientToolbar`, and `GoodRuneTrackerConfig`. Registers the panel, constructs the
`TrackingService` and `SessionStore`, and translates `@Subscribe` events into
plain method calls on `TrackingService`. Contains no tracking logic itself — it
is a thin wiring layer (this is why the logic-bearing `TrackingService` can be
tested without a live client).

Subscribed events:

- `GameTick` → `service.onTick()`
- `ItemContainerChanged` (inventory or equipment) → `service.markCarriedDirty()`
- `NpcLootReceived` → `service.onKill(npcName, items)`
- `StatChanged` → `service.onXp(skillName, totalXp)`
- `WidgetLoaded` with the bank group id → `service.onBankOpened()`
- `ActorDeath` where the actor is the local player → `service.onLocalPlayerDeath()`

### `TrackingService`

The lifecycle state machine and the only place tracking logic lives. Holds the
active `Session` (or none), the active `TripLedger` (or none), the per-skill
last-seen XP map, and an `inventoryDirty` flag. Collaborators are injected as
interfaces so the service is testable headless:

- `Clock` — supplies `nowMillis()` and trip/session/uuid ids (a fake in tests).
- `CarriedSnapshotSupplier` — returns the current combined inventory+equipment as
  a raw `Map<Integer,Integer>` (backed by `Client.getItemContainer(...)`; a fake
  in tests).
- Item-name lookup — a bare `IntFunction<String>` over `ItemManager` for
  normalization (implemented as `IntFunction<String>` directly, not a named
  interface).
- `LiveItemValuer` — the core `ItemValuer` for live readouts and price capture.
- `SessionStore` — persistence.
- `PanelView` — a narrow interface the panel implements (`renderCurrentTrip(...)`,
  `showDeathPrompt()`), so the service never touches Swing directly.

Behaviors:

- **startSession():** create a `Session` (empty), then `startTrip()`.
- **startTrip():** create a fresh `TripLedger`; immediately feed it a baseline
  `updateCarried(normalize(currentCarried))`; reset `inventoryDirty`.
- **onTick():** if a trip is active and `inventoryDirty`, read + normalize current
  carried, call `ledger.updateCarried(...)`, clear the flag, and refresh the
  panel readout.
- **onKill(npc, items):** if a trip is active, normalize `items` (updating the
  `PotionRegistry`) and call `ledger.recordKill(npc, drops)`.
- **onXp(skill, totalXp):** maintain the last-seen total per skill; the first
  observation of a skill only primes the baseline. Subsequent positive deltas are
  passed to `ledger.recordXp(skill, delta)` when a trip is active.
- **onBankOpened():** if a trip is active, `endTrip()` then `startTrip()`.
- **endTrip():** flush a final `updateCarried`, `build` the `Trip`. If it has no
  kills, no supplies, and no XP, discard it. Otherwise capture unit prices (see
  Valuation), append it to the session, and persist.
- **discardTrip():** drop the active ledger without building/persisting; start a
  fresh trip if the session is still active.
- **endSession():** `endTrip()` for the final trip, persist the session, clear
  active state.
- **onLocalPlayerDeath():** if a trip is active, set its `died` flag, **stop
  feeding ticks** (so the post-death inventory drop never reaches the ledger),
  and `showDeathPrompt()`. The user's choice resolves to keep (end the trip,
  marked died) or discard.

### Valuation: `LiveItemValuer`, `PotionRegistry`, `FrozenItemValuer`

- **`PotionRegistry`** — learns, as raw snapshots are normalized, a representative
  `(itemId, dose)` for each potion family (the highest-dose form seen). Adapter-
  side, since the core's `ItemKey.potion(family)` deliberately drops the id.
- **`LiveItemValuer`** implements the core `ItemValuer`:
  - `ItemKey.item(id)` → `ItemManager.getItemPrice(id) * qty`.
  - `ItemKey.potion(family)` → `qty * (price(repId) / repDose)` using the
    registry (per-dose price; integer division, floored).
  - Exposes `unitValue(key) = value(key, 1)` for price capture.
- **Capture at trip end:** `endTrip()` builds `Map<ItemKey, Long> unitPrices` over
  every key in the trip's quantity maps via `LiveItemValuer.unitValue(key)`, and
  stores it on the persisted trip.
- **`FrozenItemValuer`** implements the core `ItemValuer` from a stored
  `Map<ItemKey, Long>` unit-price map: `value(key, qty) = unitPrices.get(key) *
  qty` (0 if absent). At read time it feeds the unchanged
  `Trip`/`Session`/`CategoryStats` value methods, so history is valued exactly as
  captured.

### Persistence: `SessionStore` and `ItemKeyCodec`

- Storage root: `RUNELITE_DIR/goodrunetracker/<accountHash>/`; one file
  `<sessionId>.json` per session. `accountHash` from `Client.getAccountHash()`.
- **`ItemKeyCodec`** maps `ItemKey` ↔ a string token (`"item:560"`,
  `"potion:Prayer potion"` — its existing `toString`, plus a matching `parse`).
  With string keys, the quantity maps and the unit-price map serialize naturally
  through Gson.
- A small DTO mirrors `Session`/`Trip` with string-keyed maps and the captured
  unit prices. `SessionStore` converts core ↔ DTO on write/read; `Trip` and
  `Session` already expose public constructors that make reconstruction direct.
- On startup, load all session files for the active account into memory. Write a
  session's file on trip end, session end, and (Phase 3) edits. Writes are small;
  performed synchronously for v1.

### Minimal panel: `GoodRuneTrackerPanel`

A single `PluginPanel` implementing `PanelView`: a Start/Stop-tracking toggle,
End trip and Discard trip buttons, a live readout of the current trip (kills,
loot picked, on-ground, supplies, XP, GP/hr), and the death keep/discard prompt
(two buttons). Swing updates marshalled onto the EDT. Intentionally plain; Phase
3 replaces it with the tabbed UI.

### Config: `GoodRuneTrackerConfig`

A `@ConfigGroup` interface exposing: bank-detection on/off, and a minimum
on-ground item value threshold (clutter filter for the readout). (Idle-timeout
auto-end is deferred; sessions end manually in Phase 2.)

## Testing

**Headless unit tests (no live client), via injected interfaces:**

- `TrackingService` lifecycle: start session → kill → tick (pickup) → bank
  (trip rolls over) → end session → assert persisted sessions/trips; the
  loot-and-supply overlap end-to-end; death → keep and death → discard; empty-
  trip suppression.
- `SessionStore` round-trip: build a session, write, read back, assert equality
  of quantities, xp, times, `died`, and captured unit prices.
- `ItemKeyCodec`: `item`/`potion` keys round-trip through token form, including a
  family name containing a colon.
- `PotionRegistry` + `LiveItemValuer` (with a fake price source) and
  `FrozenItemValuer`: per-dose math and capture/replay.

**Manual in-client verification (checklist):** install the plugin, start tracking,
kill an NPC, leave an item on the ground, drink a potion, bank, confirm the panel
readout and that a `<sessionId>.json` with the expected shape is written under the
account dir; trigger a death and confirm the prompt.

## File structure

New package `com.goodrunetracker.adapter`:

- `GoodRuneTrackerPlugin.java` — `@Subscribe` wiring only.
- `GoodRuneTrackerConfig.java` — config interface.
- `TrackingService.java` — lifecycle state machine (logic).
- `Clock.java`, `CarriedSnapshotSupplier.java`, `PanelView.java` — the injected
  collaborator interfaces (item-name lookup is a bare `IntFunction<String>`, not
  its own file).
- `PotionRegistry.java`, `LiveItemValuer.java`, `FrozenItemValuer.java` —
  valuation.
- `SessionStore.java`, `ItemKeyCodec.java`, plus DTO types — persistence.
- `GoodRuneTrackerPanel.java` — the minimal Swing panel.

Phase 2b adds the plugin descriptor resources and the
`runelite-plugin.properties` needed to load the plugin. (Lombok is intentionally
not used anywhere — plain Java throughout, for JDK 24/25 compatibility.)

## Open questions / assumptions

- **Bank widget ids:** the exact group id(s) for the standard bank (and whether to
  also treat the deposit box as a trip-end trigger) are confirmed against the
  live client during implementation; the standard bank is the baseline.
- **XP priming on login:** the first `StatChanged` per skill sets the baseline and
  is never counted; this is the standard RuneLite pattern and assumed sufficient.
- **Account hash when logged out:** `getAccountHash()` returns -1 when not logged
  in; the service only persists under a real account, so tracking requires being
  logged in (assumed acceptable).

## Phase 2b decisions (recorded during 2b brainstorming)

Phase 2 is implemented in two sub-phases: **2a** (adapter logic, merged) and
**2b** (the RuneLite glue + minimal panel that makes it run in-client). Two
decisions were settled before planning 2b:

- **Login lifecycle — require login to start.** Tracking needs a real account
  hash (`Client.getAccountHash()` is -1 when logged out). The panel's Start
  control is disabled until logged in; the `TrackingService` is (re)built with
  the current account hash on login. Logging out mid-session auto-stops and saves
  the session. Pre-login activity is never tracked.
- **Drop-detection deferred to Phase 2c.** The event-based fix for the
  looted-then-dropped limitation (distinguishing a drop from a consume via a
  ground-item spawn on the player's tile) is split into its own focused phase
  after 2b is verified running in-client — it adds a new event path and
  attribution heuristics that need live tuning, and bundling it into the
  un-unit-testable 2b glue would muddy verification. 2b keeps the documented
  looted-drop limitation; 2c removes it.

Phase 2b is intentionally a thin wiring layer over the already-tested 2a logic:
the only genuinely new testable code is the inventory+equipment → raw-map
combiner and the login/account wiring; the `@Subscribe` handlers and the Swing
panel are verified by an in-client checklist.

## Phase 3 read-path decision (recorded now, implemented in Phase 3)

Captured unit prices are stored **per trip**, but the Phase 1 core's
`Session.gpPerHour(ItemValuer)` and `CategoryStats.from(List<Session>,
ItemValuer)` take a **single** valuer for the whole aggregate. To value history
with each trip's own captured prices, Phase 3 will:

1. Add a `SessionMapper.toSession(StoredSession) -> core Session` reader (it does
   not exist yet; only trip-level `toTrip` does).
2. Add core overloads that accept a **`Function<Trip, ItemValuer>`** so each trip
   self-values with `new FrozenItemValuer(thatTrip.unitPrices)` — e.g.
   `Session.gpPerHour(Function<Trip,ItemValuer>)` and
   `CategoryStats.from(..., Function<Trip,ItemValuer>)`, with the existing
   single-valuer methods delegating (`v -> t -> v`) for backward compatibility.

A single composite `ItemValuer` is explicitly rejected: `ItemKey` carries no trip
identity, so trips with different captured prices for the same key would collide.
The persisted data (per-trip `unitPrices`) is already correct; only this
read-side API is deferred.

## Known limitation carried from review (looted-then-dropped)

The ledger cannot distinguish dropping an item to the ground from consuming it —
both are inventory decreases. So an item you **loot and then drop again** mid-trip
is counted as both picked-up loot and a supply used (net profit nets to zero, but
the supplies-used figure is inflated). The "un-pick-up" alternative was rejected
because it breaks the more common case of a consumable that also drops as loot
(e.g. bringing food that the monster also drops, then eating it). The correct fix
is event-based — detect a drop via RuneLite's ground-item spawn on the player's
tile — and is planned for **Phase 2b**, where those events are available.
