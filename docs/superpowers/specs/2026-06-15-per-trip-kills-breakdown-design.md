# Per-Trip Kills Breakdown by NPC — Design

**Date:** 2026-06-15
**Status:** Approved (brainstorming)
**Builds on:** Phase 3 UI, the shared `Styles` helper, and the per-skill XP breakdown
(which this directly parallels).

## Overview

Surface, for each trip, the **per-NPC kill counts** — how many of each NPC was killed
(e.g. 20 Goblins, 10 Birds) — on the **current trip** (Now tab) and on **previous
trips** (Sessions → trip detail). The data already exists: the core `Trip` carries a
`kills` `Map<String,Integer>` keyed by NPC name (incremented by `TripLedger.recordKill`)
and it is persisted with every stored trip. Today only the aggregate `totalKills` (the
"Kills" line / `TripSnapshot.kills`) is exposed. This ticket exposes the breakdown and
renders it.

This is the kills analogue of the per-skill XP breakdown; it follows the same shape.

## Decisions (from brainstorming)

- **Order:** most-killed first (descending by count); ties broken alphabetically by NPC
  name for stable ordering.
- **Total:** the Kills breakdown card/group shows a bold **Total** row on both tabs. On
  the Now tab this coexists with the existing Current-trip "Kills" total line — the
  redundancy is accepted (may be revisited later). Same treatment on both Now and
  Sessions.
- **Icons:** text-only (NPC name + count). NPCs have no clean bundled icon set the way
  skills do, so no icons.

## Architecture

### Data layer (headless, unit-tested)

The carrier stays RuneLite-free — NPC is a plain `String`, matching how kills are stored.

- **New value object** `com.goodrunetracker.adapter.NpcKills` — `public final` with
  `public final String npc;` and `public final int count;` and a constructor. (Mirrors
  `SkillXp`.)
- **New factory** `NpcKills.sortedByCountDesc(Map<String,Integer> kills)` → a
  `List<NpcKills>` ordered by `count` descending, with ties broken by `npc` ascending
  (so the order is deterministic).
- **`TripSnapshot`** gains a field `public final java.util.List<NpcKills> killsByNpc;`
  added to its constructor (as the new last parameter, after the existing `xpBySkill`).
  Populated in `TrackingService.computeSnapshot()` from the live `trip.kills()`. Used by
  the Now tab.
- **`SessionHistory.TripDetail`** gains a field `public final List<NpcKills> killsByNpc;`
  added to its constructor (new last parameter, after `xpGained`). Populated in
  `SessionHistory.tripDetail()` from the stored trip's reconstructed `Trip.kills()`. Used
  by the Sessions trip-detail view.
- The **Total** is the sum of the list's `count` values (equivalently `trip.totalKills()`),
  computed by the renderer — no redundant stored field.

### UI layer (RuneLite glue, manual-verified)

Reuses the existing `Styles` primitives (`card`, `sectionHeader`, `keyLabel`,
`valueLabel`, `addBoldRow`) and a text-only row (NPC name + count). No icons, so no
`SkillIconManager`-style plumbing is needed; the tabs already have what they need.

- **Now tab — "Kills" card:** a new card placed **after** the Current trip card and
  **before** the "XP gained" card. One row per NPC, most-killed first: NPC name (gray) on
  the left, count on the right; then a bold **Total** row. Shows a single "None" row when
  the current trip has no kills. Rebuilt on each `render()` (the kill set is dynamic),
  like the XP card. The Current-trip card keeps its existing "Kills" total line.
- **Sessions → trip detail — "Kills" group:** a new section
  (`Styles.sectionHeader("Kills")` + card) placed **after** the Net/Missed summary card
  and **before** the "Picked up" group, with the same row format and a bold **Total**.
  "None" when the trip has no kills.
- Count values render via `Integer.toString(count)` in the default text color
  (`Styles.TEXT`) — kills aren't profit/XP, so no green/blue accent; the Total row uses
  `Styles.TEXT` for its value too.

### Row color note

The XP rows use the XP-blue accent. Kill counts are neither GP nor XP, so they use the
neutral `Styles.TEXT` color for the per-NPC counts and the bold Total — keeping kills
visually distinct from the colored money/XP figures.

## Data flow

```
NpcLootReceived ─→ TrackingService.onKill(npc, drops) ─→ TripLedger.recordKill(npc, drops)
                                                              │  (Trip.kills: Map<String,Integer>)
            live trip ─→ computeSnapshot() ─→ TripSnapshot.killsByNpc ─→ NowTab "Kills" card
            stored trip ─→ SessionMapper.toTrip ─→ SessionHistory.tripDetail()
                                                  └─→ TripDetail.killsByNpc ─→ SessionsTab detail group
```

## Testing

**Headless unit tests:**
- `NpcKills.sortedByCountDesc`: orders by count descending; ties broken alphabetically;
  empty map → empty list.
- `TrackingService`: after kills of multiple NPC types on a trip,
  `currentSnapshot().killsByNpc` lists each NPC most-killed-first with correct counts; the
  list's counts sum to the snapshot's `kills`; empty before any kill.
- `SessionHistory`: `tripDetail(...).killsByNpc` lists the stored trip's per-NPC counts in
  count-desc order, matching what was persisted (seed a stored trip with a known `kills`
  map and assert).

**Manual in-client verification:**
- Now tab: kill several NPCs of different types; the "Kills" card lists each NPC and count,
  most-killed first, updating live, with a correct Total; shows "None" before any kill; the
  Current-trip "Kills" line still shows the total.
- Sessions → trip detail: a past trip shows its per-NPC kill breakdown with Total, after the
  summary and before "Picked up".

## File touch list

- New: `src/main/java/com/goodrunetracker/adapter/NpcKills.java`
- Modify: `TripSnapshot.java` (add `killsByNpc` + constructor param)
- Modify: `TrackingService.java` (`computeSnapshot` builds the list)
- Modify: `SessionHistory.java` (`TripDetail.killsByNpc` + populate in `tripDetail`)
- Modify: `NowTab.java` ("Kills" card)
- Modify: `SessionsTab.java` ("Kills" detail group)
- Tests: `NpcKillsTest.java` (new), `TrackingServiceTest.java`, `SessionHistoryTest.java`

## Out of scope (deferred)

Kill averages on Sessions and Stats (a later ticket, parallel to the deferred XP averages).
