# Good Rune Tracker — Phase 2c: Drop Detection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Detect when the player drops an item (via the "Drop" menu action) and reclassify it correctly — a dropped looted item returns to "missed" (left on the ground) instead of counting as a supply used, while a dropped brought item still counts as a supply.

**Architecture:** The `TripLedger` gains a drop-aware `updateCarried` overload that, for a per-tick inventory decrease, reverses a looted pickup (the rest being a brought-item supply) when the item was dropped this tick — and otherwise treats the decrease as a consume (supply), exactly as today. `TrackingService` buffers dropped item keys from a new `markDropped` call and feeds them to the ledger each tick. The plugin maps RuneLite's `MenuOptionClicked("Drop")` to `markDropped`.

**Tech Stack:** Java 11, Gradle, JUnit 4, RuneLite client API (`compileOnly`).

**Reference:** Phase 2c spec `docs/superpowers/specs/2026-06-15-phase2c-drop-detection-design.md`. Builds on the merged Phase 2b.

---

## Existing code this plan modifies

- `com.goodrunetracker.core.TripLedger` — current `updateCarried(Map<ItemKey,Integer>)` classifies each per-tick delta: positive → `reconcilePickup`, negative → `suppliesUsed`. It owns `pickedUp`, `groundPool`, `suppliesUsed`, `dropped` maps; `build(...)` computes `missed = dropped − pickedUp`.
- `com.goodrunetracker.adapter.TrackingService` — `onTick()` calls `ledger.updateCarried(normalize(carried.currentCarried()))` when `inventoryDirty`; `normalize(...)` learns potions and converts a raw `Map<Integer,Integer>` to `Map<ItemKey,Integer>`.
- `com.goodrunetracker.adapter.runelite.GoodRuneTrackerPlugin` — `@Subscribe` handlers forwarding RuneLite events to the service.

## File Structure

- Modify: `src/main/java/com/goodrunetracker/core/TripLedger.java`
- Modify: `src/test/java/com/goodrunetracker/core/TripLedgerTest.java`
- Modify: `src/main/java/com/goodrunetracker/adapter/TrackingService.java`
- Modify: `src/test/java/com/goodrunetracker/adapter/TrackingServiceTest.java`
- Modify: `src/main/java/com/goodrunetracker/adapter/runelite/GoodRuneTrackerPlugin.java`

---

## Task 1: `TripLedger` drop-aware `updateCarried`

**Files:**
- Modify: `src/main/java/com/goodrunetracker/core/TripLedger.java`
- Test: `src/test/java/com/goodrunetracker/core/TripLedgerTest.java`

- [ ] **Step 1: Write the failing tests**

Add these test methods inside the existing `TripLedgerTest` class (it already has the `carried(...)` helper and `oneGp` valuer):

```java
    @Test
    public void droppingALootedItemReversesThePickupBackToMissed() {
        TripLedger ledger = new TripLedger();
        ledger.updateCarried(carried());                                   // baseline empty
        ledger.recordKill("x", carried(ItemKey.item(560), 100));           // dropped 100
        ledger.updateCarried(carried(ItemKey.item(560), 100));             // pick up all 100
        // now drop all 100 (item 560 dropped this tick)
        java.util.Set<ItemKey> dropped = new java.util.HashSet<>();
        dropped.add(ItemKey.item(560));
        ledger.updateCarried(carried(), dropped);
        Trip trip = ledger.build("t1", 0, 60_000, false);
        assertNull(trip.pickedUp().get(ItemKey.item(560)));                // pickup reversed
        assertEquals(Integer.valueOf(100), trip.missed().get(ItemKey.item(560))); // back to missed
        assertTrue(trip.suppliesUsed().isEmpty());                         // NOT a supply
    }

    @Test
    public void droppingABroughtItemCountsAsSupply() {
        TripLedger ledger = new TripLedger();
        ledger.updateCarried(carried(ItemKey.item(560), 5));               // brought 5, no kill
        java.util.Set<ItemKey> dropped = new java.util.HashSet<>();
        dropped.add(ItemKey.item(560));
        ledger.updateCarried(carried(), dropped);                          // drop all 5
        Trip trip = ledger.build("t1", 0, 60_000, false);
        assertEquals(Integer.valueOf(5), trip.suppliesUsed().get(ItemKey.item(560)));
    }

    @Test
    public void consumingALootedItemIsStillASupplyNotADrop() {
        // No drop set => a decrease is a consume, even for a looted item (overlap stays correct).
        TripLedger ledger = new TripLedger();
        ledger.updateCarried(carried());
        ledger.recordKill("x", carried(ItemKey.item(385), 4));             // dropped 4 sharks
        ledger.updateCarried(carried(ItemKey.item(385), 4));               // pick up 4
        ledger.updateCarried(carried(ItemKey.item(385), 1));               // eat 3 (no drop set)
        Trip trip = ledger.build("t1", 0, 60_000, false);
        assertEquals(Integer.valueOf(4), trip.pickedUp().get(ItemKey.item(385)));
        assertEquals(Integer.valueOf(3), trip.suppliesUsed().get(ItemKey.item(385)));
    }

    @Test
    public void partlyLootedPartlyBroughtDropSplitsCorrectly() {
        // Brought 2, looted 3 of the same item, then drop all 5.
        TripLedger ledger = new TripLedger();
        ledger.updateCarried(carried(ItemKey.item(560), 2));               // brought 2
        ledger.recordKill("x", carried(ItemKey.item(560), 3));             // dropped 3
        ledger.updateCarried(carried(ItemKey.item(560), 5));               // pick up the 3 -> total 5
        java.util.Set<ItemKey> dropped = new java.util.HashSet<>();
        dropped.add(ItemKey.item(560));
        ledger.updateCarried(carried(), dropped);                          // drop all 5
        Trip trip = ledger.build("t1", 0, 60_000, false);
        // 3 looted reversed -> missed; 2 brought -> supply
        assertEquals(Integer.valueOf(3), trip.missed().get(ItemKey.item(560)));
        assertEquals(Integer.valueOf(2), trip.suppliesUsed().get(ItemKey.item(560)));
        assertNull(trip.pickedUp().get(ItemKey.item(560)));
    }
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew test --tests com.goodrunetracker.core.TripLedgerTest`
Expected: FAIL — `updateCarried(Map, Set)` does not exist (compilation error).

- [ ] **Step 3: Implement the overload and `reverseDrop`**

In `src/main/java/com/goodrunetracker/core/TripLedger.java`, add the import `import java.util.Collections;` (alongside the existing `java.util.*` imports). Replace the existing `updateCarried` method with the two-method form below, and add the private `reverseDrop` helper after `reconcilePickup`:

```java
    public void updateCarried(Map<ItemKey, Integer> settledCarried) {
        updateCarried(settledCarried, Collections.emptySet());
    }

    public void updateCarried(Map<ItemKey, Integer> settledCarried, Set<ItemKey> droppedThisTick) {
        if (carried == null) {
            carried = new HashMap<>(settledCarried);
            return;
        }
        Set<ItemKey> keys = new HashSet<>();
        keys.addAll(carried.keySet());
        keys.addAll(settledCarried.keySet());
        for (ItemKey key : keys) {
            int delta = settledCarried.getOrDefault(key, 0) - carried.getOrDefault(key, 0);
            if (delta > 0) {
                reconcilePickup(key, delta);
            } else if (delta < 0) {
                int lost = -delta;
                if (droppedThisTick.contains(key)) {
                    reverseDrop(key, lost);
                } else {
                    suppliesUsed.merge(key, lost, Integer::sum);
                }
            }
        }
        carried = new HashMap<>(settledCarried);
    }

    private void reverseDrop(ItemKey key, int lost) {
        int pickedUpQty = pickedUp.getOrDefault(key, 0);
        int reversed = Math.min(lost, pickedUpQty);
        if (reversed > 0) {
            int remaining = pickedUpQty - reversed;
            if (remaining == 0) {
                pickedUp.remove(key);
            } else {
                pickedUp.put(key, remaining);
            }
            // Put it back on the ground so a later re-pickup reconciles. Reducing pickedUp
            // automatically returns this quantity to missed (missed = dropped - pickedUp).
            groundPool.merge(key, reversed, Integer::sum);
        }
        int brought = lost - reversed;
        if (brought > 0) {
            suppliesUsed.merge(key, brought, Integer::sum);
        }
    }
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests com.goodrunetracker.core.TripLedgerTest`
Expected: PASS (all existing tests plus the 4 new ones — the single-arg `updateCarried` still works via delegation).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/goodrunetracker/core/TripLedger.java src/test/java/com/goodrunetracker/core/TripLedgerTest.java
git commit -m "feat: drop-aware updateCarried reverses looted drops to missed"
```

---

## Task 2: `TrackingService` — `markDropped` + per-tick wiring

**Files:**
- Modify: `src/main/java/com/goodrunetracker/adapter/TrackingService.java`
- Test: `src/test/java/com/goodrunetracker/adapter/TrackingServiceTest.java`

- [ ] **Step 1: Write the failing tests**

Add these test methods inside the existing `TrackingServiceTest` class (it has the `FakeClock`/`FakeCarried`/`FakePanel` doubles and the `newService(...)` helper; the file already imports `java.util.*`, `java.nio.file.*`, `ItemKey`):

```java
    @Test
    public void droppingALootedItemMovesItToMissedNotSupplies() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"));
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession();
        Map<Integer, Integer> drop = new HashMap<>();
        drop.put(560, 100);
        service.onKill("Demonic gorilla", drop);   // dropped 100
        carried.carried.put(560, 100);             // pick up all 100
        service.markCarriedDirty();
        clock.now = 10_000;
        service.onTick();

        service.markDropped(560);                  // player drops item 560
        carried.carried.remove(560);               // inventory now empty
        service.markCarriedDirty();
        clock.now = 20_000;
        service.onTick();

        TripSnapshot snap = service.currentSnapshot().get();
        assertEquals(0, snap.pickedGp);            // pickup reversed
        assertEquals(100, snap.groundGp);          // back to "on ground" (1gp each)
        assertEquals(0, snap.suppliesGp);          // NOT a supply
    }

    @Test
    public void consumingALootedItemStillCountsAsSupply() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"));
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession();
        Map<Integer, Integer> drop = new HashMap<>();
        drop.put(385, 4);
        service.onKill("x", drop);                 // looted 4 sharks
        carried.carried.put(385, 4);
        service.markCarriedDirty();
        clock.now = 10_000;
        service.onTick();

        // eat 3 — NO markDropped, so it's a consume
        carried.carried.put(385, 1);
        service.markCarriedDirty();
        clock.now = 20_000;
        service.onTick();

        TripSnapshot snap = service.currentSnapshot().get();
        assertEquals(4, snap.pickedGp);            // still picked up 4
        assertEquals(3, snap.suppliesGp);          // ate 3 -> supply
    }
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew test --tests com.goodrunetracker.adapter.TrackingServiceTest`
Expected: FAIL — `markDropped` does not exist.

- [ ] **Step 3: Implement `markDropped` and wire `onTick`**

In `src/main/java/com/goodrunetracker/adapter/TrackingService.java`:

Add the import `import com.goodrunetracker.core.item.Doses;` (next to the other `com.goodrunetracker.core.item.*` imports).

Add a field next to the existing `lastXp` field:

```java
    private final java.util.Set<ItemKey> droppedThisTick = new java.util.HashSet<>();
```

Add the public method (place it next to `markCarriedDirty()`):

```java
    /** Record that the player just dropped this raw item id (from the "Drop" menu action). */
    public void markDropped(int rawItemId) {
        droppedThisTick.add(toKey(rawItemId));
    }

    private ItemKey toKey(int rawItemId) {
        return Doses.parse(names.apply(rawItemId))
                .map(form -> ItemKey.potion(form.family()))
                .orElse(ItemKey.item(rawItemId));
    }
```

Replace the body of `onTick()` with the drop-aware version (passes the buffer and clears it each tick):

```java
    public void onTick() {
        if (ledger == null || awaitingDeathChoice) {
            return;
        }
        if (inventoryDirty) {
            ledger.updateCarried(normalize(carried.currentCarried()), droppedThisTick);
            inventoryDirty = false;
        }
        droppedThisTick.clear();
        refreshCache();
        panel.refresh();
    }
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests com.goodrunetracker.adapter.TrackingServiceTest`
Expected: PASS (existing tests plus the 2 new ones).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/TrackingService.java src/test/java/com/goodrunetracker/adapter/TrackingServiceTest.java
git commit -m "feat: TrackingService.markDropped feeds dropped items to the ledger per tick"
```

---

## Task 3: Plugin — map `MenuOptionClicked("Drop")` to `markDropped`

**Files:**
- Modify: `src/main/java/com/goodrunetracker/adapter/runelite/GoodRuneTrackerPlugin.java`

This is RuneLite glue (compile-checked + in-client verified).

- [ ] **Step 1: Add the import**

Add to `GoodRuneTrackerPlugin.java`:

```java
import net.runelite.api.events.MenuOptionClicked;
```

- [ ] **Step 2: Add the `@Subscribe` handler**

Add this handler alongside the other `@Subscribe` methods:

```java
    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (service != null && "Drop".equals(event.getMenuOption()) && event.getItemId() > 0) {
            service.markDropped(event.getItemId());
        }
    }
```

**API note for the implementer:** confirm against the client jar that `MenuOptionClicked` has `getMenuOption()` (the option text, e.g. `"Drop"`) and `getItemId()` (the inventory item id, `-1`/`0` when not an item). Both are long-standing, but if `getItemId()` is absent in this version, resolve via the menu entry (`event.getMenuEntry().getItemId()`); inspect the jar (`javap -p -classpath <client.jar> net.runelite.api.events.MenuOptionClicked`) and adjust to what exists.

- [ ] **Step 3: Verify the build compiles and all tests pass**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/runelite/GoodRuneTrackerPlugin.java
git commit -m "feat: detect Drop menu action and report it to the tracker"
```

---

## Task 4: In-client verification

**Files:** none (manual — the `@Subscribe`/client behavior can't be unit-tested).

- [ ] **Step 1: Launch the dev client**

Run: `./gradlew runClient`. Log in, open the Good Rune Tracker panel, Start tracking.

- [ ] **Step 2: Verify a dropped looted item returns to "On ground"**

Kill a monster and pick up a drop (watch "Loot picked" rise). Then **drop that looted item**. Confirm "Loot picked" falls and "On ground" rises by the same value — and **"Supplies" does not change**.

- [ ] **Step 3: Verify a dropped brought item counts as a supply**

Drop an item you brought from the bank (one not dropped by a monster this trip). Confirm "Supplies" rises.

- [ ] **Step 4: Verify consuming a looted consumable is still a supply**

In a spot where the monster drops a consumable you also use (e.g. a potion or food), loot one and then consume it. Confirm "Supplies" rises (it is counted as used, not reversed to missed) — this is the overlap case the design preserves.

- [ ] **Step 5: Record results**

Note any discrepancies. If all three behaviors are correct, Phase 2c is verified.

---

## Phase 2c done — what comes next

- **Phase 3:** the polished tabbed Now / Sessions / Stats panel, history browsing, edit/recategorize, and the per-trip `FrozenItemValuer` read path (`Function<Trip,ItemValuer>` core overloads + `StoredSession → Session` reader).
