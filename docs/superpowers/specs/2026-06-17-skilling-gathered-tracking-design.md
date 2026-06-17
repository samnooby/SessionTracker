# Skilling (Resources Gathered) Tracking — Design

Date: 2026-06-17
Status: Approved (brainstorming), pending implementation plan

## Goal

Track resources gathered while skilling (woodcutting, fishing, mining, etc.) as a
first-class, **separate** section of a trip/session — count and GP value — surfaced
alongside the per-skill XP that the plugin already records.

## Background

The plugin already captures per-skill XP via the `StatChanged` event
(`GoodRuneTrackerPlugin`), accumulated in `TripLedger.recordXp` and exposed as
`Trip.xpGained()` and rendered across the Now/Sessions/Stats tabs. XP tracking is
therefore considered done.

Inventory changes are reconciled per game tick in `TripLedger.updateCarried`. A
positive per-tick delta is matched first against dropped-brought items, then against
kill loot still on the ground (`groundPool`). At `TripLedger.java:90` any remaining
gain — a gain that is **not** kill loot — is currently discarded as an "untracked
generic gain." For a skiller, that remainder is precisely the logs/fish/ore harvested.

This design captures that remainder into a new `gathered` bucket and threads it through
the existing pipeline the same way `pickedUp` already flows.

## Scope

### In scope (v1)
- New `gathered` bucket: per-`ItemKey` quantities of non-loot inventory gains.
- GP valuation of gathered items, reusing the existing `ItemValuer` /
  captured-`unitPrices` mechanism.
- A dedicated **Gathered** section in the UI (live trip + session/category history)
  with its own totals and its own averages (per trip and per hour), fully independent
  of combat net profit.

### Out of scope (deferred / follow-up tasks)
- **Action counts & rates** (logs cut/hr, fish caught/hr). Deferred entirely; the
  signal choice (XP events vs item gains vs animations) is unresolved.
- **"Banking ends trip" config flag.** A RuneLite config setting to optionally end the
  current trip when the bank is opened. Tracked as its own follow-up task (see Known
  Limitations).
- **False-positive filtering** of non-loot, non-gather gains (shop buys, bank
  withdrawals, quest rewards). Acceptable noise for v1.
- Folding gathered value into `netProfit` — explicitly **not** done; combat profit
  numbers stay unchanged.

## Design

The `gathered` map mirrors `pickedUp` one-for-one through every layer. No new RuneLite
events, no architectural change.

### 1. `core/TripLedger.java`
- Add `private final Map<ItemKey, Integer> gathered = new HashMap<>();`
- In `reconcilePickup`, replace the dropped remainder at line 90 with
  `if (remaining > 0) gathered.merge(key, remaining, Integer::sum);`
- Pass `gathered` into the `Trip` constructor in `build()`.
- Note: the negative-delta path (line 66, `suppliesUsed`) is **unchanged** in v1, so a
  mid-trip bank deposit still registers as supplies until the banking flag exists.

### 2. `core/Trip.java`
- Add `gathered` field, defensive-copied like the others.
- Add `gathered()` accessor (unmodifiable) and `gatheredValue(ItemValuer)` mirroring
  `pickedUp()` / `pickedUpValue`.
- `netProfit` and all existing methods unchanged.

### 3. `adapter/StoredTrip.java` + `adapter/SessionMapper.java`
- Add `public Map<String, Integer> gathered;` to `StoredTrip`.
- Map `gathered` in both directions in `SessionMapper` (Trip→Stored and Stored→Trip),
  the same as `pickedUp`.
- Ensure the `unitPrices` capture (`FrozenItemValuer`) includes every `ItemKey` present
  in `gathered`, so gathered items get historical pricing. (Verify the price-capture
  pass iterates gathered alongside pickedUp/supplies.)
- Backward compatibility: `gathered` absent in older session JSON deserializes to null;
  mapper must treat null as empty.

### 4. `adapter/TripSnapshot.java` + `adapter/TrackingService.java`
- Add `gatheredGp` (and a gathered item breakdown list if the UI needs per-item rows)
  to `TripSnapshot`.
- Populate it in the snapshot builder in `TrackingService` using
  `Trip`/ledger gathered value.

### 5. `adapter/SessionHistory.java`
- Add gathered roll-ups to `CategoryDetail`: total gathered GP, average gathered GP per
  trip, and gathered GP per hour — mirroring the existing `gpPerHour` /
  `avgNetProfitPerTrip` / `xpAverages` fields. These are the "own averages" for the
  Gathered section.

### 6. UI (`adapter/runelite/NowTab.java`, `SessionsTab.java`, `StatsTab.java`)
- Render a distinct **Gathered** section: total gathered GP and gathered/hr on the Now
  tab, gathered totals per trip/session on Sessions, and gathered averages per category
  on Stats. Kept visually separate from the loot/profit section.

## Data flow

`StatChanged` → XP (existing). `ItemContainerChanged`/`VarbitChanged` → per-tick carried
snapshot → `TripLedger.updateCarried` → non-loot gain → `gathered` → `Trip` →
`StoredTrip`(JSON) / `TripSnapshot`(live) → `SessionHistory` roll-up → UI Gathered
section.

## Testing

`TripLedger`, `Trip`, and `SessionMapper` are plain domain/adapter logic with no
RuneLite client dependencies — unit-test directly, mirroring existing tests:
- Gather gain (no kill loot) → lands in `gathered`, not `pickedUp`.
- Gain matching `groundPool` → still `pickedUp`; only the surplus is `gathered`.
- Gather-then-bank within a trip → `gathered` retains the harvest; the deposit shows in
  `suppliesUsed` (documents the v1 banking limitation).
- `SessionMapper` round-trips `gathered` through JSON; missing `gathered` (old file)
  deserializes to empty.
- `Trip.gatheredValue` values via `ItemValuer` like `pickedUpValue`.

## Known limitations (v1)

- **Banking mid-trip** is charged as `suppliesUsed`. Until the "banking ends trip"
  config flag (follow-up task) exists, the accurate workflow is one gather run per trip,
  ending the trip at the bank.
- **False positives**: any non-loot inventory gain (shop, bank withdrawal, quest
  reward) is counted as gathered. A filter is a possible later refinement.

## Follow-up tasks

1. **Banking-ends-trip config flag** — RuneLite config setting; when enabled, opening
   the bank ends the current trip (so a gather run is one trip and deposits never hit
   supplies).
2. **Action counts & rates** — choose a signal (XP-event count recommended) and surface
   gathered actions/hr.
3. **Gathered false-positive filtering** — optionally exclude shop/bank/quest gains.
