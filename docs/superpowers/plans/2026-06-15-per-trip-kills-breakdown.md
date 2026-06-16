# Per-Trip Kills Breakdown by NPC — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show the per-NPC kill counts on each trip — current trip on the Now tab, previous trips in the Sessions trip-detail view — most-killed first, with a Total.

**Architecture:** The per-NPC kills already live in `Trip.kills()` (`Map<String,Integer>`) and are persisted. A new plain `NpcKills` carrier surfaces them (count-desc) on `TripSnapshot` (live) and `SessionHistory.TripDetail` (stored). The Swing tabs render a text-only card/group reusing existing `Styles` primitives. Directly parallels the per-skill XP breakdown already in `master`.

**Tech Stack:** Java 11, JUnit 4, Swing. Build/test: `./gradlew test`.

---

## File structure

- Create: `src/main/java/com/goodrunetracker/adapter/NpcKills.java` — plain `{npc, count}` carrier + `sortedByCountDesc(Map)`.
- Modify: `TripSnapshot.java` — add `killsByNpc` field + ctor param.
- Modify: `TrackingService.java` — `computeSnapshot` populates `killsByNpc`.
- Modify: `SessionHistory.java` — `TripDetail.killsByNpc` field + populate in `tripDetail`.
- Modify: `NowTab.java` — "Kills" card.
- Modify: `SessionsTab.java` — "Kills" detail group.
- Tests: `NpcKillsTest.java` (new), `TrackingServiceTest.java`, `SessionHistoryTest.java`.

No `Styles` changes are needed — `card`, `sectionHeader`, `keyLabel`, `valueLabel`, `addBoldRow`, `capHeight` all already exist (from the XP feature, now on master).

---

## Task 1: NpcKills value object

A plain, RuneLite-free carrier and a count-descending list factory (ties broken alphabetically).

**Files:**
- Create: `src/main/java/com/goodrunetracker/adapter/NpcKills.java`
- Test: `src/test/java/com/goodrunetracker/adapter/NpcKillsTest.java`

- [ ] **Step 1: Write the failing test**

Create `NpcKillsTest.java`:

```java
package com.goodrunetracker.adapter;

import static org.junit.Assert.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class NpcKillsTest {

    @Test
    public void sortedByCountDescOrdersByCountThenName() {
        Map<String, Integer> kills = new HashMap<>();
        kills.put("Bird", 10);
        kills.put("Goblin", 20);
        kills.put("Cow", 10); // ties with Bird at 10 -> alphabetical: Bird before Cow

        List<NpcKills> list = NpcKills.sortedByCountDesc(kills);

        assertEquals(3, list.size());
        assertEquals("Goblin", list.get(0).npc);
        assertEquals(20, list.get(0).count);
        assertEquals("Bird", list.get(1).npc);
        assertEquals(10, list.get(1).count);
        assertEquals("Cow", list.get(2).npc);
    }

    @Test
    public void sortedByCountDescEmptyMapIsEmptyList() {
        assertTrue(NpcKills.sortedByCountDesc(new HashMap<>()).isEmpty());
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew test --tests 'com.goodrunetracker.adapter.NpcKillsTest'`
Expected: FAIL — `NpcKills` does not exist.

- [ ] **Step 3: Create NpcKills.java**

```java
package com.goodrunetracker.adapter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Per-NPC kill count on a trip: a plain, RuneLite-free carrier ({@code npc} is the NPC name). */
public final class NpcKills {

    public final String npc;
    public final int count;

    public NpcKills(String npc, int count) {
        this.npc = npc;
        this.count = count;
    }

    /** The given per-NPC kill map as a list ordered by count descending, ties broken by name ascending. */
    public static List<NpcKills> sortedByCountDesc(Map<String, Integer> kills) {
        List<NpcKills> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : kills.entrySet()) {
            out.add(new NpcKills(e.getKey(), e.getValue()));
        }
        out.sort(Comparator.comparingInt((NpcKills k) -> k.count).reversed()
                .thenComparing(k -> k.npc));
        return out;
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew test --tests 'com.goodrunetracker.adapter.NpcKillsTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/NpcKills.java \
        src/test/java/com/goodrunetracker/adapter/NpcKillsTest.java
git commit -m "feat: NpcKills carrier + count-descending sortedByCountDesc"
```

---

## Task 2: TripSnapshot.killsByNpc (live current trip)

**Files:**
- Modify: `src/main/java/com/goodrunetracker/adapter/TripSnapshot.java`
- Modify: `src/main/java/com/goodrunetracker/adapter/TrackingService.java`
- Test: `src/test/java/com/goodrunetracker/adapter/TrackingServiceTest.java`

- [ ] **Step 1: Write the failing tests**

Add to `TrackingServiceTest.java`:

```java
    @Test
    public void snapshotKillsByNpcIsEmptyBeforeAnyKill() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"));
        TrackingService service = newService(clock, carried, new FakePanel(), store);
        service.startSession();
        assertTrue(service.currentSnapshot().get().killsByNpc.isEmpty());
    }

    @Test
    public void snapshotListsKillsByNpcMostKilledFirst() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"));
        TrackingService service = newService(clock, carried, new FakePanel(), store);
        service.startSession();
        Map<Integer, Integer> noDrop = new HashMap<>();
        service.onKill("Goblin", noDrop);
        service.onKill("Bird", noDrop);
        service.onKill("Goblin", noDrop);
        service.onKill("Goblin", noDrop);

        TripSnapshot snap = service.currentSnapshot().get();
        assertEquals(2, snap.killsByNpc.size());
        assertEquals("Goblin", snap.killsByNpc.get(0).npc);
        assertEquals(3, snap.killsByNpc.get(0).count);
        assertEquals("Bird", snap.killsByNpc.get(1).npc);
        assertEquals(1, snap.killsByNpc.get(1).count);
        assertEquals(4, snap.kills);
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew test --tests 'com.goodrunetracker.adapter.TrackingServiceTest'`
Expected: FAIL — `TripSnapshot` has no `killsByNpc`.

- [ ] **Step 3: Add the field to TripSnapshot**

In `TripSnapshot.java` (no import needed — `NpcKills` is in the same package), add `killsByNpc` as the LAST field (after `xpBySkill`) and LAST constructor parameter (after `xpBySkill`):

```java
    public final List<SkillXp> xpBySkill;
    public final List<NpcKills> killsByNpc;

    public TripSnapshot(int tripNumber, long durationMillis, int kills, long pickedGp,
                        long groundGp, long suppliesGp, long totalXp, long gpPerHour,
                        List<SkillXp> xpBySkill, List<NpcKills> killsByNpc) {
        this.tripNumber = tripNumber;
        this.durationMillis = durationMillis;
        this.kills = kills;
        this.pickedGp = pickedGp;
        this.groundGp = groundGp;
        this.suppliesGp = suppliesGp;
        this.totalXp = totalXp;
        this.gpPerHour = gpPerHour;
        this.xpBySkill = xpBySkill;
        this.killsByNpc = killsByNpc;
    }
```

(`List` is already imported in TripSnapshot. The `kills` int field is unchanged — it is the total.)

- [ ] **Step 4: Populate it in TrackingService.computeSnapshot()**

Change the final `return new TripSnapshot(...)` to pass the per-NPC list (`NpcKills` is same-package, no import needed):

```java
        int tripNumber = activeSession.trips.size() + 1;
        return new TripSnapshot(tripNumber, duration, trip.totalKills(),
                picked, ground, supplies, trip.totalXp(), gpPerHour,
                SkillXp.sortedFrom(trip.xpGained()), NpcKills.sortedByCountDesc(trip.kills()));
```

- [ ] **Step 5: Run the tests to verify they pass + the full suite**

Run: `./gradlew test --tests 'com.goodrunetracker.adapter.TrackingServiceTest'` then `./gradlew test`
Expected: PASS (new + existing).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/TripSnapshot.java \
        src/main/java/com/goodrunetracker/adapter/TrackingService.java \
        src/test/java/com/goodrunetracker/adapter/TrackingServiceTest.java
git commit -m "feat: per-NPC kills on the live trip snapshot"
```

---

## Task 3: SessionHistory.TripDetail.killsByNpc (stored trips)

**Files:**
- Modify: `src/main/java/com/goodrunetracker/adapter/SessionHistory.java`
- Test: `src/test/java/com/goodrunetracker/adapter/SessionHistoryTest.java`

- [ ] **Step 1: Write the failing test**

Add to `SessionHistoryTest.java`:

```java
    @Test
    public void tripDetailListsKillsByNpcMostKilledFirst() throws Exception {
        Path root = Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root);
        Map<String, Integer> kills = new HashMap<>();
        kills.put("Goblin", 20);
        kills.put("Bird", 10);
        Trip t = new Trip("t1", 0, 60_000, false, kills, new HashMap<>(),
                new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
        StoredTrip st = SessionMapper.toStored(t, new HashMap<>());
        save(store, "acct", "s", "Vorkath", "", 0, 60_000, Arrays.asList(st));

        SessionHistory history = new SessionHistory(store, "acct", names);
        SessionHistory.TripDetail d = history.tripDetail("s", "t1");

        assertEquals(2, d.killsByNpc.size());
        assertEquals("Goblin", d.killsByNpc.get(0).npc);
        assertEquals(20, d.killsByNpc.get(0).count);
        assertEquals("Bird", d.killsByNpc.get(1).npc);
        assertEquals(10, d.killsByNpc.get(1).count);
    }
```

(The `Trip` constructor arg order is `(id, startMillis, endMillis, died, kills, dropped, pickedUp, missed, suppliesUsed, xpGained)` — `kills` is the 5th arg.)

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew test --tests 'com.goodrunetracker.adapter.SessionHistoryTest'`
Expected: FAIL — `TripDetail` has no `killsByNpc`.

- [ ] **Step 3: Add the field to TripDetail and populate it**

In `SessionHistory.java`, update the `TripDetail` nested class — add `killsByNpc` as the LAST field and LAST constructor param (after `xpGained`):

```java
    public static final class TripDetail {
        public final List<ItemLine> pickedUp;
        public final List<ItemLine> leftOnGround;
        public final List<ItemLine> suppliesUsed;
        public final long netProfit;
        public final long missedValue;
        public final List<SkillXp> xpGained;
        public final List<NpcKills> killsByNpc;

        public TripDetail(List<ItemLine> pickedUp, List<ItemLine> leftOnGround,
                          List<ItemLine> suppliesUsed, long netProfit, long missedValue,
                          List<SkillXp> xpGained, List<NpcKills> killsByNpc) {
            this.pickedUp = pickedUp;
            this.leftOnGround = leftOnGround;
            this.suppliesUsed = suppliesUsed;
            this.netProfit = netProfit;
            this.missedValue = missedValue;
            this.xpGained = xpGained;
            this.killsByNpc = killsByNpc;
        }
    }
```

Then update the `return new TripDetail(...)` inside `tripDetail(...)` to pass the list (`NpcKills` is same-package, no import needed):

```java
                return new TripDetail(lines(t.pickedUp(), v), lines(t.missed(), v),
                        lines(t.suppliesUsed(), v), t.netProfit(v), t.missedValue(v),
                        SkillXp.sortedFrom(t.xpGained()), NpcKills.sortedByCountDesc(t.kills()));
```

- [ ] **Step 4: Run it to verify it passes + the full suite**

Run: `./gradlew test --tests 'com.goodrunetracker.adapter.SessionHistoryTest'` then `./gradlew test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/SessionHistory.java \
        src/test/java/com/goodrunetracker/adapter/SessionHistoryTest.java
git commit -m "feat: per-NPC kills on the stored trip detail"
```

---

## Task 4: Now tab "Kills" card

Render the live per-NPC kills card on the Now tab, between the Current trip card and the XP gained card. Swing glue — no unit test, must compile and `./gradlew test` stay green. Do NOT run `./gradlew runClient`.

**Files:**
- Modify: `src/main/java/com/goodrunetracker/adapter/runelite/NowTab.java`

- [ ] **Step 1: Add imports and the dynamic body field**

In `NowTab.java`, add `import com.goodrunetracker.adapter.NpcKills;` (the file already imports `java.util.List`, `GridLayout`, `JLabel`, `JPanel`, `Component`, `GpFormat`, `Styles`). Add a field next to the existing `private final JPanel xpBody = ...;`:

```java
    private final JPanel killsBody = new JPanel(new GridLayout(0, 2, 0, 3));
```

- [ ] **Step 2: Insert the Kills section in the constructor**

Find this block in the constructor:

```java
        body.add(Styles.sectionHeader("Current trip"));
        body.add(currentCard());

        body.add(Styles.sectionHeader("XP gained"));
        body.add(xpCard());
```

and change it to (insert the Kills section between Current trip and XP gained):

```java
        body.add(Styles.sectionHeader("Current trip"));
        body.add(currentCard());

        body.add(Styles.sectionHeader("Kills"));
        body.add(killsCard());

        body.add(Styles.sectionHeader("XP gained"));
        body.add(xpCard());
```

- [ ] **Step 3: Add the killsCard() builder and renderKills() (mirror xpCard/renderXp)**

```java
    private JPanel killsCard() {
        JPanel card = Styles.card();
        killsBody.setBackground(Styles.CARD);
        killsBody.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(killsBody);
        return card;
    }

    private void renderKills(List<NpcKills> kills) {
        killsBody.removeAll();
        if (kills == null || kills.isEmpty()) {
            killsBody.add(Styles.keyLabel("None"));
            killsBody.add(new JLabel(""));
        } else {
            int total = 0;
            for (NpcKills k : kills) {
                total += k.count;
                killsBody.add(Styles.keyLabel(k.npc));
                JLabel v = Styles.valueLabel(Styles.TEXT);
                v.setText(Integer.toString(k.count));
                killsBody.add(v);
            }
            Styles.addBoldRow(killsBody, "Total", Integer.toString(total), Styles.TEXT);
        }
        Styles.capHeight(killsBody);
        killsBody.revalidate();
        killsBody.repaint();
    }
```

- [ ] **Step 4: Call renderKills from renderStats in both branches**

In `renderStats()`, the `if (snap.isPresent())` branch currently calls `renderXp(s.xpBySkill);` — immediately after it add:

```java
            renderKills(s.killsByNpc);
```

In the `else` branch, after `renderXp(null);` add:

```java
            renderKills(null);
```

- [ ] **Step 5: Compile + run the full suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/runelite/NowTab.java
git commit -m "feat: Now tab Kills card (per-NPC, most-killed-first, total)"
```

---

## Task 5: Sessions trip-detail "Kills" group

Render the stored per-NPC kills in the trip-detail view, after the Net/Missed summary and before "Picked up". Swing glue — no unit test, must compile and `./gradlew test` stay green.

**Files:**
- Modify: `src/main/java/com/goodrunetracker/adapter/runelite/SessionsTab.java`

- [ ] **Step 1: Add the import and the group call**

In `SessionsTab.java`, add `import com.goodrunetracker.adapter.NpcKills;`. In `renderDetail(...)`, inside the `if (d != null)` block, find:

```java
            summary.add(grid);
            detailBody.add(summary);

            addGroup("Picked up", d.pickedUp, Styles.GP);
```

and insert the kills group between the summary and "Picked up":

```java
            summary.add(grid);
            detailBody.add(summary);

            addKillsGroup(d.killsByNpc);

            addGroup("Picked up", d.pickedUp, Styles.GP);
```

- [ ] **Step 2: Add the addKillsGroup method**

Add this method to `SessionsTab.java` (next to `addXpGroup`):

```java
    private void addKillsGroup(List<NpcKills> kills) {
        detailBody.add(Styles.sectionHeader("Kills"));
        JPanel card = Styles.card();
        if (kills == null || kills.isEmpty()) {
            JLabel none = Styles.keyLabel("None");
            none.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(none);
        } else {
            JPanel grid = new JPanel(new GridLayout(0, 2, 0, 3));
            grid.setBackground(Styles.CARD);
            grid.setAlignmentX(Component.LEFT_ALIGNMENT);
            int total = 0;
            for (NpcKills k : kills) {
                total += k.count;
                grid.add(Styles.keyLabel(k.npc));
                JLabel v = Styles.valueLabel(Styles.TEXT);
                v.setText(Integer.toString(k.count));
                grid.add(v);
            }
            Styles.addBoldRow(grid, "Total", Integer.toString(total), Styles.TEXT);
            Styles.capHeight(grid);
            card.add(grid);
        }
        detailBody.add(card);
    }
```

(`Component`, `GridLayout`, `List`, `JPanel`, `JLabel`, `Styles`, `GpFormat` are all already imported in `SessionsTab`.)

- [ ] **Step 3: Compile + run the full suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Launch the dev client and verify in-client**

Run: `./gradlew runClient`

Checklist (logged in):
1. **Now tab:** start tracking, kill several NPCs of different types. The "Kills" card (between Current trip and XP gained) lists each NPC most-killed-first with its count, updates live, and the **Total** matches; shows "None" before any kill. The Current-trip "Kills" line still shows the total.
2. **Sessions → trip detail:** open a past trip; the "Kills" group (after the Net/Missed summary, before "Picked up") lists that trip's per-NPC kills with Total.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/runelite/SessionsTab.java
git commit -m "feat: Sessions trip-detail Kills group"
```

---

## Final verification

- [ ] `./gradlew test` — all green (including `NpcKillsTest`, the new `TrackingServiceTest` + `SessionHistoryTest` cases).
- [ ] Re-read the spec and confirm each requirement maps to a task: carrier (T1), live snapshot (T2), stored detail (T3), Now card (T4), Sessions group (T5).
- [ ] Dispatch a final code review, then use **superpowers:finishing-a-development-branch** to open the PR.
