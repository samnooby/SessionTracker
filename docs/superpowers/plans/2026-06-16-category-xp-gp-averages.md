# Per-Category XP Averages & Per-Session Averages — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add per-skill XP averages (avg/trip + /hr) to the Stats category detail, and per-session averages (GP/hr + XP/hr headline, avg net/trip + avg XP/trip) to the expanded Sessions view.

**Architecture:** All aggregation lives in `SessionHistory` (the read API) — no core changes. `SessionSummary` gains three derived averages; `CategoryDetail` gains a per-skill `SkillXpAverage` list computed in the existing trip-iterating pass. The Swing tabs render with existing `Styles` primitives.

**Tech Stack:** Java 11, JUnit 4, Swing. Build/test: `./gradlew test`.

---

## File structure

- Modify: `SessionHistory.java` — `SessionSummary` new fields (+ populate); new `SkillXpAverage` carrier; `CategoryDetail.xpAverages` (+ populate).
- Modify: `SessionsTab.java` — session summary card in the expanded-session render.
- Modify: `StatsTab.java` — "XP averages" 3-column section in the category detail.
- Tests: `SessionHistoryTest.java`.

---

## Task 1: Per-session averages on SessionSummary

Add `xpPerHour`, `avgNetProfitPerTrip`, `avgXpPerTrip` to `SessionSummary`, computed in `sessionsNewestFirst()`.

**Files:**
- Modify: `src/main/java/com/goodrunetracker/adapter/SessionHistory.java`
- Test: `src/test/java/com/goodrunetracker/adapter/SessionHistoryTest.java`

- [ ] **Step 1: Write the failing test**

Add to `SessionHistoryTest.java`:

```java
    @Test
    public void sessionSummaryExposesPerSessionAverages() throws Exception {
        Path root = Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root);
        ItemKey coins = ItemKey.item(560);
        Map<ItemKey, Long> price = new HashMap<>();
        price.put(coins, 1L);
        Map<String, Long> xp = new HashMap<>();
        xp.put("Attack", 400L);
        Trip t1 = new Trip("t1", 0, 1_800_000L, false, new HashMap<>(), new HashMap<>(),
                qty(coins, 100), new HashMap<>(), new HashMap<>(), xp);
        Trip t2 = new Trip("t2", 1_800_000L, 3_600_000L, false, new HashMap<>(), new HashMap<>(),
                qty(coins, 100), new HashMap<>(), new HashMap<>(), xp);
        save(store, "acct", "s", "Vorkath", "", 0, 3_600_000L,
                Arrays.asList(SessionMapper.toStored(t1, price), SessionMapper.toStored(t2, price)));

        SessionHistory history = new SessionHistory(store, "acct", names);
        SessionHistory.SessionSummary sum = history.sessionsNewestFirst().get(0);

        assertEquals(2, sum.tripCount);
        assertEquals(200L, sum.netProfit);
        assertEquals(100L, sum.avgNetProfitPerTrip); // 200 / 2
        assertEquals(800L, sum.xpTotal);             // 400 + 400
        assertEquals(400L, sum.avgXpPerTrip);        // 800 / 2
        assertEquals(800L, sum.xpPerHour);           // 800 xp over 1h wall-clock
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew test --tests 'com.goodrunetracker.adapter.SessionHistoryTest'`
Expected: FAIL — `SessionSummary` has no `avgNetProfitPerTrip` / `avgXpPerTrip` / `xpPerHour`.

- [ ] **Step 3: Add the three fields to SessionSummary**

In `SessionHistory.java`, update the `SessionSummary` nested class — add the three fields as the LAST fields (after `startMillis`) and LAST constructor params:

```java
    public static final class SessionSummary {
        public final String sessionId;
        public final String name;
        public final String category;
        public final int tripCount;
        public final long netProfit;
        public final long gpPerHour;
        public final long xpTotal;
        public final long wallClockMillis;
        public final long startMillis;
        public final long xpPerHour;
        public final long avgNetProfitPerTrip;
        public final long avgXpPerTrip;

        public SessionSummary(String sessionId, String name, String category, int tripCount,
                              long netProfit, long gpPerHour, long xpTotal, long wallClockMillis,
                              long startMillis, long xpPerHour, long avgNetProfitPerTrip,
                              long avgXpPerTrip) {
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
        }
    }
```

- [ ] **Step 4: Populate them in sessionsNewestFirst()**

Replace the body of the `for` loop in `sessionsNewestFirst()` so it computes the averages and passes them:

```java
        for (StoredSession s : stored) {
            Session session = SessionMapper.toSession(s);
            Function<Trip, ItemValuer> fn = SessionMapper.valuerFor(s);
            int tripCount = s.trips.size();
            long net = session.totalNetProfit(fn);
            long xpTotal = session.totalXp();
            long avgNet = tripCount == 0 ? 0 : net / tripCount;
            long avgXp = tripCount == 0 ? 0 : xpTotal / tripCount;
            out.add(new SessionSummary(s.id, s.name, s.category, tripCount,
                    net, session.gpPerHour(fn), xpTotal, session.wallClockMillis(), s.startMillis,
                    session.xpPerHour(), avgNet, avgXp));
        }
```

(`Session.xpPerHour()` and `gpPerHour(Function)` already exist on the core `Session`.)

- [ ] **Step 5: Run the test to verify it passes + the full suite**

Run: `./gradlew test --tests 'com.goodrunetracker.adapter.SessionHistoryTest'` then `./gradlew test`
Expected: PASS (new + existing).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/SessionHistory.java \
        src/test/java/com/goodrunetracker/adapter/SessionHistoryTest.java
git commit -m "feat: per-session averages (xp/hr, avg net/trip, avg xp/trip) on SessionSummary"
```

---

## Task 2: Per-skill XP averages on CategoryDetail

Add a `SkillXpAverage` carrier and `CategoryDetail.xpAverages` (avg XP/trip + XP/hr per skill, alphabetical), computed in `categoryDetail()`.

**Files:**
- Modify: `src/main/java/com/goodrunetracker/adapter/SessionHistory.java`
- Test: `src/test/java/com/goodrunetracker/adapter/SessionHistoryTest.java`

- [ ] **Step 1: Write the failing test**

Add to `SessionHistoryTest.java`:

```java
    @Test
    public void categoryDetailListsPerSkillXpAverages() throws Exception {
        Path root = Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root);
        Map<String, Long> xpA = new HashMap<>();
        xpA.put("Attack", 200L);
        xpA.put("Ranged", 100L);
        Map<String, Long> xpB = new HashMap<>();
        xpB.put("Attack", 200L);
        Trip t1 = new Trip("t1", 0, 1_800_000L, false, new HashMap<>(), new HashMap<>(),
                new HashMap<>(), new HashMap<>(), new HashMap<>(), xpA);
        Trip t2 = new Trip("t2", 1_800_000L, 3_600_000L, false, new HashMap<>(), new HashMap<>(),
                new HashMap<>(), new HashMap<>(), new HashMap<>(), xpB);
        save(store, "acct", "s", "Vorkath", "", 0, 3_600_000L,
                Arrays.asList(SessionMapper.toStored(t1, new HashMap<>()),
                        SessionMapper.toStored(t2, new HashMap<>())));

        SessionHistory history = new SessionHistory(store, "acct", names);
        SessionHistory.CategoryDetail d = history.categoryDetail("Vorkath");

        assertEquals(2, d.xpAverages.size());
        // Attack: total 400 over 2 trips -> 200/trip; over 1h wall-clock -> 400/hr
        assertEquals("Attack", d.xpAverages.get(0).skill);
        assertEquals(200L, d.xpAverages.get(0).avgXpPerTrip);
        assertEquals(400L, d.xpAverages.get(0).xpPerHour);
        // Ranged: total 100 over 2 trips -> 50/trip; over 1h -> 100/hr
        assertEquals("Ranged", d.xpAverages.get(1).skill);
        assertEquals(50L, d.xpAverages.get(1).avgXpPerTrip);
        assertEquals(100L, d.xpAverages.get(1).xpPerHour);
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew test --tests 'com.goodrunetracker.adapter.SessionHistoryTest'`
Expected: FAIL — `SkillXpAverage` / `CategoryDetail.xpAverages` do not exist.

- [ ] **Step 3: Add a MILLIS_PER_HOUR constant and the SkillXpAverage carrier**

In `SessionHistory.java`, add `import java.util.TreeMap;` (if not already imported). Add a constant near the top of the class (after the fields):

```java
    private static final long MILLIS_PER_HOUR = 3_600_000L;
```

Add the carrier alongside the other nested carriers:

```java
    public static final class SkillXpAverage {
        public final String skill;
        public final long avgXpPerTrip;
        public final long xpPerHour;

        public SkillXpAverage(String skill, long avgXpPerTrip, long xpPerHour) {
            this.skill = skill;
            this.avgXpPerTrip = avgXpPerTrip;
            this.xpPerHour = xpPerHour;
        }
    }
```

- [ ] **Step 4: Add the field to CategoryDetail and populate it**

Add `xpAverages` as the LAST field and LAST constructor param of `CategoryDetail`:

```java
    public static final class CategoryDetail {
        public final long gpPerHour;
        public final long xpPerHour;
        public final long avgNetProfitPerTrip;
        public final long avgMissedPerTrip;
        public final long avgTripDurationMillis;
        public final double avgKillsPerTrip;
        public final List<SupplyAverage> supplies;
        public final long avgTotalSuppliesGpPerTrip;
        public final List<SkillXpAverage> xpAverages;

        public CategoryDetail(long gpPerHour, long xpPerHour, long avgNetProfitPerTrip,
                              long avgMissedPerTrip, long avgTripDurationMillis, double avgKillsPerTrip,
                              List<SupplyAverage> supplies, long avgTotalSuppliesGpPerTrip,
                              List<SkillXpAverage> xpAverages) {
            this.gpPerHour = gpPerHour;
            this.xpPerHour = xpPerHour;
            this.avgNetProfitPerTrip = avgNetProfitPerTrip;
            this.avgMissedPerTrip = avgMissedPerTrip;
            this.avgTripDurationMillis = avgTripDurationMillis;
            this.avgKillsPerTrip = avgKillsPerTrip;
            this.supplies = supplies;
            this.avgTotalSuppliesGpPerTrip = avgTotalSuppliesGpPerTrip;
            this.xpAverages = xpAverages;
        }
    }
```

In `categoryDetail(category)`, compute `xpAverages` before the `return`. Add this block just before the `return new CategoryDetail(...)` line:

```java
        Map<String, Long> skillTotalXp = new TreeMap<>(); // TreeMap -> alphabetical
        long totalWallClock = 0;
        for (Session s : sessions) {
            totalWallClock += s.wallClockMillis();
            for (Trip t : s.trips()) {
                for (Map.Entry<String, Long> e : t.xpGained().entrySet()) {
                    skillTotalXp.merge(e.getKey(), e.getValue(), Long::sum);
                }
            }
        }
        List<SkillXpAverage> xpAverages = new ArrayList<>();
        for (Map.Entry<String, Long> e : skillTotalXp.entrySet()) {
            long avgTrip = tripCount == 0 ? 0 : e.getValue() / tripCount;
            long perHour = totalWallClock <= 0 ? 0 : e.getValue() * MILLIS_PER_HOUR / totalWallClock;
            xpAverages.add(new SkillXpAverage(e.getKey(), avgTrip, perHour));
        }
```

Then update the `return` to pass it as the new final arg:

```java
        return new CategoryDetail(cs.gpPerHour(), cs.xpPerHour(), cs.avgNetProfitPerTrip(),
                cs.avgMissedPerTrip(), cs.avgTripDurationMillis(), cs.avgKillsPerTrip(),
                supplies, avgTotalSupplies, xpAverages);
```

- [ ] **Step 5: Run the test to verify it passes + the full suite**

Run: `./gradlew test --tests 'com.goodrunetracker.adapter.SessionHistoryTest'` then `./gradlew test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/SessionHistory.java \
        src/test/java/com/goodrunetracker/adapter/SessionHistoryTest.java
git commit -m "feat: per-skill XP averages (avg/trip + /hr) on CategoryDetail"
```

---

## Task 3: Sessions — per-session summary card

When a session is expanded, render a summary card (GP/hr + XP/hr tiles, then Avg net/trip + Avg XP/trip) above the trip rows. Swing glue — no unit test, must compile and `./gradlew test` stay green. Do NOT run `./gradlew runClient`.

**Files:**
- Modify: `src/main/java/com/goodrunetracker/adapter/runelite/SessionsTab.java`

- [ ] **Step 1: Insert the summary card in renderList()**

In `SessionsTab.java`, in `renderList()`, find the expanded-session branch:

```java
                } else if (s.sessionId.equals(expandedSessionId)) {
                    List<SessionHistory.TripSummary> trips = history.tripsFor(s.sessionId);
                    for (int i = 0; i < trips.size(); i++) {
                        listBody.add(Box.createVerticalStrut(2));
                        listBody.add(tripRow(s.sessionId, trips.get(i), i + 1));
                    }
                }
```

and change it to add the summary card before the trip rows:

```java
                } else if (s.sessionId.equals(expandedSessionId)) {
                    listBody.add(Box.createVerticalStrut(2));
                    listBody.add(sessionSummaryCard(s));
                    List<SessionHistory.TripSummary> trips = history.tripsFor(s.sessionId);
                    for (int i = 0; i < trips.size(); i++) {
                        listBody.add(Box.createVerticalStrut(2));
                        listBody.add(tripRow(s.sessionId, trips.get(i), i + 1));
                    }
                }
```

- [ ] **Step 2: Add the sessionSummaryCard method**

Add this method to `SessionsTab.java` (e.g. next to `sessionRow`). It reuses `Styles.tile`, the existing `signedValue` helper, and `Styles` primitives:

```java
    private JPanel sessionSummaryCard(SessionHistory.SessionSummary s) {
        JPanel card = Styles.card();

        JLabel gp = new JLabel(GpFormat.format(s.gpPerHour));
        JLabel xp = new JLabel(GpFormat.format(s.xpPerHour));
        JPanel tiles = new JPanel(new GridLayout(1, 2, 6, 0));
        tiles.setBackground(Styles.CARD);
        tiles.setAlignmentX(Component.LEFT_ALIGNMENT);
        tiles.add(Styles.tile(gp, "GP/hr", s.gpPerHour < 0 ? Styles.NEG : Styles.GP));
        tiles.add(Styles.tile(xp, "XP/hr", Styles.XP));
        Styles.capHeight(tiles);
        card.add(tiles);
        card.add(Box.createVerticalStrut(6));

        JPanel grid = new JPanel(new GridLayout(0, 2, 0, 3));
        grid.setBackground(Styles.CARD);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        grid.add(Styles.keyLabel("Avg net / trip"));
        grid.add(signedValue(s.avgNetProfitPerTrip));
        grid.add(Styles.keyLabel("Avg XP / trip"));
        JLabel avgXp = Styles.valueLabel(Styles.XP);
        avgXp.setText(GpFormat.format(s.avgXpPerTrip));
        grid.add(avgXp);
        Styles.capHeight(grid);
        card.add(grid);

        return card;
    }
```

(`GridLayout`, `Box`, `Component`, `JLabel`, `JPanel`, `GpFormat`, `Styles`, and the `signedValue` helper are all already present in `SessionsTab`. `Styles.tile(JLabel, String, Color)` exists.)

- [ ] **Step 3: Compile + run the full suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/runelite/SessionsTab.java
git commit -m "feat: Sessions expanded session summary card (GP/hr + XP/hr tiles, avg net/xp per trip)"
```

---

## Task 4: Stats — "XP averages" 3-column section

Add a per-skill XP averages table (Skill · /trip · /hr, with a Total) to the Stats category detail, after "Avg supplies / trip". Swing glue — no unit test, must compile and `./gradlew test` stay green.

**Files:**
- Modify: `src/main/java/com/goodrunetracker/adapter/runelite/StatsTab.java`

- [ ] **Step 1: Add a small right-aligned-value helper**

In `StatsTab.java`, add this private helper next to the existing `boldLabel`:

```java
    private static JLabel rightValue(String text, Color color) {
        JLabel l = Styles.valueLabel(color);
        l.setText(text);
        return l;
    }
```

(`Styles.valueLabel(Color)` returns a right-aligned label in the given color.)

- [ ] **Step 2: Render the XP averages section in renderDetail**

In `StatsTab.renderDetail(...)`, find the end of the supplies section:

```java
        Styles.capHeight(sup);
        supCard.add(sup);
        detailBody.add(supCard);

        detailBody.revalidate();
```

and insert the XP averages section between `detailBody.add(supCard);` and `detailBody.revalidate();`:

```java
        Styles.capHeight(sup);
        supCard.add(sup);
        detailBody.add(supCard);

        detailBody.add(Styles.sectionHeader("XP averages"));
        JPanel xpCard = Styles.card();
        if (d.xpAverages.isEmpty()) {
            JLabel none = Styles.keyLabel("None");
            none.setAlignmentX(Component.LEFT_ALIGNMENT);
            xpCard.add(none);
        } else {
            JPanel xpGrid = new JPanel(new GridLayout(0, 3, 6, 3));
            xpGrid.setBackground(Styles.CARD);
            xpGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
            xpGrid.add(Styles.keyLabel("Skill"));
            xpGrid.add(rightValue("/trip", Styles.SUBTEXT));
            xpGrid.add(rightValue("/hr", Styles.SUBTEXT));
            long totalAvgTrip = 0;
            for (SessionHistory.SkillXpAverage a : d.xpAverages) {
                totalAvgTrip += a.avgXpPerTrip;
                xpGrid.add(Styles.keyLabel(a.skill));
                xpGrid.add(rightValue(GpFormat.format(a.avgXpPerTrip), Styles.XP));
                xpGrid.add(rightValue(GpFormat.format(a.xpPerHour), Styles.XP));
            }
            xpGrid.add(boldLabel("Total", Styles.TEXT, SwingConstants.LEADING));
            xpGrid.add(boldLabel(GpFormat.format(totalAvgTrip), Styles.XP, SwingConstants.RIGHT));
            xpGrid.add(boldLabel(GpFormat.format(d.xpPerHour), Styles.XP, SwingConstants.RIGHT));
            Styles.capHeight(xpGrid);
            xpCard.add(xpGrid);
        }
        detailBody.add(xpCard);

        detailBody.revalidate();
```

(`GridLayout`, `Color`, `SwingConstants`, `JLabel`, `JPanel`, `Component`, `GpFormat`, `Styles`, `boldLabel` are all already present in `StatsTab`. `Styles.SUBTEXT` is a defined color.)

- [ ] **Step 3: Compile + run the full suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Launch the dev client and verify in-client**

Run: `./gradlew runClient`

Checklist (logged in, with at least one stored session that gained XP):
1. **Stats → a category:** the detail shows an **"XP averages"** table after "Avg supplies / trip" — a header row (Skill · /trip · /hr), one row per skill (alphabetical) with avg XP/trip and XP/hr, and a bold **Total** (total avg XP/trip + category XP/hr). "None" if the category gained no XP.
2. **Sessions → expand a session:** a summary card appears above the trip rows — **GP/hr** + **XP/hr** tiles, then **Avg net / trip** and **Avg XP / trip**.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/runelite/StatsTab.java
git commit -m "feat: Stats category XP-averages table (per-skill avg/trip + /hr)"
```

---

## Final verification

- [ ] `./gradlew test` — all green (including the two new `SessionHistoryTest` cases).
- [ ] Re-read the spec and confirm each requirement maps to a task: per-session averages (T1), per-skill category XP averages (T2), Sessions summary card (T3), Stats XP table (T4).
- [ ] Dispatch a final code review, then use **superpowers:finishing-a-development-branch** to open the PR.
