# Trip & Session Durations + Avg Session Length Stat

## Goal

Surface how long trips and sessions took on the Sessions page, and add an
average session length figure to the Stats page (the per-category detail
already shows average trip length).

## Background

The data is already tracked and persisted; this work is almost entirely
rendering plus one new aggregate. No new persistence and no migration.

- `Trip.durationMillis()` = `endMillis - startMillis`.
- `Session.wallClockMillis()` = first trip's start to last trip's end (real
  elapsed time, including banking/walking gaps between trips). This is the
  basis GP/hr is already computed against.
- View models already carry the values the UI needs:
  - `SessionHistory.SessionSummary.wallClockMillis`
  - `SessionHistory.TripSummary.durationMillis`
  - `CategoryStats` already sums `totalWallClock` internally.

## Decisions

- **Session duration = wall-clock span** (`wallClockMillis`), consistent with
  GP/hr. Not the sum of trip durations.
- **Sessions-page placement:** collapsed session row, expanded summary card,
  trip row, and trip detail view — all four.
- **Stats placement:** category detail view only. No category-card surfacing,
  no global cross-category summary.
- **Avg session length grouping:** its own new "Per session" section header in
  the category detail (it is a per-session figure, not a per-trip average).

## Design

### 1. Shared duration formatter

Duration formatting is currently scattered: `NowTab.formatElapsed()` renders a
live clock (`m:ss` / `h:mm:ss`) and `StatsTab` hand-rolls `"Xm"`. A
live-ticking clock reads oddly in static lists, so add a sibling to
`GpFormat` in the same package:

- **File:** `src/main/java/com/sessiontracker/adapter/DurationFormat.java`
- **Method:** `public static String compact(long ms)`
- **Format rules:**
  - `ms <= 0` → `"0m"`
  - `< 60s` → `"45s"`
  - `< 60m` → `"12m"` (seconds dropped once minutes are shown)
  - `>= 60m` → `"1h 23m"`

`NowTab`'s live elapsed timer keeps its own clock format (it ticks every
second — a clock is appropriate there) and is left unchanged. `StatsTab`'s
existing `"Xm"` trip-length text switches to `DurationFormat.compact` for
consistency.

### 2. Sessions page (`SessionsTab`)

- **Collapsed session row** (`sessionRow`, around line 138): append the
  duration to the meta line between trip count and net profit:
  `"3 trips · 1h 23m · "` + net. Uses `s.wallClockMillis`.
- **Expanded summary card:** add a `Duration` key/value row alongside the
  existing GP/hr, XP/hr, avg net/trip, avg XP/trip, avg kills/trip rows.
- **Trip row** (`tripRow`, around line 181): include duration in the label:
  `"Trip 1 · 12m · 47 kills · "` + net. Uses `t.durationMillis`.
- **Trip detail view** (`renderDetail`): add a `Duration` row near net profit.
  Requires the duration to be available in the detail path — pass
  `TripSummary.durationMillis` through, or read it from the already-loaded
  trip; `TripDetail` does not currently carry duration, so add a
  `durationMillis` field to `TripDetail` and populate it in
  `SessionHistory.tripDetail` (it already constructs the `Trip`, so
  `t.durationMillis()` is available).

### 3. Stats page (`StatsTab`) — category detail only

- `CategoryStats`: add `avgSessionDurationMillis` field, computed as
  `sessionCount == 0 ? 0 : totalWallClock / sessionCount`, with a
  `avgSessionDurationMillis()` getter. `totalWallClock` and `sessionCount`
  are already computed in `from(...)`.
- `SessionHistory.CategoryDetail`: add `avgSessionDurationMillis` field and
  pass `cs.avgSessionDurationMillis()` through in `categoryDetail(...)`.
- `StatsTab` detail rendering:
  - Existing **Trip length** row stays in the "Per-trip averages" card,
    reformatted via `DurationFormat.compact(d.avgTripDurationMillis)`.
  - New **"Per session"** section header + card with an **Avg session length**
    row showing `DurationFormat.compact(d.avgSessionDurationMillis)`.

## Components touched

| File | Change |
|------|--------|
| `adapter/DurationFormat.java` | NEW — `compact(long ms)` formatter |
| `adapter/runelite/SessionsTab.java` | duration in session row, summary card, trip row, trip detail |
| `adapter/runelite/StatsTab.java` | reformat trip length; add "Per session" / avg session length |
| `core/CategoryStats.java` | add `avgSessionDurationMillis` field + getter |
| `adapter/SessionHistory.java` | carry `avgSessionDurationMillis` in `CategoryDetail`; add `durationMillis` to `TripDetail` |

## Testing

- `DurationFormatTest`: boundaries — `0`, sub-minute, exact minute, sub-hour,
  hour rollover (e.g. `3_600_000` → `"1h 0m"`, `4_980_000` → `"1h 23m"`).
- `CategoryStatsTest`: `avgSessionDurationMillis()` across multiple sessions
  and the zero-session guard (returns `0`).

Swing rendering is wired by hand following the existing row/card patterns and
is not unit-tested (consistent with the rest of the panel code).

## Out of scope

- Both lengths on the category stats card.
- Global cross-category duration summary.
- Changes to `NowTab`'s live elapsed timer.
- Any change to how durations are tracked or persisted.
