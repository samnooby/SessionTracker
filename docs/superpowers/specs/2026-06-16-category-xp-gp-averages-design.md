# Per-Category XP Averages & Per-Session Averages — Design

**Date:** 2026-06-16
**Status:** Approved (brainstorming)
**Builds on:** Phase 3 UI, the per-trip XP breakdown, and the shared `Styles` helper.

## Overview

Two aggregated-average additions, both about surfacing averages that the per-trip
data already supports:

1. **Stats category detail — per-skill XP averages.** A new "XP averages" section
   showing, per skill, the **avg XP/trip** and **XP/hr**, plus a bold Total. Today the
   Stats detail shows only a single total XP/hr tile (no per-skill breakdown).
2. **Sessions — per-session averages.** When a session is expanded, a small summary
   card shows the session's **GP/hr** and **XP/hr** as the headline (two tiles), with
   **Avg net/trip** and **Avg XP/trip** beneath. Today a session row shows only its
   name and total net.

The GP-side category averages (GP/hr, avg net/trip, avg missed/trip, avg supplies/trip)
already exist on the Stats detail and are unchanged. **Kill averages remain deferred**
(separate backlog item).

## Decisions (from brainstorming)

- **Stats per-skill XP:** show **both** avg XP/trip and XP/hr per skill.
- **Sessions:** keep the headline as just **GP/hr + XP/hr**; place the extra per-trip
  averages (avg net/trip, avg XP/trip) in the session summary card beneath the tiles,
  not in the headline.
- Per-skill ordering: **alphabetical** by skill name (consistent with the per-trip XP
  breakdown).

## Architecture

### Data layer (headless, unit-tested)

All aggregation lives in `SessionHistory` (the existing read API). No core changes —
`CategoryStats`, `Session`, and the per-trip `Trip.xpGained()`/maps already expose
what's needed.

**Stats — per-skill XP averages:**
- New carrier `SessionHistory.SkillXpAverage` — `public final String skill;`,
  `public final long avgXpPerTrip;`, `public final long xpPerHour;`.
- `SessionHistory.CategoryDetail` gains `public final List<SkillXpAverage> xpAverages;`
  (alphabetical by skill).
- Computed in `categoryDetail(category)` in the same trip-iterating pass that already
  builds the supply averages: accumulate per-skill total XP across the category's trips;
  also accumulate the category's total wall-clock (sum of `session.wallClockMillis()`).
  Then per skill: `avgXpPerTrip = totalSkillXp / tripCount` and
  `xpPerHour = totalWallClock <= 0 ? 0 : totalSkillXp * 3_600_000 / totalWallClock`.
  (`tripCount` from the already-computed `CategoryStats.tripCount()`.)
- The Total row uses `sum(avgXpPerTrip)` (= total avg XP/trip) and the existing
  `CategoryDetail.xpPerHour` (the category's overall XP/hr).

**Sessions — per-session averages:**
- `SessionHistory.SessionSummary` gains `public final long xpPerHour;`,
  `public final long avgNetProfitPerTrip;`, `public final long avgXpPerTrip;`.
- Computed in `sessionsNewestFirst()` alongside the existing `gpPerHour`:
  `xpPerHour = session.xpPerHour()`; `avgNetProfitPerTrip = tripCount == 0 ? 0 :
  netProfit / tripCount`; `avgXpPerTrip = tripCount == 0 ? 0 : xpTotal / tripCount`.
  (`session.xpPerHour()` and `gpPerHour(fn)` already exist on the core `Session`.)

### UI layer (RuneLite glue, manual-verified)

Reuses existing `Styles` primitives (`card`, `tile`, `sectionHeader`, `keyLabel`,
`valueLabel`, `addBoldRow`, `capHeight`) and the GP-green / XP-blue accents. Text-only.

**Stats — "XP averages" section** (`StatsTab.renderDetail`): placed after the
"Avg supplies / trip" card. A 3-column grid (`GridLayout(0, 3, ...)`):
- A small header row: `Skill` · `/trip` · `/hr` (gray labels).
- One row per skill (alphabetical): skill name (gray) · avg XP/trip (XP-blue) ·
  XP/hr (XP-blue), formatted via `GpFormat.format`.
- A bold **Total** row: "Total" · total avg XP/trip · category XP/hr (both XP-blue).
- "None" when the category has no XP.

**Sessions — session summary card** (`SessionsTab`, shown when a session is expanded,
above the trip rows): a `Styles.card()` containing:
- Two stat tiles — **GP/hr** (green) and **XP/hr** (blue) — the headline.
- Two key/value rows beneath: **Avg net/trip** (sign-colored) and **Avg XP/trip**
  (XP-blue).
This card is inserted in `renderList()` between the expanded session's header row and
its trip rows.

## Data flow

```
stored sessions ─→ SessionHistory.sessionsNewestFirst() ─→ SessionSummary{gpPerHour, xpPerHour,
                                                            avgNetProfitPerTrip, avgXpPerTrip, ...}
                                                          └─→ SessionsTab summary card (when expanded)

stored sessions ─→ SessionHistory.categoryDetail(cat) ─→ CategoryDetail{..., xpAverages: List<SkillXpAverage>}
                                                        └─→ StatsTab "XP averages" 3-col table
```

## Testing

**Headless unit tests:**
- `SessionHistory.categoryDetail`: with trips carrying known per-skill XP, `xpAverages`
  lists each skill alphabetically with correct `avgXpPerTrip` (total/tripCount) and
  `xpPerHour` (total * 3.6M / category wall-clock); the per-skill avg/trip values sum to
  the overall avg XP/trip.
- `SessionHistory.sessionsNewestFirst`: a session with known trips exposes the right
  `xpPerHour`, `avgNetProfitPerTrip`, and `avgXpPerTrip`; zero-trip guards hold.

**Manual in-client verification:**
- Stats → a category detail shows the "XP averages" table (per-skill avg/trip + /hr) with
  a correct Total.
- Sessions → expand a session: the summary card shows GP/hr + XP/hr tiles and Avg net/trip
  + Avg XP/trip, above the trip rows.

## File touch list

- Modify: `SessionHistory.java` — new `SkillXpAverage` carrier; `CategoryDetail.xpAverages`
  (+ populate in `categoryDetail`); `SessionSummary` new fields (+ populate in
  `sessionsNewestFirst`).
- Modify: `StatsTab.java` — "XP averages" 3-column section in the category detail.
- Modify: `SessionsTab.java` — session summary card in the expanded-session render.
- Tests: `SessionHistoryTest.java`.

## Out of scope (deferred)

- **Kill averages** on Sessions/Stats (separate backlog item).
- Per-skill XP on the Sessions *trip-detail* view already exists (the per-trip XP card);
  this ticket is about the *aggregated* category/session averages only.
