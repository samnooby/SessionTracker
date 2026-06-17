# Per-Category Kill Averages & Per-Session Avg Kills — Design

**Date:** 2026-06-16
**Status:** Approved (brainstorming)
**Builds on:** the per-trip kills breakdown and the just-shipped per-category XP averages
(this is the kills analogue, in the same shape).

## Overview

Two aggregated kill-average additions, parallel to the XP averages:

1. **Stats category detail — per-NPC kill averages.** A new "Kill averages" section
   showing, per NPC, the **avg kills/trip** and **kills/hr**, plus a bold Total. Today the
   Stats detail shows only a single total avg-kills/trip figure (in "Per-trip averages").
2. **Sessions — per-session avg kills/trip.** The expanded-session summary card gains an
   **Avg kills/trip** row, beneath the existing Avg net/trip and Avg XP/trip.

The data already exists: `Trip.kills()` is a per-NPC `Map<String,Integer>`, persisted;
`CategoryStats.avgKillsPerTrip()` already gives the category-level total. This ticket adds
the per-NPC breakdown and the per-session figure.

## Decisions (from brainstorming)

- **Stats per-NPC kills:** show **both** avg kills/trip and kills/hr.
- **Ordering:** **most-killed-first** (avg kills/trip descending; ties broken
  alphabetically by NPC name) — consistent with the per-trip kills breakdown (not the
  alphabetical order used for XP).
- **Sessions:** add an **Avg kills/trip** row to the session summary card.

## Architecture

### Data layer (headless, unit-tested)

All aggregation lives in `SessionHistory`. No core changes — `CategoryStats`, `Session`,
and `Trip.kills()` already expose what's needed. Kill averages are fractional, so they are
`double` (rendered `%.1f`), matching the existing `avgKillsPerTrip`.

**Stats — per-NPC kill averages:**
- New carrier `SessionHistory.NpcKillAverage` — `public final String npc;`,
  `public final double avgPerTrip;`, `public final double perHour;`.
- `SessionHistory.CategoryDetail` gains `public final List<NpcKillAverage> killAverages;`,
  ordered by `avgPerTrip` descending, ties by `npc` ascending.
- Computed in `categoryDetail(category)`, reusing the already-computed `tripCount`
  (`CategoryStats.tripCount()`) and `totalWallClock` (sum of `session.wallClockMillis()`,
  the same value the per-skill XP `/hr` uses): accumulate per-NPC total kills across the
  category's trips (`Trip.kills()`), then per NPC
  `avgPerTrip = tripCount == 0 ? 0 : (double) totalNpcKills / tripCount` and
  `perHour = totalWallClock <= 0 ? 0 : (double) totalNpcKills * 3_600_000 / totalWallClock`.
  Sort the result by `avgPerTrip` descending (ties `npc` ascending).
- The Total row (rendered in the tab) is summed from the per-NPC rows:
  total avg kills/trip = `Σ avgPerTrip` and total kills/hr = `Σ perHour` over
  `killAverages`. Both sums are exact (the per-NPC values are `double`, not truncated),
  and equal the category totals (`Σ npcKills/tripCount` and `Σ npcKills*3.6M/wallClock`).

  > **Note on the existing `totalWallClock` computation:** the per-skill XP averages added
  > in the previous ticket already compute `totalWallClock = Σ session.wallClockMillis()`
  > inside `categoryDetail`. The kill-average loop reuses that same local, computed once.

**Sessions — per-session avg kills/trip:**
- `SessionHistory.SessionSummary` gains `public final double avgKillsPerTrip;`.
- Computed in `sessionsNewestFirst()`: sum `t.totalKills()` over `session.trips()` to get
  the session's total kills, then `avgKillsPerTrip = tripCount == 0 ? 0 :
  (double) totalKills / tripCount`.

### UI layer (RuneLite glue, manual-verified)

Reuses existing `Styles` primitives and the same 3-column table idiom as the XP averages.
Kill counts are neutral (not money/XP), so values render in the default `Styles.TEXT`
color. Values formatted `String.format(Locale.US, "%.1f", x)`.

**Stats — "Kill averages" section** (`StatsTab.renderDetail`): placed immediately **after**
the "XP averages" card. A 3-column grid (`GridLayout(0, 3, ...)`):
- A header row: `NPC` · `/trip` · `/hr` (gray).
- One row per NPC (most-killed-first): NPC name (gray) · avg/trip · /hr (both `%.1f`,
  `Styles.TEXT`).
- A bold **Total** row: "Total" · total avg kills/trip · category kills/hr.
- "None" when the category has no kills.

**Sessions — Avg kills/trip row** (`SessionsTab.sessionSummaryCard`): one more key/value
row after "Avg XP / trip": `"Avg kills / trip"` → `%.1f` of `s.avgKillsPerTrip`, in
`Styles.TEXT`.

## Data flow

```
stored sessions ─→ SessionHistory.sessionsNewestFirst() ─→ SessionSummary{..., avgKillsPerTrip}
                                                          └─→ SessionsTab summary card row

stored sessions ─→ SessionHistory.categoryDetail(cat) ─→ CategoryDetail{..., killAverages: List<NpcKillAverage>}
                                                        └─→ StatsTab "Kill averages" 3-col table
```

## Testing

**Headless unit tests:**
- `SessionHistory.categoryDetail`: with trips carrying known per-NPC kills, `killAverages`
  lists each NPC ordered most-killed-first (ties alphabetical) with correct `avgPerTrip`
  (totalNpcKills/tripCount) and `perHour` (totalNpcKills * 3.6M / category wall-clock).
- `SessionHistory.sessionsNewestFirst`: a session with known trips exposes the right
  `avgKillsPerTrip`; zero-trip guard holds.

**Manual in-client verification:**
- Stats → a category detail shows the "Kill averages" table (per-NPC avg/trip + /hr, Total)
  after the "XP averages" table.
- Sessions → expand a session: the summary card shows an "Avg kills / trip" row beneath
  "Avg XP / trip".

## File touch list

- Modify: `SessionHistory.java` — new `NpcKillAverage` carrier; `CategoryDetail.killAverages`
  (+ populate in `categoryDetail`); `SessionSummary.avgKillsPerTrip` (+ populate in
  `sessionsNewestFirst`).
- Modify: `StatsTab.java` — "Kill averages" 3-column section after the XP averages section.
- Modify: `SessionsTab.java` — "Avg kills / trip" row in the session summary card.
- Tests: `SessionHistoryTest.java`.

## Out of scope (deferred)

None specific — this completes the per-trip → per-category/per-session aggregation for kills
(parallel to XP). Larger backlog items (overlay, export, resume-session, tracking-accuracy
limitations) remain separate.
