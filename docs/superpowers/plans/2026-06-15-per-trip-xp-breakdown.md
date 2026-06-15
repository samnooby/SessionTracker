# Per-Trip XP Breakdown by Skill — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show the per-skill XP gained on each trip — current trip on the Now tab, previous trips in the Sessions trip-detail view — with skill icons and a Total.

**Architecture:** The per-skill XP already lives in `Trip.xpGained` (`Map<String,Long>`) and is persisted. A new plain `SkillXp` carrier surfaces it (alphabetical) on `TripSnapshot` (live) and `SessionHistory.TripDetail` (stored). The Swing tabs render it using a `Map<String,Icon>` of skill-name → RuneLite skill icon built once at startup; data carriers stay RuneLite-free.

**Tech Stack:** Java 11, JUnit 4, RuneLite (`SkillIconManager`, `Skill`), Swing. Build/test: `./gradlew test`.

---

## File structure

- Create: `src/main/java/com/goodrunetracker/adapter/SkillXp.java` — plain `{skill, xp}` carrier + `sortedFrom(Map)` helper.
- Modify: `TripSnapshot.java` — add `xpBySkill` field + ctor param.
- Modify: `TrackingService.java` — `computeSnapshot` populates `xpBySkill`.
- Modify: `SessionHistory.java` — `TripDetail.xpGained` field + populate in `tripDetail`.
- Modify: `GoodRuneTrackerPlugin.java` — inject `SkillIconManager`, build icon map, pass to panel.
- Modify: `GoodRuneTrackerPanel.java` — accept + forward the icon map.
- Modify: `NowTab.java`, `SessionsTab.java` — accept the icon map (Task 4) and render the "XP gained" section (Tasks 5–6).
- Modify: `Styles.java` — `skillLabel(skill, icon)` helper.
- Tests: `SkillXpTest.java` (new), `TrackingServiceTest.java`, `SessionHistoryTest.java`.

---

## Task 1: SkillXp value object

A plain, RuneLite-free carrier and an alphabetical-list factory used by both the live and stored paths.

**Files:**
- Create: `src/main/java/com/goodrunetracker/adapter/SkillXp.java`
- Test: `src/test/java/com/goodrunetracker/adapter/SkillXpTest.java`

- [ ] **Step 1: Write the failing test**

Create `SkillXpTest.java`:

```java
package com.goodrunetracker.adapter;

import static org.junit.Assert.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class SkillXpTest {

    @Test
    public void sortedFromReturnsAlphabeticalListBySkill() {
        Map<String, Long> xp = new HashMap<>();
        xp.put("Ranged", 300L);
        xp.put("Attack", 200L);
        xp.put("Hitpoints", 100L);

        List<SkillXp> list = SkillXp.sortedFrom(xp);

        assertEquals(3, list.size());
        assertEquals("Attack", list.get(0).skill);
        assertEquals(200L, list.get(0).xp);
        assertEquals("Hitpoints", list.get(1).skill);
        assertEquals("Ranged", list.get(2).skill);
    }

    @Test
    public void sortedFromEmptyMapIsEmptyList() {
        assertTrue(SkillXp.sortedFrom(new HashMap<>()).isEmpty());
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew test --tests 'com.goodrunetracker.adapter.SkillXpTest'`
Expected: FAIL — `SkillXp` does not exist.

- [ ] **Step 3: Create SkillXp.java**

```java
package com.goodrunetracker.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Per-skill XP gained on a trip: a plain, RuneLite-free carrier ({@code skill} is a display name). */
public final class SkillXp {

    public final String skill;
    public final long xp;

    public SkillXp(String skill, long xp) {
        this.skill = skill;
        this.xp = xp;
    }

    /** The given per-skill XP map as a list ordered alphabetically by skill name. */
    public static List<SkillXp> sortedFrom(Map<String, Long> xpGained) {
        List<SkillXp> out = new ArrayList<>();
        for (Map.Entry<String, Long> e : new TreeMap<>(xpGained).entrySet()) {
            out.add(new SkillXp(e.getKey(), e.getValue()));
        }
        return out;
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew test --tests 'com.goodrunetracker.adapter.SkillXpTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/SkillXp.java \
        src/test/java/com/goodrunetracker/adapter/SkillXpTest.java
git commit -m "feat: SkillXp carrier + alphabetical sortedFrom"
```

---

## Task 2: TripSnapshot.xpBySkill (live current trip)

Expose the live trip's per-skill XP on the snapshot the Now tab reads.

**Files:**
- Modify: `src/main/java/com/goodrunetracker/adapter/TripSnapshot.java`
- Modify: `src/main/java/com/goodrunetracker/adapter/TrackingService.java`
- Test: `src/test/java/com/goodrunetracker/adapter/TrackingServiceTest.java`

- [ ] **Step 1: Write the failing tests**

Add to `TrackingServiceTest.java`:

```java
    @Test
    public void snapshotXpIsEmptyBeforeAnyXp() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"));
        TrackingService service = newService(clock, carried, new FakePanel(), store);
        service.startSession();
        assertTrue(service.currentSnapshot().get().xpBySkill.isEmpty());
    }

    @Test
    public void snapshotListsXpPerSkillAlphabetically() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"));
        TrackingService service = newService(clock, carried, new FakePanel(), store);
        service.startSession();
        service.onXp("Ranged", 100_000); // primes
        service.onXp("Attack", 50_000);  // primes
        service.onXp("Ranged", 100_300); // +300
        service.onXp("Attack", 50_200);  // +200

        TripSnapshot snap = service.currentSnapshot().get();
        assertEquals(2, snap.xpBySkill.size());
        assertEquals("Attack", snap.xpBySkill.get(0).skill);
        assertEquals(200L, snap.xpBySkill.get(0).xp);
        assertEquals("Ranged", snap.xpBySkill.get(1).skill);
        assertEquals(300L, snap.xpBySkill.get(1).xp);
        assertEquals(500L, snap.totalXp);
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew test --tests 'com.goodrunetracker.adapter.TrackingServiceTest'`
Expected: FAIL — `TripSnapshot` has no `xpBySkill`.

- [ ] **Step 3: Add the field to TripSnapshot**

In `TripSnapshot.java`, add `import java.util.List;`. Add the field as the last field and the last constructor parameter:

```java
    public final long gpPerHour;
    public final List<SkillXp> xpBySkill;

    public TripSnapshot(int tripNumber, long durationMillis, int kills, long pickedGp,
                        long groundGp, long suppliesGp, long totalXp, long gpPerHour,
                        List<SkillXp> xpBySkill) {
        this.tripNumber = tripNumber;
        this.durationMillis = durationMillis;
        this.kills = kills;
        this.pickedGp = pickedGp;
        this.groundGp = groundGp;
        this.suppliesGp = suppliesGp;
        this.totalXp = totalXp;
        this.gpPerHour = gpPerHour;
        this.xpBySkill = xpBySkill;
    }
```

(Leave the existing fields above `gpPerHour` unchanged; only add the `xpBySkill` field and the new constructor parameter + assignment.)

- [ ] **Step 4: Populate it in TrackingService.computeSnapshot**

In `TrackingService.java`, change the `return new TripSnapshot(...)` at the end of `computeSnapshot()` to pass the per-skill list:

```java
        int tripNumber = activeSession.trips.size() + 1;
        return new TripSnapshot(tripNumber, duration, trip.totalKills(),
                picked, ground, supplies, trip.totalXp(), gpPerHour,
                SkillXp.sortedFrom(trip.xpGained()));
```

(`trip` is the local `Trip` already built in `computeSnapshot`; `SkillXp` is in the same package, no import needed.)

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew test --tests 'com.goodrunetracker.adapter.TrackingServiceTest'`
Expected: PASS (new + all existing).

- [ ] **Step 6: Run the full suite**

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/TripSnapshot.java \
        src/main/java/com/goodrunetracker/adapter/TrackingService.java \
        src/test/java/com/goodrunetracker/adapter/TrackingServiceTest.java
git commit -m "feat: per-skill XP on the live trip snapshot"
```

---

## Task 3: SessionHistory.TripDetail.xpGained (stored trips)

Expose each stored trip's per-skill XP in the trip-detail view model.

**Files:**
- Modify: `src/main/java/com/goodrunetracker/adapter/SessionHistory.java`
- Test: `src/test/java/com/goodrunetracker/adapter/SessionHistoryTest.java`

- [ ] **Step 1: Write the failing test**

Add to `SessionHistoryTest.java`:

```java
    @Test
    public void tripDetailListsXpPerSkillAlphabetically() throws Exception {
        Path root = Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root);
        Map<String, Long> xp = new HashMap<>();
        xp.put("Ranged", 300L);
        xp.put("Attack", 200L);
        Trip t = new Trip("t1", 0, 60_000, false, new HashMap<>(), new HashMap<>(),
                new HashMap<>(), new HashMap<>(), new HashMap<>(), xp);
        StoredTrip st = SessionMapper.toStored(t, new HashMap<>());
        save(store, "acct", "s", "Vorkath", "", 0, 60_000, Arrays.asList(st));

        SessionHistory history = new SessionHistory(store, "acct", names);
        SessionHistory.TripDetail d = history.tripDetail("s", "t1");

        assertEquals(2, d.xpGained.size());
        assertEquals("Attack", d.xpGained.get(0).skill);
        assertEquals(200L, d.xpGained.get(0).xp);
        assertEquals("Ranged", d.xpGained.get(1).skill);
        assertEquals(300L, d.xpGained.get(1).xp);
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew test --tests 'com.goodrunetracker.adapter.SessionHistoryTest'`
Expected: FAIL — `TripDetail` has no `xpGained`.

- [ ] **Step 3: Add the field to TripDetail and populate it**

In `SessionHistory.java`, update the `TripDetail` nested class — add the field as the last field and last constructor parameter:

```java
    public static final class TripDetail {
        public final List<ItemLine> pickedUp;
        public final List<ItemLine> leftOnGround;
        public final List<ItemLine> suppliesUsed;
        public final long netProfit;
        public final long missedValue;
        public final List<SkillXp> xpGained;

        public TripDetail(List<ItemLine> pickedUp, List<ItemLine> leftOnGround,
                          List<ItemLine> suppliesUsed, long netProfit, long missedValue,
                          List<SkillXp> xpGained) {
            this.pickedUp = pickedUp;
            this.leftOnGround = leftOnGround;
            this.suppliesUsed = suppliesUsed;
            this.netProfit = netProfit;
            this.missedValue = missedValue;
            this.xpGained = xpGained;
        }
    }
```

Then update the `tripDetail(...)` method's `return new TripDetail(...)` to pass the list:

```java
                return new TripDetail(lines(t.pickedUp(), v), lines(t.missed(), v),
                        lines(t.suppliesUsed(), v), t.netProfit(v), t.missedValue(v),
                        SkillXp.sortedFrom(t.xpGained()));
```

(`SkillXp` is in the same package; no import needed.)

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew test --tests 'com.goodrunetracker.adapter.SessionHistoryTest'`
Expected: PASS.

- [ ] **Step 5: Run the full suite**

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/SessionHistory.java \
        src/test/java/com/goodrunetracker/adapter/SessionHistoryTest.java
git commit -m "feat: per-skill XP on the stored trip detail"
```

---

## Task 4: Skill icon map + panel/tab plumbing

Build a skill-name → icon map once at startup and thread it to the Now and Sessions tabs. No rendering yet — this task only wires the map through and must compile with the suite green. (Swing glue; no unit test.)

**Files:**
- Modify: `src/main/java/com/goodrunetracker/adapter/runelite/GoodRuneTrackerPlugin.java`
- Modify: `src/main/java/com/goodrunetracker/adapter/runelite/GoodRuneTrackerPanel.java`
- Modify: `src/main/java/com/goodrunetracker/adapter/runelite/NowTab.java`
- Modify: `src/main/java/com/goodrunetracker/adapter/runelite/SessionsTab.java`

- [ ] **Step 1: Inject SkillIconManager and build the icon map in the plugin**

In `GoodRuneTrackerPlugin.java`, add imports:

```java
import java.awt.Image;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
```

(`java.awt.image.BufferedImage`, `java.util.HashMap`, `java.util.Map`, and `javax.inject.Inject` may already be imported — do not duplicate.)

Add the injected field alongside the other `@Inject` fields:

```java
    @Inject private SkillIconManager skillIconManager;
```

Add a builder method (anywhere in the class, e.g. near `buildService`):

```java
    /** Skill-name -> small skill icon, resolved once. Keyed by Skill.getName() to match stored XP keys. */
    private Map<String, Icon> buildSkillIcons() {
        Map<String, Icon> icons = new HashMap<>();
        for (Skill skill : Skill.values()) {
            if (skill == Skill.OVERALL) {
                continue;
            }
            try {
                BufferedImage img = skillIconManager.getSkillImage(skill, true);
                if (img != null) {
                    Image scaled = img.getScaledInstance(16, 16, Image.SCALE_SMOOTH);
                    icons.put(skill.getName(), new ImageIcon(scaled));
                }
            } catch (RuntimeException ignored) {
                // A not-yet-released skill may lack an icon resource; skip it.
            }
        }
        return icons;
    }
```

In `startUp()`, change the panel construction to pass the map:

```java
        panel = new GoodRuneTrackerPanel(clientThread, buildSkillIcons());
```

- [ ] **Step 2: Forward the map through the panel**

In `GoodRuneTrackerPanel.java`, add imports `import java.util.Map;` and `import javax.swing.Icon;`. Change the constructor to accept and forward the map:

```java
    public GoodRuneTrackerPanel(ClientThread clientThread, Map<String, Icon> skillIcons) {
        this.nowTab = new NowTab(clientThread, skillIcons);
        this.sessionsTab = new SessionsTab(clientThread, skillIcons);
        this.statsTab = new StatsTab(clientThread);
        setLayout(new BorderLayout());
        tabs.addTab("Now", nowTab);
        tabs.addTab("Sessions", sessionsTab);
        tabs.addTab("Stats", statsTab);
        tabs.addChangeListener(e -> onTabSelected());
        add(tabs, BorderLayout.CENTER);
    }
```

(StatsTab is unchanged — it has no XP breakdown.)

- [ ] **Step 3: Accept + store the map in NowTab**

In `NowTab.java`, add imports `import java.util.Map;` and `import javax.swing.Icon;`. Add a field and accept it in the constructor:

```java
    private final ClientThread clientThread;
    private final Map<String, Icon> skillIcons;
```

Change the constructor signature and assign the field as the first statements:

```java
    NowTab(ClientThread clientThread, Map<String, Icon> skillIcons) {
        this.clientThread = clientThread;
        this.skillIcons = skillIcons;
        setBackground(Styles.PANEL);
        // ... rest of the existing constructor body unchanged ...
```

(The field is unused until Task 5 — that is expected.)

- [ ] **Step 4: Accept + store the map in SessionsTab**

In `SessionsTab.java`, add imports `import java.util.Map;` and `import javax.swing.Icon;`. Add a field and accept it in the constructor:

```java
    private final ClientThread clientThread;
    private final Map<String, Icon> skillIcons;
```

Change the constructor signature and assign the field as the first statements:

```java
    SessionsTab(ClientThread clientThread, Map<String, Icon> skillIcons) {
        this.clientThread = clientThread;
        this.skillIcons = skillIcons;
        setBackground(Styles.PANEL);
        // ... rest of the existing constructor body unchanged ...
```

(The field is unused until Task 6 — that is expected.)

- [ ] **Step 5: Compile and run the full suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL (no test changes; verifies the plumbing compiles).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/runelite/GoodRuneTrackerPlugin.java \
        src/main/java/com/goodrunetracker/adapter/runelite/GoodRuneTrackerPanel.java \
        src/main/java/com/goodrunetracker/adapter/runelite/NowTab.java \
        src/main/java/com/goodrunetracker/adapter/runelite/SessionsTab.java
git commit -m "feat: build skill-icon map at startup and thread it to the tabs"
```

---

## Task 5: Now tab "XP gained" card + shared skill-label helper

Render the live per-skill XP card on the Now tab, below the Current trip card.

**Files:**
- Modify: `src/main/java/com/goodrunetracker/adapter/runelite/Styles.java`
- Modify: `src/main/java/com/goodrunetracker/adapter/runelite/NowTab.java`

- [ ] **Step 1: Add the skill-label helper to Styles**

In `Styles.java`, add `import javax.swing.Icon;` (with the other `javax.swing` imports). Add:

```java
    /** A skill row label: the skill name, with its icon if one was supplied. */
    static JLabel skillLabel(String skill, Icon icon) {
        JLabel l = new JLabel(skill);
        l.setFont(FontManager.getRunescapeSmallFont());
        l.setForeground(SUBTEXT);
        if (icon != null) {
            l.setIcon(icon);
            l.setIconTextGap(5);
        }
        return l;
    }
```

- [ ] **Step 2: Add the XP card to NowTab**

In `NowTab.java`, add imports `import java.util.List;` (if not already present) and `import com.goodrunetracker.adapter.SkillXp;`. Add a field for the card's dynamic body next to the other value fields:

```java
    private final JPanel xpBody = new JPanel(new GridLayout(0, 2, 0, 3));
```

In the constructor, insert the XP section between the Current trip card and the "Session so far" section header. Find:

```java
        body.add(Styles.sectionHeader("Current trip"));
        body.add(currentCard());

        body.add(Styles.sectionHeader("Session so far"));
```

and change it to:

```java
        body.add(Styles.sectionHeader("Current trip"));
        body.add(currentCard());

        body.add(Styles.sectionHeader("XP gained"));
        body.add(xpCard());

        body.add(Styles.sectionHeader("Session so far"));
```

Add the `xpCard()` builder method:

```java
    private JPanel xpCard() {
        JPanel card = Styles.card();
        xpBody.setBackground(Styles.CARD);
        xpBody.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        card.add(xpBody);
        return card;
    }
```

- [ ] **Step 3: Render the XP rows from the snapshot**

In `NowTab.java`, add a `renderXp(...)` method:

```java
    private void renderXp(List<SkillXp> xp) {
        xpBody.removeAll();
        if (xp == null || xp.isEmpty()) {
            xpBody.add(Styles.keyLabel("None"));
            xpBody.add(new JLabel(""));
        } else {
            long total = 0;
            for (SkillXp s : xp) {
                total += s.xp;
                xpBody.add(Styles.skillLabel(s.skill, skillIcons.get(s.skill)));
                JLabel v = Styles.valueLabel(Styles.XP);
                v.setText(GpFormat.format(s.xp));
                xpBody.add(v);
            }
            JLabel totalKey = new JLabel("Total");
            totalKey.setFont(net.runelite.client.ui.FontManager.getRunescapeBoldFont());
            totalKey.setForeground(Styles.TEXT);
            JLabel totalVal = new JLabel(GpFormat.format(total));
            totalVal.setFont(net.runelite.client.ui.FontManager.getRunescapeBoldFont());
            totalVal.setForeground(Styles.XP);
            totalVal.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
            xpBody.add(totalKey);
            xpBody.add(totalVal);
        }
        xpBody.revalidate();
        xpBody.repaint();
    }
```

Then call it from `renderStats()`. In the `if (snap.isPresent())` branch, after the existing `elapsed.setText(...)` line, add:

```java
            renderXp(s.xpBySkill);
```

and in the matching `else` branch (no snapshot), after `elapsed.setText("");`, add:

```java
            renderXp(null);
```

- [ ] **Step 4: Compile and run the full suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/runelite/Styles.java \
        src/main/java/com/goodrunetracker/adapter/runelite/NowTab.java
git commit -m "feat: Now tab XP-gained card (per-skill, icons, total)"
```

---

## Task 6: Sessions trip-detail "XP gained" group

Render the stored per-skill XP in the trip-detail view, after "Supplies used".

**Files:**
- Modify: `src/main/java/com/goodrunetracker/adapter/runelite/SessionsTab.java`

- [ ] **Step 1: Add the XP group to renderDetail**

In `SessionsTab.java`, add `import com.goodrunetracker.adapter.SkillXp;`. In `renderDetail(...)`, inside the `if (d != null)` block, after the line:

```java
            addGroup("Supplies used", d.suppliesUsed, Styles.NEG);
```

add:

```java
            addXpGroup(d.xpGained);
```

- [ ] **Step 2: Add the addXpGroup method**

Add this method to `SessionsTab.java` (next to `addGroup`):

```java
    private void addXpGroup(List<SkillXp> xp) {
        detailBody.add(Styles.sectionHeader("XP gained"));
        JPanel card = Styles.card();
        if (xp == null || xp.isEmpty()) {
            JLabel none = Styles.keyLabel("None");
            none.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(none);
        } else {
            JPanel grid = new JPanel(new GridLayout(0, 2, 0, 3));
            grid.setBackground(Styles.CARD);
            grid.setAlignmentX(Component.LEFT_ALIGNMENT);
            long total = 0;
            for (SkillXp s : xp) {
                total += s.xp;
                grid.add(Styles.skillLabel(s.skill, skillIcons.get(s.skill)));
                JLabel v = Styles.valueLabel(Styles.XP);
                v.setText(GpFormat.format(s.xp));
                grid.add(v);
            }
            JLabel totalKey = new JLabel("Total");
            totalKey.setFont(FontManager.getRunescapeBoldFont());
            totalKey.setForeground(Styles.TEXT);
            JLabel totalVal = new JLabel(GpFormat.format(total));
            totalVal.setFont(FontManager.getRunescapeBoldFont());
            totalVal.setForeground(Styles.XP);
            totalVal.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
            grid.add(totalKey);
            grid.add(totalVal);
            Styles.capHeight(grid);
            card.add(grid);
        }
        detailBody.add(card);
    }
```

(`FontManager` is already imported in `SessionsTab`; `Component`, `GridLayout`, `List`, `JPanel`, `JLabel` are too.)

- [ ] **Step 3: Compile and run the full suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Launch the dev client and verify in-client**

Run: `./gradlew runClient`

Checklist (logged in):
1. **Now tab:** start tracking, train two or more skills (e.g. kill something for combat XP). The "XP gained" card lists each skill alphabetically with its icon and amount, updates live, and the **Total** matches; shows "None" before any XP.
2. **Sessions → trip detail:** open a past trip; the "XP gained" group lists that trip's per-skill XP with icons and Total, after "Supplies used".

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/runelite/SessionsTab.java
git commit -m "feat: Sessions trip-detail XP-gained group"
```

---

## Final verification

- [ ] `./gradlew test` — all green (including `SkillXpTest`, the new `TrackingServiceTest` + `SessionHistoryTest` cases).
- [ ] Re-read the spec and confirm each requirement maps to a task: data carrier (T1), live snapshot (T2), stored detail (T3), icon map + plumbing (T4), Now card (T5), Sessions group (T6).
- [ ] Dispatch a final code review, then use **superpowers:finishing-a-development-branch** to open the PR.
