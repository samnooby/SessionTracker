# Good Rune Tracker — Phase 3: Tabbed UI, History & Stats Design

**Date:** 2026-06-15
**Status:** Approved (brainstorming)
**Builds on:** Phase 1 core, Phase 2a adapter logic, Phase 2b RuneLite glue, Phase 2c
drop detection. Implements the Phase 3 read-path decision recorded in
`2026-06-14-phase2-runelite-adapter-design.md`.

## Overview

Phase 3 replaces the minimal Phase 2 readout with the polished **Now / Sessions /
Stats** tabbed panel: a live current-trip view, full session-history browsing down
to the item level, per-category aggregated stats, and session rename/recategorize.
It also lands the deferred **per-trip pricing read path** so historical sessions
are valued with each trip's own captured prices.

The work splits cleanly into a headless, TDD-friendly core/adapter layer (the read
path + a `SessionHistory` read API + edit operations) and a thin Swing rendering
layer (manual-verified in-client, like Phase 2b).

## Goals

- Value history correctly: each trip self-values with its captured unit prices.
- Browse all past sessions: session → trip → item-level breakdown.
- Compare activities: per-category averages (GP/hr, XP/hr, per-trip averages,
  exact supplies used).
- Edit labels: rename a session and recategorize it (re-files it into Stats).
- Keep tracking logic and data access headless-testable; confine the un-testable
  surface to Swing wiring.

## The per-trip read path (core + adapter)

Captured unit prices are stored **per trip**, but the core's
`Session.gpPerHour(ItemValuer)` and `CategoryStats.from(List<Session>, ItemValuer)`
take a **single** valuer. Phase 3 adds per-trip valuation:

1. **`SessionMapper.toSession(StoredSession) → Session`** — new reader. Today only
   `toTrip(StoredTrip)` and `unitPrices(StoredTrip)` exist. `toSession` assembles a
   core `Session` (id, accountHash, category, name, and its `Trip`s via `toTrip`).
   A companion `SessionMapper.valuerFor(StoredSession)` (or equivalent) yields a
   `Function<Trip, ItemValuer>` that maps each trip id to a
   `FrozenItemValuer` built from that trip's stored `unitPrices`.

2. **Core overloads accepting `Function<Trip, ItemValuer>`:**
   - `Session.totalNetProfit(Function<Trip,ItemValuer>)`
   - `Session.gpPerHour(Function<Trip,ItemValuer>)`
   - `CategoryStats.from(String category, List<Session>, Function<Trip,ItemValuer>)`

   Each trip is valued with `valuerFn.apply(trip)`. The existing single-valuer
   methods delegate to the new ones with `t -> valuer` (a constant function), so all
   current behavior and tests are unchanged.

3. A single composite `ItemValuer` is **rejected**: `ItemKey` carries no trip
   identity, so two trips with different captured prices for the same key would
   collide. Per-trip valuation via the function is the correct seam. The persisted
   data is already correct; only this read-side API was deferred.

## Read API: `SessionHistory`

A new pure-Java class (`com.goodrunetracker.adapter.SessionHistory`) wrapping
`SessionStore` + `SessionMapper`. It is the single read/edit seam the Swing tabs
render; it returns immutable value objects and touches **no Swing and no RuneLite
types**, so it is unit-tested headless against a temp `SessionStore` (like the
existing `SessionStoreTest`).

It is constructed with the `SessionStore` and the active `accountHash`. Each query
loads that account's stored sessions, maps them, and computes on demand (no caching;
dozens of sessions is trivially cheap).

**Read queries (each returns immutable view-model records):**

- `sessionsNewestFirst()` → `List<SessionSummary>`: `{sessionId, name, category,
  tripCount, netProfit, gpPerHour, xpTotal, wallClockMillis, startMillis}`, sorted
  by `startMillis` descending. Values use the per-trip valuer.
- `tripsFor(sessionId)` → `List<TripSummary>`: `{tripId, kills, netProfit,
  durationMillis, xpTotal, startMillis, died}`, trip order preserved.
- `tripDetail(sessionId, tripId)` → `TripDetail`: three lists of
  `{label, quantity, gpValue}` for **picked up**, **left on ground** (missed), and
  **supplies used**, plus the trip's net and missed totals. Item labels come from
  the stored `ItemKey` tokens rendered to display names (item id → name via the
  adapter's name lookup; potion family → family name). GP values use that trip's
  frozen valuer.
- `categoryStats()` → `List<CategoryStatsView>`: one per distinct category, each
  wrapping the core `CategoryStats` headline fields `{category, sessionCount,
  tripCount, gpPerHour, xpPerHour}`, sorted by `gpPerHour` descending.
- `categoryDetail(category)` → `CategoryDetail`: the per-trip averages
  (`avgNetProfitPerTrip`, `avgMissedPerTrip`, `avgTripDurationMillis`,
  `avgKillsPerTrip`) plus the per-item supply averages
  (`List<{label, avgQtyPerTrip, avgGpPerTrip}>`) **and** the summed
  `avgTotalSuppliesGpPerTrip` across all supply items.

**Edit operations:**

- `rename(sessionId, newName)` and `recategorize(sessionId, newCategory)` load the
  stored session, mutate the label, and write it back through `SessionStore`.
- `categories()` → distinct categories currently on disk (for the reuse chips).

## Active-session editing

The active (in-progress) session is editable. `TrackingService` holds the live
`Session`, which is also persisted incrementally on each trip end. To keep memory
and disk in sync, the panel routes an edit through `TrackingService`
(`renameActiveSession` / `recategorizeActiveSession`, which mutate the in-memory
`Session` and re-persist) when the edited session is the active one; otherwise the
edit goes straight through `SessionHistory`. The panel knows the active session id
from `TrackingService`.

An in-progress session first appears in the Sessions tab after its **first trip
ends** (when it first hits disk). The live current trip shows only in the Now tab.

## The panel: `GoodRuneTrackerPanel` → tabbed

The `PluginPanel` hosts a `JTabbedPane` with three tabs. The tabs are thin
renderers over `SessionHistory` view models and the live `TripSnapshot`; navigation
state lives in the tab components. Manual-verified in-client.

### Now tab

The live view. Top: status (account/activity + elapsed) and the **Stop / End trip /
Discard** controls; the death keep/discard prompt surfaces here. Then a **Current
trip** card (GP/hr + XP/hr headline; kills, loot picked, on ground, supplies) and a
**Session so far** roll-up (trips, net profit, total XP, session GP/hr). Refreshes
live via the existing `PanelView.refresh()`. Reuses the cached `TripSnapshot` and a
new session-level snapshot from `TrackingService`.

### Sessions tab — hybrid drill-down

- **Accordion** at the session level: a list of session rows (name, trip count, net,
  wall-clock); tapping a row unfolds its trips inline. Each session header carries a
  **pencil** that opens the inline edit form.
- **Drill-in** for item detail: tapping a trip row replaces the view (a
  `CardLayout` swap) with a full-width **Trip detail** page — a `‹ <session> · <trip>`
  back link, the trip summary, and the item breakdown grouped **Picked up / Left on
  ground / Supplies used**, each line `label … gpValue`.
- Read on tab selection and after any edit.

### Stats tab — hybrid drill-down

- A list of **compact category cards** (name, session/trip count, headline GP/hr +
  XP/hr), sorted GP/hr-descending.
- Tapping a card drills in (`CardLayout` swap) to a **Category detail** page: a
  `‹ Stats · <category>` back link, the GP/hr + XP/hr headline, the per-trip averages,
  and **Avg supplies / trip** — each item line `label  avgQty · avgGp` followed by a
  **Total** line (`avgTotalSuppliesGpPerTrip`).

### Edit form (inline, Sessions tab)

Opened by the session-header pencil. A **Name** text field and a **Category** text
field (free text), with tappable **reuse chips** for existing categories, and
**Save / Cancel**. Save routes to the active-session path or `SessionHistory` as
above, persists, and re-reads the affected tabs (the session re-files into Stats if
its category changed). Matches the founding decision: free-text category, defaults
to the first monster's name, editable.

## Data flow

```
SessionStore (JSON per account)
        │  load
        ▼
SessionMapper.toSession + valuerFor  ──►  core Session + Function<Trip,ItemValuer>
        │                                         │
        ▼                                         ▼
SessionHistory  ──► immutable view models  ──►  Swing tabs (Sessions / Stats)
        ▲                                         
        │ rename / recategorize (write-back)      Now tab ◄── TripSnapshot + session
        │                                                     snapshot from TrackingService
   TrackingService (active session, in sync on edit)
```

## Components / file structure

New / modified under `com.goodrunetracker`:

- **Core (modify):** `Session.java`, `CategoryStats.java` — add the
  `Function<Trip,ItemValuer>` overloads; keep single-valuer methods delegating.
- **Adapter (modify):** `SessionMapper.java` — add `toSession` and `valuerFor`.
- **Adapter (new):** `SessionHistory.java` — read/edit API.
- **Adapter (new):** view-model records — `SessionSummary`, `TripSummary`,
  `TripDetail`, `CategoryStatsView`, `CategoryDetail` (plain immutable carriers; may
  be nested types to avoid file sprawl).
- **Adapter (modify):** `TrackingService.java` — expose the active session id, a
  session-level live snapshot for the Now roll-up, and
  `renameActiveSession` / `recategorizeActiveSession`.
- **RuneLite glue (modify):** `GoodRuneTrackerPanel.java` — becomes the tabbed
  panel; split tab rendering into focused components
  (`NowTab` / `SessionsTab` / `StatsTab`) under
  `com.goodrunetracker.adapter.runelite` to keep files focused.
- **RuneLite glue (modify):** `GoodRuneTrackerPlugin.java` — construct
  `SessionHistory`, pass it (plus `TrackingService`) to the panel.

## Testing

**Headless unit tests (no live client):**

- **Read path:** `SessionMapper.toSession` round-trips a stored session to a core
  `Session`; `valuerFor` returns a function that values each trip with its own
  captured prices. A two-trip session whose trips captured *different* prices for the
  same `ItemKey` values correctly per trip (the regression the composite valuer would
  cause). `Session.gpPerHour(fn)` / `CategoryStats.from(..., fn)` match hand-computed
  expectations; the single-valuer delegation still matches its old results.
- **`SessionHistory`:** against a temp `SessionStore` seeded with known sessions —
  `sessionsNewestFirst` order and summary values; `tripsFor` and `tripDetail`
  grouping + GP values; `categoryStats` grouping/sort; `categoryDetail` per-item and
  **total** supply averages; `rename`/`recategorize` write-through (reload and assert,
  including a recategorize that moves a session between Stats buckets).
- **Active-session edit:** `TrackingService.renameActiveSession` /
  `recategorizeActiveSession` mutate the in-memory session and re-persist (load via a
  fresh `SessionHistory` and assert).

**Manual in-client verification (checklist):** finished sessions list newest-first;
expand a session → trips; drill a trip → correct picked/ground/supplies with GP;
Stats cards sorted by GP/hr; drill a category → averages + supplies total; rename
and recategorize a session (incl. the active one) and confirm Stats re-files; Now tab
shows live current-trip + session roll-up and the death prompt.

## Out of scope (deferred, per master spec)

In-game overlay; CSV/export; multi-account comparison views; charged-item (trident /
blowpipe / etc.) tracking; per-trip rename (only sessions carry editable labels);
Stats sort toggle (fixed GP/hr-descending).
