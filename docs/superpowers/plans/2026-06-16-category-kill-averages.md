# Per-Category Kill Averages & Per-Session Avg Kills — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add per-NPC kill averages (avg/trip + /hr) to the Stats category detail, and an aggregate avg-kills/trip to the expanded Sessions summary card.

**Architecture:** All aggregation in `SessionHistory` — no core changes. `SessionSummary` gains `avgKillsPerTrip`; `CategoryDetail` gains a `NpcKillAverage` list computed in `categoryDetail()` (reusing its already-computed `totalWallClock`). The Swing tabs render with existing `Styles` primitives. Directly parallels the per-category XP averages already in master.

**Tech Stack:** Java 11, JUnit 4, Swing. Build/test: `./gradlew test`.

---

## File structure

- Modify: `SessionHistory.java` — `SessionSummary.avgKillsPerTrip` (+ populate); new `NpcKillAverage` carrier; `CategoryDetail.killAverages` (+ populate).
- Modify: `SessionsTab.java` — "Avg kills / trip" row in the session summary card.
- Modify: `StatsTab.java` — "Kill averages" 3-column section after the XP averages section.
- Tests: `SessionHistoryTest.java`.

---

## Task 1: Per-session avg kills/trip on SessionSummary

Add `avgKillsPerTrip` (double) to `SessionSummary`, computed in `sessionsNewestFirst()`.

**Files:**
- Modify: `src/main/java/com/goodrunetracker/adapter/SessionHistory.java`
- Test: `src/test/java/com/goodrunetracker/adapter/SessionHistoryTest.java`

- [ ] **Step 1: Write the failing test**

Add to `SessionHistoryTest.java`:

```java
    @Test
    public void sessionSummaryExposesAvgKillsPerTrip() throws Exception {
        Path root = Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root);
        Map<String, Integer> killsA = new HashMap<>();
        killsA.put("Goblin", 3);
        Map<String, Integer> killsB = new HashMap<>();
        killsB.put("Goblin", 1);
        killsB.put("Cow", 2);
        Trip t1 = new Trip("t1", 0, 1_800_000L, false, killsA, new HashMap<>(),
                new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
        Trip t2 = new Trip("t2", 1_800_000L, 3_600_000L, false, killsB, new HashMap<>(),
                new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
        save(store, "acct", "s", "Vorkath", "", 0, 3_600_000L,
                Arrays.asList(SessionMapper.toStored(t1, new HashMap<>()),
                        SessionMapper.toStored(t2, new HashMap<>())));

        SessionHistory history = new SessionHistory(store, "acct", names);
        SessionHistory.SessionSummary sum = history.sessionsNewestFirst().get(0);

        // total kills = 3 + 3 = 6 over 2 trips = 3.0
        assertEquals(3.0, sum.avgKillsPerTrip, 0.0001);
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew test --tests 'com.goodrunetracker.adapter.SessionHistoryTest'`
Expected: FAIL — `SessionSummary` has no `avgKillsPerTrip`.

- [ ] **Step 3: Add the field to SessionSummary**

In `SessionHistory.java`, add `avgKillsPerTrip` as the LAST field (after `avgXpPerTrip`) and LAST constructor param:

```java
        public final long xpPerHour;
        public final long avgNetProfitPerTrip;
        public final long avgXpPerTrip;
        public final double avgKillsPerTrip;

        public SessionSummary(String sessionId, String name, String category, int tripCount,
                              long netProfit, long gpPerHour, long xpTotal, long wallClockMillis,
                              long startMillis, long xpPerHour, long avgNetProfitPerTrip,
                              long avgXpPerTrip, double avgKillsPerTrip) {
            this.sessionId = sessionId;
            this.name = name;
            this.category = category;
            this.tripCount = tripCount;
            this.netProfit = netProfit;
            this.gpPerHour = gpPerHour;
            this.xpTotal = xpTotal;
            this.wallClockMillis = wallClockMillis;
            this.startMillis = startMillis;
            this.xpPerHour = xpPerHour;
            this.avgNetProfitPerTrip = avgNetProfitPerTrip;
            this.avgXpPerTrip = avgXpPerTrip;
            this.avgKillsPerTrip = avgKillsPerTrip;
        }
```

(Leave the fields above `xpPerHour` unchanged; only add the new `avgKillsPerTrip` field + param + assignment.)

- [ ] **Step 4: Populate it in sessionsNewestFirst()**

In the `for` loop body, compute the session's total kills and average, and pass it as the new final arg:

```java
        for (StoredSession s : stored) {
            Session session = SessionMapper.toSession(s);
            Function<Trip, ItemValuer> fn = SessionMapper.valuerFor(s);
            int tripCount = s.trips.size();
            long net = session.totalNetProfit(fn);
            long xpTotal = session.totalXp();
            long avgNet = tripCount == 0 ? 0 : net / tripCount;
            long avgXp = tripCount == 0 ? 0 : xpTotal / tripCount;
            long totalKills = 0;
            for (Trip t : session.trips()) {
                totalKills += t.totalKills();
            }
            double avgKills = tripCount == 0 ? 0 : (double) totalKills / tripCount;
            out.add(new SessionSummary(s.id, s.name, s.category, tripCount,
                    net, session.gpPerHour(fn), xpTotal, session.wallClockMillis(), s.startMillis,
                    session.xpPerHour(), avgNet, avgXp, avgKills));
        }
```

(`Trip.totalKills()` returns int; `Session.trips()` returns `List<Trip>`. Leave the `out.sort(...)` line unchanged.)

- [ ] **Step 5: Run the test to verify it passes + the full suite**

Run: `./gradlew test --tests 'com.goodrunetracker.adapter.SessionHistoryTest'` then `./gradlew test`
Expected: PASS (new + existing).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/SessionHistory.java \
        src/test/java/com/goodrunetracker/adapter/SessionHistoryTest.java
git commit -m "feat: per-session avg kills/trip on SessionSummary"
```

---

## Task 2: Per-NPC kill averages on CategoryDetail

Add a `NpcKillAverage` carrier and `CategoryDetail.killAverages` (avg/trip + /hr per NPC, most-killed-first), computed in `categoryDetail()`.

**Files:**
- Modify: `src/main/java/com/goodrunetracker/adapter/SessionHistory.java`
- Test: `src/test/java/com/goodrunetracker/adapter/SessionHistoryTest.java`

- [ ] **Step 1: Write the failing test**

Add to `SessionHistoryTest.java`:

```java
    @Test
    public void categoryDetailListsPerNpcKillAveragesMostKilledFirst() throws Exception {
        Path root = Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root);
        Map<String, Integer> kA = new HashMap<>();
        kA.put("Goblin", 20);
        kA.put("Cow", 10);
        Map<String, Integer> kB = new HashMap<>();
        kB.put("Goblin", 20);
        Trip t1 = new Trip("t1", 0, 1_800_000L, false, kA, new HashMap<>(),
                new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
        Trip t2 = new Trip("t2", 1_800_000L, 3_600_000L, false, kB, new HashMap<>(),
                new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
        save(store, "acct", "s", "Vorkath", "", 0, 3_600_000L,
                Arrays.asList(SessionMapper.toStored(t1, new HashMap<>()),
                        SessionMapper.toStored(t2, new HashMap<>())));

        SessionHistory history = new SessionHistory(store, "acct", names);
        SessionHistory.CategoryDetail d = history.categoryDetail("Vorkath");

        assertEquals(2, d.killAverages.size());
        // Goblin total 40 over 2 trips -> 20/trip; over 1h -> 40/hr (most killed first)
        assertEquals("Goblin", d.killAverages.get(0).npc);
        assertEquals(20.0, d.killAverages.get(0).avgPerTrip, 0.0001);
        assertEquals(40.0, d.killAverages.get(0).perHour, 0.0001);
        // Cow total 10 over 2 trips -> 5/trip; over 1h -> 10/hr
        assertEquals("Cow", d.killAverages.get(1).npc);
        assertEquals(5.0, d.killAverages.get(1).avgPerTrip, 0.0001);
        assertEquals(10.0, d.killAverages.get(1).perHour, 0.0001);
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew test --tests 'com.goodrunetracker.adapter.SessionHistoryTest'`
Expected: FAIL — `NpcKillAverage` / `CategoryDetail.killAverages` do not exist.

- [ ] **Step 3: Add the NpcKillAverage carrier**

In `SessionHistory.java`, add the carrier alongside the other nested carriers (e.g. after `SkillXpAverage`):

```java
    public static final class NpcKillAverage {
        public final String npc;
        public final double avgPerTrip;
        public final double perHour;

        public NpcKillAverage(String npc, double avgPerTrip, double perHour) {
            this.npc = npc;
            this.avgPerTrip = avgPerTrip;
            this.perHour = perHour;
        }
    }
```

- [ ] **Step 4: Add the field to CategoryDetail**

Add `killAverages` as the LAST field and LAST constructor param of `CategoryDetail` (after `xpAverages`):

```java
        public final List<SkillXpAverage> xpAverages;
        public final List<NpcKillAverage> killAverages;

        public CategoryDetail(long gpPerHour, long xpPerHour, long avgNetProfitPerTrip,
                              long avgMissedPerTrip, long avgTripDurationMillis, double avgKillsPerTrip,
                              List<SupplyAverage> supplies, long avgTotalSuppliesGpPerTrip,
                              List<SkillXpAverage> xpAverages, List<NpcKillAverage> killAverages) {
            this.gpPerHour = gpPerHour;
            this.xpPerHour = xpPerHour;
            this.avgNetProfitPerTrip = avgNetProfitPerTrip;
            this.avgMissedPerTrip = avgMissedPerTrip;
            this.avgTripDurationMillis = avgTripDurationMillis;
            this.avgKillsPerTrip = avgKillsPerTrip;
            this.supplies = supplies;
            this.avgTotalSuppliesGpPerTrip = avgTotalSuppliesGpPerTrip;
            this.xpAverages = xpAverages;
            this.killAverages = killAverages;
        }
```

(Leave the fields above `xpAverages` unchanged; only add the new `killAverages` field + param + assignment.)

- [ ] **Step 5: Compute killAverages in categoryDetail()**

In `categoryDetail(category)`, after the existing `xpAverages` block and BEFORE the `return new CategoryDetail(...)` line, add the per-NPC kill aggregation. It reuses the `totalWallClock` and `tripCount` locals already computed:

```java
        Map<String, Integer> npcTotalKills = new HashMap<>();
        for (Session s : sessions) {
            for (Trip t : s.trips()) {
                for (Map.Entry<String, Integer> e : t.kills().entrySet()) {
                    npcTotalKills.merge(e.getKey(), e.getValue(), Integer::sum);
                }
            }
        }
        List<NpcKillAverage> killAverages = new ArrayList<>();
        for (Map.Entry<String, Integer> e : npcTotalKills.entrySet()) {
            double avgTrip = tripCount == 0 ? 0 : (double) e.getValue() / tripCount;
            double perHour = totalWallClock <= 0 ? 0
                    : (double) e.getValue() * MILLIS_PER_HOUR / totalWallClock;
            killAverages.add(new NpcKillAverage(e.getKey(), avgTrip, perHour));
        }
        killAverages.sort(Comparator.comparingDouble((NpcKillAverage k) -> k.avgPerTrip).reversed()
                .thenComparing(k -> k.npc));
```

Then change the `return` to pass it as the new final arg:

```java
        return new CategoryDetail(cs.gpPerHour(), cs.xpPerHour(), cs.avgNetProfitPerTrip(),
                cs.avgMissedPerTrip(), cs.avgTripDurationMillis(), cs.avgKillsPerTrip(),
                supplies, avgTotalSupplies, xpAverages, killAverages);
```

(`Comparator` and `HashMap` are already imported in `SessionHistory`. `MILLIS_PER_HOUR`, `tripCount`, `sessions`, and `totalWallClock` are already in scope.)

- [ ] **Step 6: Run the test to verify it passes + the full suite**

Run: `./gradlew test --tests 'com.goodrunetracker.adapter.SessionHistoryTest'` then `./gradlew test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/SessionHistory.java \
        src/test/java/com/goodrunetracker/adapter/SessionHistoryTest.java
git commit -m "feat: per-NPC kill averages (avg/trip + /hr) on CategoryDetail"
```

---

## Task 3: Sessions — Avg kills/trip row in the summary card

Add one row to the expanded-session summary card. Swing glue — no unit test, must compile and `./gradlew test` stay green. Do NOT run `./gradlew runClient`.

**Files:**
- Modify: `src/main/java/com/goodrunetracker/adapter/runelite/SessionsTab.java`

- [ ] **Step 1: Add the row in sessionSummaryCard**

In `SessionsTab.sessionSummaryCard(...)`, find the Avg XP / trip row:

```java
        grid.add(Styles.keyLabel("Avg XP / trip"));
        JLabel avgXp = Styles.valueLabel(Styles.XP);
        avgXp.setText(GpFormat.format(s.avgXpPerTrip));
        grid.add(avgXp);
        Styles.capHeight(grid);
```

and insert the Avg kills / trip row before `Styles.capHeight(grid);`:

```java
        grid.add(Styles.keyLabel("Avg XP / trip"));
        JLabel avgXp = Styles.valueLabel(Styles.XP);
        avgXp.setText(GpFormat.format(s.avgXpPerTrip));
        grid.add(avgXp);
        grid.add(Styles.keyLabel("Avg kills / trip"));
        JLabel avgKills = Styles.valueLabel(Styles.TEXT);
        avgKills.setText(String.format(java.util.Locale.US, "%.1f", s.avgKillsPerTrip));
        grid.add(avgKills);
        Styles.capHeight(grid);
```

- [ ] **Step 2: Compile + run the full suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/runelite/SessionsTab.java
git commit -m "feat: Sessions summary card Avg kills/trip row"
```

---

## Task 4: Stats — "Kill averages" 3-column section

Add a per-NPC kill averages table (NPC · /trip · /hr, with a Total) to the Stats category detail, after the "XP averages" card. Swing glue — no unit test, must compile and `./gradlew test` stay green.

**Files:**
- Modify: `src/main/java/com/goodrunetracker/adapter/runelite/StatsTab.java`

- [ ] **Step 1: Render the Kill averages section in renderDetail**

In `StatsTab.renderDetail(...)`, find the end of the XP averages section followed by the revalidate:

```java
        detailBody.add(xpCard);

        detailBody.revalidate();
```

and insert the Kill averages section between `detailBody.add(xpCard);` and `detailBody.revalidate();`:

```java
        detailBody.add(xpCard);

        detailBody.add(Styles.sectionHeader("Kill averages"));
        JPanel killCard = Styles.card();
        if (d.killAverages.isEmpty()) {
            JLabel none = Styles.keyLabel("None");
            none.setAlignmentX(Component.LEFT_ALIGNMENT);
            killCard.add(none);
        } else {
            JPanel killGrid = new JPanel(new GridLayout(0, 3, 6, 3));
            killGrid.setBackground(Styles.CARD);
            killGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
            killGrid.add(Styles.keyLabel("NPC"));
            killGrid.add(rightValue("/trip", Styles.SUBTEXT));
            killGrid.add(rightValue("/hr", Styles.SUBTEXT));
            double totalTrip = 0;
            double totalHr = 0;
            for (SessionHistory.NpcKillAverage k : d.killAverages) {
                totalTrip += k.avgPerTrip;
                totalHr += k.perHour;
                killGrid.add(Styles.keyLabel(k.npc));
                killGrid.add(rightValue(String.format(Locale.US, "%.1f", k.avgPerTrip), Styles.TEXT));
                killGrid.add(rightValue(String.format(Locale.US, "%.1f", k.perHour), Styles.TEXT));
            }
            killGrid.add(boldLabel("Total", Styles.TEXT, SwingConstants.LEADING));
            killGrid.add(boldLabel(String.format(Locale.US, "%.1f", totalTrip), Styles.TEXT, SwingConstants.RIGHT));
            killGrid.add(boldLabel(String.format(Locale.US, "%.1f", totalHr), Styles.TEXT, SwingConstants.RIGHT));
            Styles.capHeight(killGrid);
            killCard.add(killGrid);
        }
        detailBody.add(killCard);

        detailBody.revalidate();
```

(`GridLayout`, `Color`, `Locale`, `SwingConstants`, `Component`, `JLabel`, `JPanel`, `Styles`, `rightValue`, `boldLabel` are all already present in `StatsTab`.)

- [ ] **Step 2: Compile + run the full suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Launch the dev client and verify in-client**

Run: `./gradlew runClient`

Checklist (logged in, with a stored session that has kills of more than one NPC type):
1. **Stats → a category:** the detail shows a **"Kill averages"** table after "XP averages" — a header row (NPC · /trip · /hr), one row per NPC most-killed-first with avg/trip and /hr (1 decimal), and a bold **Total**. "None" if the category has no kills.
2. **Sessions → expand a session:** the summary card now shows an **"Avg kills / trip"** row beneath "Avg XP / trip".

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/runelite/StatsTab.java
git commit -m "feat: Stats category Kill-averages table (per-NPC avg/trip + /hr)"
```

---

## Final verification

- [ ] `./gradlew test` — all green (including the two new `SessionHistoryTest` cases).
- [ ] Re-read the spec and confirm each requirement maps to a task: per-session avg kills (T1), per-NPC category kill averages (T2), Sessions summary row (T3), Stats kill table (T4).
- [ ] Dispatch a final code review, then use **superpowers:finishing-a-development-branch** to open the PR.
