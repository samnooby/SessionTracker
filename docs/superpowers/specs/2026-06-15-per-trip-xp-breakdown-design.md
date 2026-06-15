# Per-Trip XP Breakdown by Skill — Design

**Date:** 2026-06-15
**Status:** Approved (brainstorming)
**Builds on:** Phase 3 UI (Now / Sessions / Stats tabs) and the shared `Styles` helper.

## Overview

Surface, for each trip, the **per-skill XP gained** — which skills gained XP and how
much — on the **current trip** (Now tab) and on **previous trips** (Sessions →
trip detail). The data already exists: the core `Trip` carries an `xpGained`
`Map<String,Long>` keyed by skill (fed tick-by-tick from `StatChanged`) and it is
persisted with every stored trip. Today only the aggregate `totalXp` is exposed in
the view models. This ticket exposes the breakdown and renders it.

XP **averages** on Sessions and Stats are explicitly **out of scope** (a later ticket).

## Decisions (from brainstorming)

- **Order:** alphabetical by skill name (stable; doesn't reshuffle as live XP accrues).
- **Total:** include a bold **Total** row summing all per-skill XP (mirrors the
  "Total" line in the Stats supplies breakdown).
- **Icons:** show each skill's RuneLite icon (small variant) next to its name.

## Architecture

### Data layer (headless, unit-tested)

The data carriers stay free of RuneLite `Skill`/icon types — skill is a plain
`String`, matching how XP is already stored.

- **New value object** `com.goodrunetracker.adapter.SkillXp` — `public final`
  with `public final String skill;` and `public final long xp;` and a constructor.
  (Mirrors the existing plain carriers `TripSnapshot` / `SessionSnapshot`.)
- **`TripSnapshot`** gains a field `public final java.util.List<SkillXp> xpBySkill;`
  added to its constructor. Populated in `TrackingService.computeSnapshot()` from
  the live `trip.xpGained()` map, sorted alphabetically by skill name. Used by the
  Now tab's current-trip view.
- **`SessionHistory.TripDetail`** gains a field `public final List<SkillXp> xpGained;`
  added to its constructor. Populated in `SessionHistory.tripDetail()` from the
  stored trip's reconstructed `Trip.xpGained()` map, sorted alphabetically. Used by
  the Sessions trip-detail view.
- Sorting: alphabetical by `skill` (case-sensitive natural order is fine — skill
  names are proper-case display names from RuneLite, e.g. "Attack", "Ranged").
- The **Total** is the sum of the list's `xp` values; computed by the renderer (not
  stored), so there is no redundant field to keep in sync.

### UI layer (RuneLite glue, manual-verified)

Skill icons come from RuneLite's `SkillIconManager`, which loads icons from bundled
plugin resources (not the sprite cache), so resolution is synchronous and EDT-safe —
no client-thread hop required.

- **Icon map built once at startup:** `GoodRuneTrackerPlugin` injects
  `SkillIconManager`. In `startUp()` it builds a `Map<String, javax.swing.Icon>`
  keyed by skill name: iterate `net.runelite.api.Skill.values()`, and for each map
  `skill.getName()` → an `ImageIcon` of `skillIconManager.getSkillImage(skill, true)`
  (the small variant), scaled to a consistent small size (~16px) for the list rows.
  This map is passed to the panel and on to the tabs at construction.
- **Plumbing:** `GoodRuneTrackerPanel` constructor takes the icon map (alongside
  `ClientThread`) and forwards it to `NowTab` and `SessionsTab`. The icon map is
  login-independent, so it is wired at construction, not via `setService`.
- **Now tab — "XP gained" card:** a new card placed immediately **after** the
  Current trip card (and before "Session so far"). One row per skill, alphabetical:
  `[icon] Skill` on the left, XP value (XP-blue accent, via `GpFormat.format`) on the
  right; then a bold **Total** row. Shows a single "None" row when the current trip
  has gained no XP. The card's contents are rebuilt on each `render()` (the skill set
  is dynamic), like the other live stats.
- **Sessions → trip detail — "XP gained" group:** a new section (`Styles.sectionHeader`
  + card) placed **after** the "Supplies used" group, with the same row format and a
  bold **Total**. "None" when the trip gained no XP.
- Both reuse the existing `Styles` primitives (`card`, `keyLabel`, `valueLabel`,
  bold Total label) and the XP-blue accent color for values, for visual consistency
  with the XP/hr tile.

### Skill-name → Skill mapping

Stored skill names are the proper-case display names from
`event.getSkill().getName()` (e.g. "Attack", "Hitpoints", "Ranged"). The icon map is
built by iterating `Skill.values()` and keying on `getName()`, so lookups by the
stored name resolve directly. A skill name with no matching icon (should not occur)
renders the row without an icon — the row still shows name + XP.

## Data flow

```
StatChanged ─→ TrackingService.onXp(skill, totalXp) ─→ TripLedger.recordXp(skill, delta)
                                                              │  (Trip.xpGained: Map<String,Long>)
            live trip ─→ computeSnapshot() ─→ TripSnapshot.xpBySkill ─→ NowTab "XP gained" card
            stored trip ─→ SessionMapper.toTrip ─→ SessionHistory.tripDetail()
                                                  └─→ TripDetail.xpGained ─→ SessionsTab detail group
   Skill icons:  SkillIconManager ─(startup)→ Map<String,Icon> ─→ NowTab / SessionsTab (render lookup)
```

## Testing

**Headless unit tests:**
- `TrackingService`: after kills + XP across a trip, `currentSnapshot().xpBySkill`
  lists each skill that gained XP, alphabetical, with correct amounts; empty when no
  XP gained; the list sums to the snapshot's `totalXp`.
- `SessionHistory`: `tripDetail(...).xpGained` lists the stored trip's per-skill XP,
  alphabetical, matching what was persisted (seed a stored trip with a known
  `xpGained` map and assert).

**Manual in-client verification:**
- Now tab: train multiple skills on a trip; the "XP gained" card lists each skill
  (with icon) and amount, alphabetical, updating live, with a correct Total; shows
  "None" before any XP.
- Sessions → trip detail: a past trip shows its per-skill XP breakdown with icons and
  Total.

## File touch list

- New: `src/main/java/com/goodrunetracker/adapter/SkillXp.java`
- Modify: `TripSnapshot.java` (add `xpBySkill` + constructor param)
- Modify: `TrackingService.java` (`computeSnapshot` builds the list)
- Modify: `SessionHistory.java` (`TripDetail.xpGained` + populate in `tripDetail`)
- Modify: `GoodRuneTrackerPlugin.java` (inject `SkillIconManager`, build icon map, pass down)
- Modify: `GoodRuneTrackerPanel.java` (accept + forward icon map)
- Modify: `NowTab.java` ("XP gained" card)
- Modify: `SessionsTab.java` ("XP gained" detail group)
- Tests: `TrackingServiceTest.java`, `SessionHistoryTest.java`
