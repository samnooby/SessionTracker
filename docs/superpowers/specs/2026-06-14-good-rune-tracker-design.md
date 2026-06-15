# Good Rune Tracker — Design

**Date:** 2026-06-14
**Status:** Approved (brainstorming)
**Type:** RuneLite plugin for Old School RuneScape

## Overview

A RuneLite plugin that tracks PvM/skilling **Trips** and rolls them up into
**Sessions**, then reports per-category averages so the player can compare how
different activities perform over time.

- A **Trip** is one outing between bank resupplies (e.g. one lap of Demonic
  Gorillas before banking).
- A **Session** is an ordered collection of Trips for a single activity (e.g. an
  evening of Demonic Gorillas made up of 4 trips).
- A **Category** groups Sessions of the same activity so averages can be
  computed across them.

For each trip the plugin tracks monsters killed, loot dropped by kills, loot
actually picked up (and therefore loot left on the ground / "missed value"),
supplies used, and XP gained. Sessions and their trips are saved to disk and
aggregated into per-category averages: GP/hr, XP/hr, average supplies per trip,
average trip length, and more.

## Scope

### In scope (v1)

- Hybrid trip boundary detection: auto-detect banking with manual
  Start / End / Discard override.
- Live trip readout: kills per monster, loot picked up, loot left on ground
  (missed value), supplies used, XP gained.
- Sessions as ordered collections of trips, with a free-text **category** and
  **name**. Category defaults to the first monster killed if left blank. Both are
  editable at any time, including on past sessions.
- Per-category averages: GP/hr, XP/hr, and per-trip averages for length, kills,
  loot picked up, loot left on ground, net profit, and supplies used (in doses /
  ammo counts).
- Per-account JSON storage with full, browsable history.
- Tabbed side panel: **Now / Sessions / Stats**.
- Supplies tracking via an event-ordered ledger: any net decrease in combined
  inventory+equipment counts as used/lost, dose-normalized for potions (ammo
  fired-but-not-recovered nets out automatically).

### Deferred (v2+)

- Charged-item consumption tracking (trident, blowpipe, etc.) — logged as a
  known gap in v1.
- In-game overlay showing live rates.
- CSV / JSON export.
- Multi-account aggregate views.
- **Supply recommendations (v2/v3):** detect loadout imbalances from historical
  leftover patterns (e.g. food consistently left over while prayer potions run
  out) and suggest rebalancing supplies for that category of trip.

### Explicitly not built

- Separate tracking/measurement of banking or travel "downtime" as its own
  metric. Session GP/hr simply uses full session wall-clock time, which
  inherently includes banking.

## Domain model

All entities are keyed by stable UUIDs, never by name, so renaming or
recategorizing never breaks references.

### Trip

- `id`: UUID
- `startTime`, `endTime`: timestamps
- `activeDuration`: trip wall-clock from start to end (used for per-trip rates)
- `kills`: `Map<NpcName, count>`
- `dropped`: `Map<ItemId, quantity>` — everything kills produced
- `pickedUp`: `Map<ItemId, quantity>` — what entered the inventory
- `suppliesUsed`: `Map<ItemId, doseOrQty>` — net carried-quantity decreases,
  dose-normalized for potions
- `xpGained`: `Map<Skill, xp>`
- `diedDuringTrip`: boolean — set if the player died; surfaces the keep/discard
  prompt and marks the trip
- Derived: `missed = dropped − pickedUp`; `netProfit = value(pickedUp) −
  value(suppliesUsed)`.

### Session

- `id`: UUID
- `accountHash`: owning RuneScape account
- `category`: free-text string (defaults to first monster killed)
- `name`: free-text string (optional)
- `tripIds`: ordered list of Trip UUIDs
- `startTime`, `endTime`: full session wall-clock span (includes time between
  trips)
- Totals are derived from member trips. Session GP/hr uses
  `value(netProfitAcrossTrips) / sessionWallClock`.

### CategoryStats (computed, not stored)

Aggregation over all Sessions sharing a category:

- GP/hr, XP/hr (averaged across sessions, weighted by time)
- Per-trip averages: length, kills, loot picked up, loot left on ground, net
  profit, supplies used per item
- Sample size: session count and trip count

## Tracking engine

### Trip lifecycle

- **Trip starts:** entering combat / first tracked kill after a session is
  active, or manual Start.
- **Trip ends:** the bank interface opening is the sole automatic trigger in v1.
  Manual **End** or **Discard** always override automatic detection. Discard
  drops the in-progress trip without saving. (Region-based and teleport-based
  detection are explicitly deferred — too error-prone for v1.)
- **Session ends:** manual stop, or after a configurable idle timeout with no
  tracked activity.
- **Death:** the local player's death is detected and the in-progress trip is
  flagged, prompting the user to **Discard** it or **Keep** it (marked "died").
  This prevents a death's mass inventory loss from registering as supplies used.

### The ledger (core tracking model)

Tracking is an event-ordered ledger held in the domain core, updated live as
events arrive (which also drives the real-time panel). It is **not** a single
start-vs-end inventory diff — that misclassifies the common case where an item
is both kill loot and a consumed supply, and where potions change item id per
dose.

- **Carried-quantity state:** the ledger holds the combined carried quantity per
  item id across **inventory + equipment together**. Combining the containers
  means equipping gear, or firing-then-recovering ammo, nets correctly instead
  of looking like a loss.
- **On `NpcLootReceived`:** add items to `dropped`, increment the per-NPC kill
  count, and add the items to a "still on the ground" pool.
- **On `ItemContainerChanged` (inventory or equipment):** diff against the
  previous snapshot and apply each change to the ledger:
  - **Net decrease of an id → supplies used.** Any decrease counts (eating,
    drinking, firing ammo, dropping a brought item and leaving it). Ammo fired
    from the equipment ammo slot and not recovered nets out here automatically.
  - **Net increase of an id → gained.** If the id is in the still-on-the-ground
    pool, it is recorded as **picked up** and drained from that pool; otherwise
    it is a generic gain.
- **Dose normalization:** a layer groups potion item ids into families and
  reports consumption in **doses** (a (4)→(3) transition is 1 dose used), so the
  panel and stats show "1 dose of Prayer potion," not "(4) −1, (3) +1."
- **Missed value:** at trip end, `missed = dropped − pickedUp`, valued at GE
  price via `ItemManager`.
- **XP:** `StatChanged` deltas per skill.

### Known v1 limitations (documented, not handled)

- **Storage containers** (looting bag, rune pouch, herb sack, gem/coal bag, seed
  box): moving items into them reads as an inventory decrease, so it is counted
  as "used." For the looting bag this means stored loot is mis-attributed to
  supplies. Accepted for v1; the live ledger makes it visible. Revisit in v2.
- **Charged items** (trident, blowpipe, etc.): internal charge consumption is
  invisible to inventory diffs and is not tracked in v1.
- **Rune pouch:** runes spent from the pouch do not appear as inventory
  decreases and are not counted; loose-inventory runes are.
- **Non-potion item transformations** (fletching, alching, etc.): the consumed
  item reads as "used" and the product as "gained." Rare on combat trips;
  accepted for v1.
- **Ground-recovered ammo:** ammo fired and later picked back up off the ground
  is counted as used at fire time and not credited back on recovery (overcounts
  for setups without an Ava's device). Ava's-saved ammo, which never leaves the
  slot, is counted correctly. The ledger classifies per settled game tick, so
  this only affects fire-then-recover-across-ticks.
- **Looted-then-dropped item:** an item picked up as loot and then dropped again
  mid-trip is counted as both picked-up loot and a supply used (net profit nets
  to zero, but supplies-used is inflated). The ledger cannot distinguish a drop
  from a consume. A precise fix needs RuneLite ground-item events and is planned
  for Phase 2b; see `2026-06-14-phase2-runelite-adapter-design.md`.

### Rates

- Per-**trip** metrics (GP/hr, XP/hr for a trip) use that trip's
  `activeDuration`.
- **Session** GP/hr uses the full session wall-clock span, so banking and travel
  between trips count as real elapsed time.

## Persistence

- Storage root: `~/.runelite/goodrunetracker/<accountHash>/`.
- One JSON file per session (small writes, scalable history). Serialized with
  Gson.
- All sessions for the active account are loaded into memory on startup.
- Writes occur on trip end, session end, and on any edit.
- Edits (rename, recategorize, delete) rewrite only the affected session file.
- **Valuation (refined in Phase 2):** each finished trip stores the GE unit
  prices in effect at trip end alongside its quantities, so historical sessions
  are valued as earned and never drift with the market. See
  `2026-06-14-phase2-runelite-adapter-design.md`.

## UI — tabbed side panel

RuneLite side panel width (~225px); content stacks vertically. Three tabs:

- **Now:** live trip card (timer, End / Discard buttons), current trip stats
  (kills, loot picked, on-ground, supplies, XP, GP/hr), and the running session
  total.
- **Sessions:** history list, newest first. Selecting a session shows its trips
  and allows editing name / category and deleting the session.
- **Stats:** category list (each showing GP/hr and XP/hr at a glance) →
  drill-down to a category's averages (per-hour rates, per-trip averages, and
  average supplies per trip in doses / ammo).

### Config options

- Idle timeout for auto-ending a session.
- Banking auto-detection on/off.
- Minimum value threshold for showing on-ground items (reduce clutter).

## Architecture & testing

The central design decision is a hard separation between domain logic and the
RuneLite integration:

- **Pure domain core** — trip/session model, inventory diffing, dose math,
  ammo-slot math, missed-loot calculation, and category aggregation. Zero
  RuneLite dependencies. Plain Java, fully unit-testable via TDD.
- **RuneLite adapter layer** — `@Subscribe` event handlers, the Swing
  `PluginPanel` UI, and JSON persistence. Thin; translates game events into
  calls on the core and renders core state.

This keeps every piece of tricky logic (doses, ammo, missed value, averages) in
testable plain Java, with the RuneLite-coupled code kept minimal.

- Built as a standard Gradle RuneLite **external plugin**, structured to be
  Plugin Hub-ready.
- Domain core developed test-first. UI and event plumbing verified manually in
  the client plus light integration tests where practical.

## Open questions / assumptions

- **Banking detection (resolved):** v1 uses the bank interface opening as the
  only automatic trip-end trigger, backed by manual End/Discard. Which specific
  bank widgets count (standard bank, deposit box, bank chest) to be confirmed
  against the live client.
- **Other players' loot (resolved):** v1 ignores the possibility of picking up
  items that were not produced by your own kills. Any inventory increase during
  a trip is attributed to your kill loot.
- **Loot-and-supply overlap (resolved):** handled by the event-ordered ledger
  with dose normalization (see "The ledger" above), not an end-of-trip diff.
  Isolated in the domain core and covered by targeted unit tests (the potion
  case, ammo fire/recover, kill with partial pickup, equip-from-inventory).
- **Supplies definition (resolved):** any net decrease in combined
  inventory+equipment carried quantity counts as used/lost. No maintained
  "supply list"; this intentionally captures dropped-and-abandoned items too.
- **Death handling (resolved):** local-player death flags the trip for a
  keep/discard prompt.
