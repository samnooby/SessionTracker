# Stats Loot Averages Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add four per-item average tables to the Stats category detail — Avg picked up, Avg supplies, Avg left on ground, Gross avg drops — in that order, right after "Per-trip averages".

**Architecture:** A pure data-layer change in `SessionHistory.categoryDetail` (generalize the existing `SupplyAverage` carrier to a reusable `ItemAverage`, extract one shared per-item-average helper, and compute picked/missed/dropped alongside the existing supplies) plus a UI change in `StatsTab.renderDetail` (one shared table-rendering helper used four times). The core `Trip` already exposes `pickedUp()`, `missed()`, `dropped()` — no core changes.

**Tech Stack:** Java 11, Gradle, JUnit 4, RuneLite Swing (`PluginPanel`), Guice.

---

## Background facts (verified against the current code)

- `Trip` (core) exposes `Map<ItemKey,Integer>` accessors: `pickedUp()`, `missed()` (left on ground), `dropped()` (gross), `suppliesUsed()`. `dropped` is an independent stored field (NOT derived from picked+missed) and round-trips through `SessionMapper` (`encode`/`decode`).
- `SessionHistory.categoryDetail` currently builds the supplies averages by iterating trips, summing per-`ItemKey` gp via `fn.apply(t).value(key, qty)`, and dividing the per-item total and `cs.avgSuppliesPerTrip()` (= `sum(qty)/tripCount`) by `tripCount`. The shared helper in this plan computes qty the same way (`sum(qty)/tripCount`), so the refactor is behavior-preserving and the existing supplies test stays green.
- `CategoryStats.avgMissedPerTrip()` = `sum(t.missedValue(v))/tripCount` over the same `fn`, which equals the missed table's single-division total — so the "Avg left on ground" table reuses `d.avgMissedPerTrip` as its Total (no new missed-total field needed).
- Carrier `SupplyAverage {String label; double avgQtyPerTrip; long avgGpPerTrip}` is referenced by name only in `SessionHistory` (the class + the `supplies` field type + the build loop) and in `StatsTab` (`for (SessionHistory.SupplyAverage s : d.supplies)`). Tests access elements via `d.supplies.get(0).label` etc. and do NOT name the type — so renaming the type does not touch any test.
- Test helpers in `SessionHistoryTest`: `qty(ItemKey,int)`, `trip(...)` (passes EMPTY dropped), `save(...)`. New tests that need a non-empty `dropped` construct `Trip` directly with the full 10-arg constructor (as `categoryDetailListsPerNpcKillAveragesMostKilledFirst` already does) and store via `SessionMapper.toStored(t, prices)`.
- Colors on `Styles`: `GP` (green), `NEG` (red), `MISSED` (amber), `TEXT` (neutral white) — all already used in `StatsTab`.

---

## File Structure

- **Modify** `src/main/java/com/goodrunetracker/adapter/SessionHistory.java`
  - Rename carrier `SupplyAverage` → `ItemAverage` (same fields); `supplies` field type becomes `List<ItemAverage>`.
  - Add a private holder `ItemAverages {List<ItemAverage> items; long avgTotalGpPerTrip}` and a private instance helper `itemAverages(sessions, fn, tripCount, extractor)`.
  - Refactor the supplies computation onto the helper; add picked/missed/dropped via the helper.
  - `CategoryDetail` gains `pickedAverages`, `avgPickedGpPerTrip`, `missedAverages`, `droppedAverages`, `avgDroppedGpPerTrip` (missed Total reuses existing `avgMissedPerTrip`).
- **Modify** `src/main/java/com/goodrunetracker/adapter/runelite/StatsTab.java`
  - Rename the supplies loop variable type to `ItemAverage` (Task 1, to keep the build green).
  - Replace the inline supplies block with four `addItemTable(...)` calls in order (Task 2).
- **Modify** `src/test/java/com/goodrunetracker/adapter/SessionHistoryTest.java`
  - Add one test covering picked/missed/dropped per-item averages + totals.

---

### Task 1: Data layer — `ItemAverage` carrier, shared helper, and picked/missed/dropped on `CategoryDetail`

**Files:**
- Modify: `src/main/java/com/goodrunetracker/adapter/SessionHistory.java`
- Modify: `src/main/java/com/goodrunetracker/adapter/runelite/StatsTab.java` (rename loop var type only)
- Test: `src/test/java/com/goodrunetracker/adapter/SessionHistoryTest.java`

- [ ] **Step 1: Write the failing test**

Add this test method to `SessionHistoryTest` (after `categoryDetailAveragesSuppliesPerTripWithTotal`):

```java
    @Test
    public void categoryDetailAveragesPickedDroppedAndLeftOnGroundPerItem() throws Exception {
        Path root = Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root);
        ItemKey coins = ItemKey.item(560);
        ItemKey scale = ItemKey.item(12934);
        Map<ItemKey, Long> price = new HashMap<>();
        price.put(coins, 1L);
        price.put(scale, 10L);

        Map<ItemKey, Integer> dropped1 = new HashMap<>();
        dropped1.put(coins, 100);
        dropped1.put(scale, 4);
        Map<ItemKey, Integer> picked1 = new HashMap<>();
        picked1.put(coins, 100);
        picked1.put(scale, 1);
        Map<ItemKey, Integer> missed1 = new HashMap<>();
        missed1.put(scale, 3);

        Map<ItemKey, Integer> dropped2 = new HashMap<>();
        dropped2.put(coins, 200);
        Map<ItemKey, Integer> picked2 = new HashMap<>();
        picked2.put(coins, 200);

        Trip t1 = new Trip("t1", 0, 3_600_000L, false, new HashMap<>(), dropped1,
                picked1, missed1, new HashMap<>(), new HashMap<>());
        Trip t2 = new Trip("t2", 3_600_000L, 7_200_000L, false, new HashMap<>(), dropped2,
                picked2, new HashMap<>(), new HashMap<>(), new HashMap<>());
        save(store, "acct", "s", "Vorkath", "", 0, 7_200_000L,
                Arrays.asList(SessionMapper.toStored(t1, price), SessionMapper.toStored(t2, price)));

        SessionHistory history = new SessionHistory(store, "acct", names);
        SessionHistory.CategoryDetail d = history.categoryDetail("Vorkath");

        // Picked: coins 300qty/300gp, scale 1qty/10gp -> /2 trips; sorted gp desc (coins first)
        assertEquals(2, d.pickedAverages.size());
        assertEquals("Item 560", d.pickedAverages.get(0).label);
        assertEquals(150.0, d.pickedAverages.get(0).avgQtyPerTrip, 0.0001);
        assertEquals(150L, d.pickedAverages.get(0).avgGpPerTrip);
        assertEquals("Item 12934", d.pickedAverages.get(1).label);
        assertEquals(0.5, d.pickedAverages.get(1).avgQtyPerTrip, 0.0001);
        assertEquals(5L, d.pickedAverages.get(1).avgGpPerTrip);
        assertEquals(155L, d.avgPickedGpPerTrip);

        // Left on ground (missed): scale 3qty/30gp total -> /2 trips
        assertEquals(1, d.missedAverages.size());
        assertEquals("Item 12934", d.missedAverages.get(0).label);
        assertEquals(1.5, d.missedAverages.get(0).avgQtyPerTrip, 0.0001);
        assertEquals(15L, d.missedAverages.get(0).avgGpPerTrip);
        assertEquals(15L, d.avgMissedPerTrip);

        // Gross dropped: coins 300qty/300gp, scale 4qty/40gp -> /2 trips; coins first
        assertEquals(2, d.droppedAverages.size());
        assertEquals("Item 560", d.droppedAverages.get(0).label);
        assertEquals(150.0, d.droppedAverages.get(0).avgQtyPerTrip, 0.0001);
        assertEquals(150L, d.droppedAverages.get(0).avgGpPerTrip);
        assertEquals("Item 12934", d.droppedAverages.get(1).label);
        assertEquals(2.0, d.droppedAverages.get(1).avgQtyPerTrip, 0.0001);
        assertEquals(20L, d.droppedAverages.get(1).avgGpPerTrip);
        assertEquals(170L, d.avgDroppedGpPerTrip);
    }
```

(`Trip` and `SessionMapper` are already imported in this test file.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.goodrunetracker.adapter.SessionHistoryTest"`
Expected: COMPILE FAILURE — `CategoryDetail` has no `pickedAverages`/`avgPickedGpPerTrip`/`missedAverages`/`droppedAverages`/`avgDroppedGpPerTrip`.

- [ ] **Step 3: Rename `SupplyAverage` → `ItemAverage` and add the holder + helper**

In `SessionHistory.java`, rename the carrier class (keep fields identical):

```java
    public static final class ItemAverage {
        public final String label;
        public final double avgQtyPerTrip;
        public final long avgGpPerTrip;

        public ItemAverage(String label, double avgQtyPerTrip, long avgGpPerTrip) {
            this.label = label;
            this.avgQtyPerTrip = avgQtyPerTrip;
            this.avgGpPerTrip = avgGpPerTrip;
        }
    }
```

Add this private holder + helper (place near the other private methods, e.g. just above `private String label(ItemKey key)`):

```java
    private static final class ItemAverages {
        final List<ItemAverage> items;
        final long avgTotalGpPerTrip;

        ItemAverages(List<ItemAverage> items, long avgTotalGpPerTrip) {
            this.items = items;
            this.avgTotalGpPerTrip = avgTotalGpPerTrip;
        }
    }

    private ItemAverages itemAverages(List<Session> sessions, Function<Trip, ItemValuer> fn,
                                      int tripCount, Function<Trip, Map<ItemKey, Integer>> extractor) {
        Map<ItemKey, Long> totalQty = new HashMap<>();
        Map<ItemKey, Long> totalGp = new HashMap<>();
        for (Session s : sessions) {
            for (Trip t : s.trips()) {
                ItemValuer v = fn.apply(t);
                for (Map.Entry<ItemKey, Integer> e : extractor.apply(t).entrySet()) {
                    totalQty.merge(e.getKey(), e.getValue().longValue(), Long::sum);
                    totalGp.merge(e.getKey(), v.value(e.getKey(), e.getValue()), Long::sum);
                }
            }
        }
        List<ItemAverage> items = new ArrayList<>();
        long sumGp = 0;
        for (Map.Entry<ItemKey, Long> e : totalGp.entrySet()) {
            sumGp += e.getValue();
            double avgQty = tripCount == 0 ? 0.0 : (double) totalQty.get(e.getKey()) / tripCount;
            long avgGp = tripCount == 0 ? 0 : e.getValue() / tripCount;
            items.add(new ItemAverage(label(e.getKey()), avgQty, avgGp));
        }
        items.sort(Comparator.comparingLong((ItemAverage a) -> a.avgGpPerTrip).reversed());
        long avgTotal = tripCount == 0 ? 0 : sumGp / tripCount;
        return new ItemAverages(items, avgTotal);
    }
```

- [ ] **Step 4: Refactor `categoryDetail` to use the helper for all four loot kinds**

In `categoryDetail`, delete the manual supplies block (the `avgQtyByKey`, `totalGp` loop, the `List<SupplyAverage> supplies` loop, the `supplies.sort(...)`, and `avgTotalSupplies`) and replace with:

```java
        ItemAverages supplyAvg = itemAverages(sessions, fn, tripCount, Trip::suppliesUsed);
        ItemAverages pickedAvg = itemAverages(sessions, fn, tripCount, Trip::pickedUp);
        ItemAverages missedAvg = itemAverages(sessions, fn, tripCount, Trip::missed);
        ItemAverages droppedAvg = itemAverages(sessions, fn, tripCount, Trip::dropped);
```

Also remove the now-unused line `java.util.Map<ItemKey, Double> avgQtyByKey = cs.avgSuppliesPerTrip();`. Leave the XP-averages and kill-averages blocks unchanged. Update the return to pass the new lists/totals (append after `killAverages`):

```java
        return new CategoryDetail(cs.gpPerHour(), cs.xpPerHour(), cs.avgNetProfitPerTrip(),
                cs.avgMissedPerTrip(), cs.avgTripDurationMillis(), cs.avgKillsPerTrip(),
                supplyAvg.items, supplyAvg.avgTotalGpPerTrip, xpAverages, killAverages,
                pickedAvg.items, pickedAvg.avgTotalGpPerTrip,
                missedAvg.items, droppedAvg.items, droppedAvg.avgTotalGpPerTrip);
```

- [ ] **Step 5: Extend `CategoryDetail` with the new fields**

Update the `CategoryDetail` class — change the `supplies` field type to `List<ItemAverage>`, and add the five new fields + constructor params (appended after `killAverages`):

```java
    public static final class CategoryDetail {
        public final long gpPerHour;
        public final long xpPerHour;
        public final long avgNetProfitPerTrip;
        public final long avgMissedPerTrip;
        public final long avgTripDurationMillis;
        public final double avgKillsPerTrip;
        public final List<ItemAverage> supplies;
        public final long avgTotalSuppliesGpPerTrip;
        public final List<SkillXpAverage> xpAverages;
        public final List<NpcKillAverage> killAverages;
        public final List<ItemAverage> pickedAverages;
        public final long avgPickedGpPerTrip;
        public final List<ItemAverage> missedAverages;
        public final List<ItemAverage> droppedAverages;
        public final long avgDroppedGpPerTrip;

        public CategoryDetail(long gpPerHour, long xpPerHour, long avgNetProfitPerTrip,
                              long avgMissedPerTrip, long avgTripDurationMillis, double avgKillsPerTrip,
                              List<ItemAverage> supplies, long avgTotalSuppliesGpPerTrip,
                              List<SkillXpAverage> xpAverages, List<NpcKillAverage> killAverages,
                              List<ItemAverage> pickedAverages, long avgPickedGpPerTrip,
                              List<ItemAverage> missedAverages, List<ItemAverage> droppedAverages,
                              long avgDroppedGpPerTrip) {
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
            this.pickedAverages = pickedAverages;
            this.avgPickedGpPerTrip = avgPickedGpPerTrip;
            this.missedAverages = missedAverages;
            this.droppedAverages = droppedAverages;
            this.avgDroppedGpPerTrip = avgDroppedGpPerTrip;
        }
    }
```

- [ ] **Step 6: Fix the `StatsTab` reference to the renamed type (keep the build green)**

In `StatsTab.renderDetail`, the existing supplies loop names the old type. Update only the type in that loop for now:

```java
        for (SessionHistory.ItemAverage s : d.supplies) {
```

(Task 2 rewrites this block entirely; this one-line rename just keeps Task 1 compiling.)

- [ ] **Step 7: Run the new test + full suite**

Run: `./gradlew test --tests "com.goodrunetracker.adapter.SessionHistoryTest"`
Expected: PASS (new test green, existing `categoryDetailAveragesSuppliesPerTripWithTotal` and kill/xp tests still green).

Then run the whole suite: `./gradlew test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/SessionHistory.java \
        src/main/java/com/goodrunetracker/adapter/runelite/StatsTab.java \
        src/test/java/com/goodrunetracker/adapter/SessionHistoryTest.java
git commit -m "feat: per-item picked/missed/dropped averages on CategoryDetail (shared ItemAverage helper)"
```

---

### Task 2: UI layer — four per-item loot tables in order on the Stats detail

**Files:**
- Modify: `src/main/java/com/goodrunetracker/adapter/runelite/StatsTab.java`

- [ ] **Step 1: Add a shared per-item table helper**

Add this private method to `StatsTab` (next to the other private render helpers, e.g. above `private static JPanel grid()`):

```java
    private void addItemTable(String header, List<SessionHistory.ItemAverage> items,
                              long total, Color valueColor) {
        detailBody.add(Styles.sectionHeader(header));
        JPanel card = Styles.card();
        if (items.isEmpty()) {
            JLabel none = Styles.keyLabel("None");
            none.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(none);
        } else {
            JPanel g = grid();
            for (SessionHistory.ItemAverage a : items) {
                g.add(Styles.keyLabel(a.label + "  " + String.format(Locale.US, "%.1f", a.avgQtyPerTrip)));
                JLabel v = Styles.valueLabel(valueColor);
                v.setText(GpFormat.format(a.avgGpPerTrip));
                g.add(v);
            }
            g.add(boldLabel("Total", Styles.TEXT, SwingConstants.LEADING));
            g.add(boldLabel(GpFormat.format(total), valueColor, SwingConstants.RIGHT));
            Styles.capHeight(g);
            card.add(g);
        }
        detailBody.add(card);
    }
```

- [ ] **Step 2: Replace the inline supplies block with four ordered table calls**

In `renderDetail`, delete the entire existing "Avg supplies / trip" block (from `detailBody.add(Styles.sectionHeader("Avg supplies / trip"));` through its `detailBody.add(supCard);`) and, in its place — immediately after `detailBody.add(avgCard);` — insert:

```java
        addItemTable("Avg picked up / trip", d.pickedAverages, d.avgPickedGpPerTrip, Styles.GP);
        addItemTable("Avg supplies / trip", d.supplies, d.avgTotalSuppliesGpPerTrip, Styles.NEG);
        addItemTable("Avg left on ground / trip", d.missedAverages, d.avgMissedPerTrip, Styles.MISSED);
        addItemTable("Gross avg drops / trip", d.droppedAverages, d.avgDroppedGpPerTrip, Styles.TEXT);
```

Leave the "XP averages" and "Kill averages" blocks exactly as they are, after these four tables.

- [ ] **Step 3: Compile and run the full suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL (no test changes; this verifies the UI module still compiles).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/runelite/StatsTab.java
git commit -m "feat: render picked/supplies/left-on-ground/gross-drops tables on Stats detail"
```

---

## Manual in-client verification (after both tasks)

Drill into a category on the Stats tab. Confirm, in order after "Per-trip averages":
1. **Avg picked up / trip** — green per-item gp + bold Total.
2. **Avg supplies / trip** — red per-item gp + bold Total (unchanged values from before).
3. **Avg left on ground / trip** — amber per-item gp + bold Total (Total matches the "Missed" figure in Per-trip averages).
4. **Gross avg drops / trip** — neutral per-item gp + bold Total.

Then "XP averages" and "Kill averages" below, unchanged. Empty categories show "None" in each table.

## Out of scope

- Sessions-tab / Now-tab loot breakdowns.
- Any change to the net-profit / gp-hr / missed aggregate figures in "Per-trip averages".
