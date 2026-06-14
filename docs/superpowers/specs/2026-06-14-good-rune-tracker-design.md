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
- Supplies tracking that is dose-aware (potions) and ammo-aware (equipment ammo
  slot).

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
- `suppliesUsed`: `Map<ItemId, doseOrQty>` — consumables spent, dose-/ammo-aware
- `xpGained`: `Map<Skill, xp>`
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
- **Trip ends:** banking detected (bank interface opened, or known bank region
  plus an inventory deposit) or teleport out of the area. Manual **End** or
  **Discard** always override automatic detection. Discard drops the in-progress
  trip without saving.
- **Session ends:** manual stop, or after a configurable idle timeout with no
  tracked activity.

### Metric sources (RuneLite API)

- **Loot dropped + kills:** `NpcLootReceived` — provides the NPC and the items
  the kill dropped; increments the per-NPC kill count and accumulates `dropped`.
- **Loot picked up:** inventory snapshot diffs via `ItemContainerChanged`
  (inventory container). Net increases matched against `dropped` are recorded as
  `pickedUp`.
- **Missed value:** `dropped − pickedUp`, valued at GE price via `ItemManager`.
- **Supplies used:** inventory decreases that are not attributable to banking.
  Dose-aware: a 4-dose → 3-dose potion transition counts as 1 dose used, not 1
  item. Ammo-aware: arrows/bolts/darts are counted from the **equipment ammo
  slot** delta, not the inventory. Charged-item consumption is out of scope for
  v1.
- **XP:** `StatChanged` deltas per skill.

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

- Exact banking-detection heuristics (which widgets / regions) to be refined
  during implementation against the live client.
- Matching picked-up items to dropped items when the same item id also arrives
  from a non-kill source mid-trip is best-effort; ambiguous gains default to
  "picked up loot".
