# Trip & Session Durations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show how long trips and sessions took on the Sessions page, and add an average session length figure to the Stats category-detail view.

**Architecture:** The durations are already tracked (`Trip.durationMillis()`, `Session.wallClockMillis()`) and already exposed on the `SessionSummary`/`TripSummary` view models. Work is: (1) a shared compact duration formatter, (2) one new aggregate (`avgSessionDurationMillis`) plumbed through `CategoryStats` → `CategoryDetail`, (3) a `durationMillis` field on `TripDetail`, and (4) wiring all of it into the existing Swing rows/cards.

**Tech Stack:** Java 11, Swing (RuneLite client UI), Gradle, JUnit 4.

## Global Constraints

- Session duration = wall-clock span (`Session.wallClockMillis()` / `SessionSummary.wallClockMillis`), NOT the sum of trip durations.
- Follow existing panel patterns (`Styles.keyLabel`, `Styles.valueLabel`, `signedValue`, `Styles.card`, `Styles.sectionHeader`, `GridLayout(0, 2, 0, 3)` grids).
- New duration display strings come from `DurationFormat.compact(...)` — no hand-rolled formatting in the panels.
- Tests are JUnit 4 (`org.junit.Test`, `static org.junit.Assert.*`). Run with `./gradlew test`.
- All work happens in this worktree (branch `clever-hertz-42b4f2`), already rebased onto current `master`.

---

### Task 1: `DurationFormat.compact` formatter

**Files:**
- Create: `src/main/java/com/sessiontracker/adapter/DurationFormat.java`
- Test: `src/test/java/com/sessiontracker/adapter/DurationFormatTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `public static String DurationFormat.compact(long ms)` → `"0m"` for `ms <= 0`, `"45s"` (sub-minute), `"12m"` (sub-hour), `"1h 23m"` (>= 1h).

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/sessiontracker/adapter/DurationFormatTest.java`:

```java
package com.sessiontracker.adapter;

import static org.junit.Assert.*;
import org.junit.Test;

public class DurationFormatTest {

    @Test
    public void zeroAndNegativeRenderAsZeroMinutes() {
        assertEquals("0m", DurationFormat.compact(0));
        assertEquals("0m", DurationFormat.compact(-5_000));
    }

    @Test
    public void subSecondRendersAsZeroSeconds() {
        assertEquals("0s", DurationFormat.compact(999));
    }

    @Test
    public void subMinuteRendersSeconds() {
        assertEquals("45s", DurationFormat.compact(45_000));
        assertEquals("59s", DurationFormat.compact(59_999));
    }

    @Test
    public void subHourRendersWholeMinutes() {
        assertEquals("1m", DurationFormat.compact(60_000));
        assertEquals("12m", DurationFormat.compact(12 * 60_000L));
    }

    @Test
    public void hourOrMoreRendersHoursAndMinutes() {
        assertEquals("1h 0m", DurationFormat.compact(3_600_000L));
        assertEquals("1h 23m", DurationFormat.compact(4_980_000L));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'com.sessiontracker.adapter.DurationFormatTest'`
Expected: FAIL — compilation error, `DurationFormat` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `src/main/java/com/sessiontracker/adapter/DurationFormat.java`:

```java
package com.sessiontracker.adapter;

/** Formats a duration in millis as a short human string (e.g. "45s", "12m", "1h 23m"). */
public final class DurationFormat {

    private DurationFormat() {
    }

    public static String compact(long ms) {
        if (ms <= 0) {
            return "0m";
        }
        long totalSec = ms / 1000;
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        if (h > 0) {
            return h + "h " + m + "m";
        }
        if (m > 0) {
            return m + "m";
        }
        return totalSec + "s";
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests 'com.sessiontracker.adapter.DurationFormatTest'`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/sessiontracker/adapter/DurationFormat.java src/test/java/com/sessiontracker/adapter/DurationFormatTest.java
git commit -m "feat: add DurationFormat.compact for short duration strings"
```

---

### Task 2: `CategoryStats.avgSessionDurationMillis`

**Files:**
- Modify: `src/main/java/com/sessiontracker/core/CategoryStats.java`
- Test: `src/test/java/com/sessiontracker/core/CategoryAggregatorTest.java`

**Interfaces:**
- Consumes: existing `CategoryStats.from(String, List<Session>, Function<Trip, ItemValuer>)`, which already computes `totalWallClock` and `sessions.size()`.
- Produces: `public long CategoryStats.avgSessionDurationMillis()` = `sessionCount == 0 ? 0 : totalWallClock / sessionCount`.

- [ ] **Step 1: Write the failing test**

Add these two tests to `src/test/java/com/sessiontracker/core/CategoryAggregatorTest.java` (the file already imports `java.util.Arrays` and defines `oneGp`, `trip(...)`, `session(...)`):

```java
    @Test
    public void averageSessionDurationIsWallClockPerSession() {
        Session a = session("a", "Cat", trip("a1", 0, 3_600_000, 100, 0)); // 1h wall clock
        Session b = session("b", "Cat", trip("b1", 0, 1_800_000, 100, 0)); // 0.5h wall clock
        CategoryStats stats = CategoryStats.from("Cat", Arrays.asList(a, b), oneGp);
        // (3_600_000 + 1_800_000) / 2 sessions
        assertEquals(2_700_000L, stats.avgSessionDurationMillis());
    }

    @Test
    public void averageSessionDurationIsZeroWithNoSessions() {
        CategoryStats stats = CategoryStats.from("Cat", java.util.Collections.emptyList(), oneGp);
        assertEquals(0L, stats.avgSessionDurationMillis());
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'com.sessiontracker.core.CategoryAggregatorTest'`
Expected: FAIL — compilation error, `avgSessionDurationMillis()` does not exist.

- [ ] **Step 3: Write minimal implementation**

In `src/main/java/com/sessiontracker/core/CategoryStats.java`:

3a. Add the field next to the other fields (after `private final long avgTripDurationMillis;`):

```java
    private final long avgSessionDurationMillis;
```

3b. Add a constructor parameter and assignment. Change the constructor signature from:

```java
    private CategoryStats(String category, int sessionCount, int tripCount, long gpPerHour,
                          long xpPerHour, long avgTripDurationMillis, long avgNetProfitPerTrip,
                          long avgMissedPerTrip, double avgKillsPerTrip,
                          Map<ItemKey, Double> avgSuppliesPerTrip) {
```

to:

```java
    private CategoryStats(String category, int sessionCount, int tripCount, long gpPerHour,
                          long xpPerHour, long avgTripDurationMillis, long avgSessionDurationMillis,
                          long avgNetProfitPerTrip, long avgMissedPerTrip, double avgKillsPerTrip,
                          Map<ItemKey, Double> avgSuppliesPerTrip) {
```

and add inside the constructor body, after `this.avgTripDurationMillis = avgTripDurationMillis;`:

```java
        this.avgSessionDurationMillis = avgSessionDurationMillis;
```

3c. In `from(...)`, after the line `long avgDuration = tripCount == 0 ? 0 : totalTripDuration / tripCount;` add:

```java
        long avgSessionDuration = sessions.isEmpty() ? 0 : totalWallClock / sessions.size();
```

3d. Update the `return new CategoryStats(...)` call from:

```java
        return new CategoryStats(category, sessions.size(), tripCount, gpPerHour, xpPerHour,
                avgDuration, avgNet, avgMissed, avgKills, avgSupplies);
```

to:

```java
        return new CategoryStats(category, sessions.size(), tripCount, gpPerHour, xpPerHour,
                avgDuration, avgSessionDuration, avgNet, avgMissed, avgKills, avgSupplies);
```

3e. Add the getter next to `avgTripDurationMillis()`:

```java
    public long avgSessionDurationMillis() {
        return avgSessionDurationMillis;
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests 'com.sessiontracker.core.CategoryAggregatorTest'`
Expected: PASS (all existing tests plus the 2 new ones).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/sessiontracker/core/CategoryStats.java src/test/java/com/sessiontracker/core/CategoryAggregatorTest.java
git commit -m "feat: add avgSessionDurationMillis to CategoryStats"
```

---

### Task 3: Plumb durations through `SessionHistory` view models

**Files:**
- Modify: `src/main/java/com/sessiontracker/adapter/SessionHistory.java`
- Test: `src/test/java/com/sessiontracker/adapter/SessionHistoryTest.java`

**Interfaces:**
- Consumes: `CategoryStats.avgSessionDurationMillis()` (Task 2); `Trip.durationMillis()` (existing).
- Produces:
  - `SessionHistory.TripDetail` gains `public final long durationMillis;` (last constructor parameter).
  - `SessionHistory.CategoryDetail` gains `public final long avgSessionDurationMillis;` (added as a new constructor parameter immediately after `avgTripDurationMillis`).

- [ ] **Step 1: Write the failing test**

Add these two tests to `src/test/java/com/sessiontracker/adapter/SessionHistoryTest.java` (the file already defines `names`, `qty`, `trip`, `save`):

```java
    @Test
    public void tripDetailCarriesDuration() throws Exception {
        java.nio.file.Path root = java.nio.file.Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root, new com.google.gson.Gson());
        ItemKey coins = ItemKey.item(560);
        Map<ItemKey, Long> price = new HashMap<>();
        price.put(coins, 1L);
        save(store, "acct", "s", "Vorkath", "", 0, 1_800_000L,
                Arrays.asList(trip("t1", 0, 1_800_000L, 5, qty(coins, 80), new HashMap<>(),
                        new HashMap<>(), price)));

        SessionHistory history = new SessionHistory(store, "acct", names);
        SessionHistory.TripDetail d = history.tripDetail("s", "t1");
        assertEquals(1_800_000L, d.durationMillis);
    }

    @Test
    public void categoryDetailCarriesAvgSessionDuration() throws Exception {
        java.nio.file.Path root = java.nio.file.Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root, new com.google.gson.Gson());
        ItemKey coins = ItemKey.item(560);
        Map<ItemKey, Long> price = new HashMap<>();
        price.put(coins, 1L);
        save(store, "acct", "s1", "Vorkath", "", 0, 3_600_000L,
                Arrays.asList(trip("t1", 0, 3_600_000L, 5, qty(coins, 80), new HashMap<>(),
                        new HashMap<>(), price)));
        save(store, "acct", "s2", "Vorkath", "", 10_000_000L, 11_800_000L,
                Arrays.asList(trip("t2", 10_000_000L, 11_800_000L, 5, qty(coins, 80),
                        new HashMap<>(), new HashMap<>(), price)));

        SessionHistory history = new SessionHistory(store, "acct", names);
        SessionHistory.CategoryDetail d = history.categoryDetail("Vorkath");
        // (3_600_000 + 1_800_000) / 2 sessions
        assertEquals(2_700_000L, d.avgSessionDurationMillis);
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'com.sessiontracker.adapter.SessionHistoryTest'`
Expected: FAIL — compilation error, `durationMillis` / `avgSessionDurationMillis` not found on the view models.

- [ ] **Step 3: Write minimal implementation**

3a. `TripDetail` — add the field. In `SessionHistory.java`, change the `TripDetail` class. Add field after `public final List<ItemLine> usedLoot;`:

```java
        public final long durationMillis;
```

Change its constructor signature from:

```java
        public TripDetail(List<ItemLine> pickedUp, List<ItemLine> leftOnGround,
                          List<ItemLine> suppliesUsed, long netProfit, long missedValue,
                          List<SkillXp> xpGained, List<NpcKills> killsByNpc,
                          List<ItemLine> gathered, long gatheredValue, List<ItemLine> usedLoot) {
```

to add a trailing parameter:

```java
        public TripDetail(List<ItemLine> pickedUp, List<ItemLine> leftOnGround,
                          List<ItemLine> suppliesUsed, long netProfit, long missedValue,
                          List<SkillXp> xpGained, List<NpcKills> killsByNpc,
                          List<ItemLine> gathered, long gatheredValue, List<ItemLine> usedLoot,
                          long durationMillis) {
```

and add at the end of the constructor body, after `this.usedLoot = usedLoot;`:

```java
            this.durationMillis = durationMillis;
```

3b. Populate it in `tripDetail(...)`. Change the `return new TripDetail(...)` call from:

```java
                return new TripDetail(lines(t.pickedUpKept(), v), lines(t.missed(), v),
                        lines(t.suppliesUsed(), v), t.netProfit(v), t.missedValue(v),
                        SkillXp.sortedFrom(t.xpGained()), NpcKills.sortedByCountDesc(t.kills()),
                        lines(t.gatheredKept(), v), t.gatheredValue(v), lines(t.consumedLoot(), v));
```

to:

```java
                return new TripDetail(lines(t.pickedUpKept(), v), lines(t.missed(), v),
                        lines(t.suppliesUsed(), v), t.netProfit(v), t.missedValue(v),
                        SkillXp.sortedFrom(t.xpGained()), NpcKills.sortedByCountDesc(t.kills()),
                        lines(t.gatheredKept(), v), t.gatheredValue(v), lines(t.consumedLoot(), v),
                        t.durationMillis());
```

3c. `CategoryDetail` — add the field after `public final long avgTripDurationMillis;`:

```java
        public final long avgSessionDurationMillis;
```

Change its constructor signature to add the parameter right after `avgTripDurationMillis`. From:

```java
        public CategoryDetail(long gpPerHour, long xpPerHour, long avgNetProfitPerTrip,
                              long avgMissedPerTrip, long avgTripDurationMillis, double avgKillsPerTrip,
```

to:

```java
        public CategoryDetail(long gpPerHour, long xpPerHour, long avgNetProfitPerTrip,
                              long avgMissedPerTrip, long avgTripDurationMillis,
                              long avgSessionDurationMillis, double avgKillsPerTrip,
```

and add the assignment after `this.avgTripDurationMillis = avgTripDurationMillis;`:

```java
            this.avgSessionDurationMillis = avgSessionDurationMillis;
```

3d. Pass it through in `categoryDetail(...)`. Change the `return new CategoryDetail(...)` call from:

```java
        return new CategoryDetail(cs.gpPerHour(), cs.xpPerHour(), cs.avgNetProfitPerTrip(),
                cs.avgMissedPerTrip(), cs.avgTripDurationMillis(), cs.avgKillsPerTrip(),
```

to:

```java
        return new CategoryDetail(cs.gpPerHour(), cs.xpPerHour(), cs.avgNetProfitPerTrip(),
                cs.avgMissedPerTrip(), cs.avgTripDurationMillis(), cs.avgSessionDurationMillis(),
                cs.avgKillsPerTrip(),
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests 'com.sessiontracker.adapter.SessionHistoryTest'`
Expected: PASS (existing tests plus the 2 new ones).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/sessiontracker/adapter/SessionHistory.java src/test/java/com/sessiontracker/adapter/SessionHistoryTest.java
git commit -m "feat: carry trip duration and avg session length through view models"
```

---

### Task 4: Show durations on the Sessions page (`SessionsTab`)

**Files:**
- Modify: `src/main/java/com/sessiontracker/adapter/runelite/SessionsTab.java`

**Interfaces:**
- Consumes: `SessionSummary.wallClockMillis`, `TripSummary.durationMillis`, `TripDetail.durationMillis` (Task 3), `DurationFormat.compact` (Task 1).
- Produces: UI only.

No unit test — this is Swing rendering, verified by compiling and by the app's run skill, consistent with the rest of the panel code (no Swing tests exist).

- [ ] **Step 1: Add the import**

At the top of `SessionsTab.java`, with the other `com.sessiontracker.adapter` imports (next to the `GpFormat` import), add:

```java
import com.sessiontracker.adapter.DurationFormat;
```

(If `GpFormat` is imported there, place this immediately after it to keep import ordering.)

- [ ] **Step 2: Collapsed session row — add duration to the meta line**

In `sessionRow(...)`, the meta line currently builds:

```java
        String tripWord = s.tripCount == 1 ? "trip" : "trips";
        JLabel metaText = Styles.keyLabel(s.tripCount + " " + tripWord + " · ");
```

Change the `metaText` to include the duration:

```java
        String tripWord = s.tripCount == 1 ? "trip" : "trips";
        JLabel metaText = Styles.keyLabel(s.tripCount + " " + tripWord + " · "
                + DurationFormat.compact(s.wallClockMillis) + " · ");
```

- [ ] **Step 3: Expanded session summary card — add a Duration row**

In `sessionSummaryCard(...)`, the averages grid currently starts with the "Avg net / trip" row. Add a Duration row as the FIRST row of that grid — insert immediately after the grid is created and before `grid.add(Styles.keyLabel("Avg net / trip"));`:

```java
        grid.add(Styles.keyLabel("Duration"));
        JLabel duration = Styles.valueLabel(Styles.TEXT);
        duration.setText(DurationFormat.compact(s.wallClockMillis));
        grid.add(duration);
```

- [ ] **Step 4: Trip row — add duration to the label**

In `tripRow(...)`, the label currently is:

```java
        JLabel label = Styles.keyLabel("Trip " + number + " · " + t.kills + " kills · ");
```

Change it to include duration after the trip number:

```java
        JLabel label = Styles.keyLabel("Trip " + number + " · "
                + DurationFormat.compact(t.durationMillis) + " · " + t.kills + " kills · ");
```

- [ ] **Step 5: Trip detail view — add a Duration row**

In `renderDetail(...)`, the summary card grid currently adds "Net profit" then "Missed". Add a Duration row immediately after the "Missed" rows (after `grid.add(missed);` and before `Styles.capHeight(grid);`):

```java
            grid.add(Styles.keyLabel("Duration"));
            JLabel duration = Styles.valueLabel(Styles.TEXT);
            duration.setText(DurationFormat.compact(d.durationMillis));
            grid.add(duration);
```

- [ ] **Step 6: Compile to verify it builds**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/sessiontracker/adapter/runelite/SessionsTab.java
git commit -m "feat: show trip and session durations on the Sessions page"
```

---

### Task 5: Show durations on the Stats page (`StatsTab`)

**Files:**
- Modify: `src/main/java/com/sessiontracker/adapter/runelite/StatsTab.java`

**Interfaces:**
- Consumes: `CategoryDetail.avgTripDurationMillis` (existing), `CategoryDetail.avgSessionDurationMillis` (Task 3), `DurationFormat.compact` (Task 1).
- Produces: UI only.

No unit test — Swing rendering, verified by compile.

- [ ] **Step 1: Add the import**

At the top of `StatsTab.java`, immediately after `import com.sessiontracker.adapter.GpFormat;`, add:

```java
import com.sessiontracker.adapter.DurationFormat;
```

- [ ] **Step 2: Reformat the existing "Trip length" value**

In the detail-rendering method, the trip length is currently set as:

```java
        len.setText((d.avgTripDurationMillis / MILLIS_PER_MINUTE) + "m");
```

Change it to use the shared formatter:

```java
        len.setText(DurationFormat.compact(d.avgTripDurationMillis));
```

(Leave the `MILLIS_PER_MINUTE` constant in place; it may be used elsewhere. If after this change the IDE/compiler flags it as unused, remove the `private static final long MILLIS_PER_MINUTE = 60_000L;` declaration in the same commit.)

- [ ] **Step 3: Add a "Per session" section with average session length**

Immediately after the existing per-trip averages card is added to the body — i.e. right after the line `detailBody.add(avgCard);` — insert a new section:

```java
        detailBody.add(Styles.sectionHeader("Per session"));
        JPanel sessionCard = Styles.card();
        JPanel sessionGrid = grid();
        sessionGrid.add(Styles.keyLabel("Avg session length"));
        JLabel sessionLen = Styles.valueLabel(Styles.TEXT);
        sessionLen.setText(DurationFormat.compact(d.avgSessionDurationMillis));
        sessionGrid.add(sessionLen);
        Styles.capHeight(sessionGrid);
        sessionCard.add(sessionGrid);
        detailBody.add(sessionCard);
```

(`grid()` is the existing private helper in `StatsTab` that returns a 2-column grid panel; the per-trip averages card uses it.)

- [ ] **Step 4: Compile to verify it builds**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run the full test suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL — all tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/sessiontracker/adapter/runelite/StatsTab.java
git commit -m "feat: show avg trip and session length on the Stats page"
```

---

## Notes for the implementer

- After Task 5, optionally launch the app (via the project's run skill) to eyeball the four Sessions-page placements and the Stats "Per session" section. Rendering is not unit-tested.
- Duration uses wall-clock time for sessions everywhere, matching GP/hr — do not switch any of these to summed trip durations.
