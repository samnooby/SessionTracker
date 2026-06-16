# Charged Items (1:1 scale weapons) in Combined Carried — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fold the Zulrah's-scale charged weapons' stored scales into the combined carried map (reusing the rune-pouch mechanism) so shooting them books scale consumption in the trip where it happens, with no double-counting.

**Architecture:** A pure `ChargedItems.contents(...)` registry-assembly helper + a client-bound `ChargedItemReader` (blowpipe / serpentine helm / toxic staff → Zulrah's scales). `CarriedSnapshots.combine` collapses to varargs so pouch + charged-items both fold in; `ClientCarriedSnapshotSupplier` passes both; the existing `onVarbitChanged` hook is extended to the charge varbits. Core `TripLedger` unchanged.

**Tech Stack:** Java 11, JUnit 4, RuneLite (`gameval.VarbitID`, `Client.getVarbitValue`, `VarbitChanged`). Build/test: `./gradlew test`.

---

## File structure

- New: `src/main/java/com/goodrunetracker/adapter/ChargedItems.java` — pure assembly.
- New: `src/main/java/com/goodrunetracker/adapter/runelite/ChargedItemReader.java` — registry + varbit reads.
- Modify: `CarriedSnapshots.java` — collapse the two `combine` overloads into one varargs form.
- Modify: `ClientCarriedSnapshotSupplier.java` — pass the charged-items source too.
- Modify: `GoodRuneTrackerPlugin.java` — extend the `onVarbitChanged` guard with `isChargeVarbit`.
- Tests: `ChargedItemsTest.java` (new), `CarriedSnapshotsTest.java` (extend), `TrackingServiceTest.java` (extend).

---

## Task 1: ChargedItems pure assembly helper

`contents(int[] varbitIds, int[] itemIds, IntUnaryOperator varbitToValue)` → `Map<Integer,Integer>` (itemId → summed count). The value source is injected, so it's fully headless-tested.

**Files:**
- Create: `src/main/java/com/goodrunetracker/adapter/ChargedItems.java`
- Test: `src/test/java/com/goodrunetracker/adapter/ChargedItemsTest.java`

- [ ] **Step 1: Write the failing test**

Create `ChargedItemsTest.java`:

```java
package com.goodrunetracker.adapter;

import static org.junit.Assert.*;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import org.junit.Test;

public class ChargedItemsTest {

    // varbit 10 -> 1000, 11 -> 50, 12 -> 0, anything else -> -5
    private static final IntUnaryOperator VALUE = id -> id == 10 ? 1000 : id == 11 ? 50 : id == 12 ? 0 : -5;

    @Test
    public void mapsEachEntryToItsItemAndSumsSharedItems() {
        int[] varbits = {10, 11};
        int[] items = {12934, 12934}; // both store Zulrah's scales -> sum
        Map<Integer, Integer> c = ChargedItems.contents(varbits, items, VALUE);
        assertEquals(1, c.size());
        assertEquals(Integer.valueOf(1050), c.get(12934)); // 1000 + 50
    }

    @Test
    public void skipsZeroAndNegativeValues() {
        int[] varbits = {12, 99, 10}; // 12 -> 0, 99 -> -5, 10 -> 1000
        int[] items = {555, 556, 12934};
        Map<Integer, Integer> c = ChargedItems.contents(varbits, items, VALUE);
        assertEquals(1, c.size());
        assertEquals(Integer.valueOf(1000), c.get(12934));
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew test --tests 'com.goodrunetracker.adapter.ChargedItemsTest'`
Expected: FAIL — `ChargedItems` does not exist.

- [ ] **Step 3: Create ChargedItems.java**

```java
package com.goodrunetracker.adapter;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/**
 * Pure assembly of charged-item charge counts into an itemId -&gt; quantity map. RuneLite-free:
 * each registry entry pairs a charge varbit id with the item id it stores (1 charge = 1 item);
 * {@code varbitToValue} supplies the current charge count. Entries sharing an item id are summed;
 * a value &le; 0 is skipped.
 */
public final class ChargedItems {

    private ChargedItems() {
    }

    public static Map<Integer, Integer> contents(int[] varbitIds, int[] itemIds,
                                                 IntUnaryOperator varbitToValue) {
        Map<Integer, Integer> out = new HashMap<>();
        int n = Math.min(varbitIds.length, itemIds.length);
        for (int i = 0; i < n; i++) {
            int value = varbitToValue.applyAsInt(varbitIds[i]);
            if (value > 0) {
                out.merge(itemIds[i], value, Integer::sum);
            }
        }
        return out;
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew test --tests 'com.goodrunetracker.adapter.ChargedItemsTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/ChargedItems.java \
        src/test/java/com/goodrunetracker/adapter/ChargedItemsTest.java
git commit -m "feat: ChargedItems pure assembly helper (charge varbits -> item quantities)"
```

---

## Task 2: CarriedSnapshots varargs combine

Collapse the two `combine` overloads into one varargs form so any number of extra container sources fold in.

**Files:**
- Modify: `src/main/java/com/goodrunetracker/adapter/CarriedSnapshots.java`
- Test: `src/test/java/com/goodrunetracker/adapter/CarriedSnapshotsTest.java`

- [ ] **Step 1: Write the failing test**

Add to `CarriedSnapshotsTest.java`:

```java
    @Test
    public void combinesMultipleExtraSources() {
        Map<Integer, Integer> result = CarriedSnapshots.combine(
                map(556, 100), map(560, 1), map(556, 50), map(12934, 1000));
        assertEquals(Integer.valueOf(150), result.get(556));   // 100 inventory + 50 pouch
        assertEquals(Integer.valueOf(1), result.get(560));
        assertEquals(Integer.valueOf(1000), result.get(12934)); // charged-item scales
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew test --tests 'com.goodrunetracker.adapter.CarriedSnapshotsTest'`
Expected: FAIL — no 4-argument `combine`.

- [ ] **Step 3: Replace the two overloads with one varargs combine**

In `CarriedSnapshots.java`, remove the existing 2-arg and 3-arg `combine` methods and the now-unused `import java.util.Collections;`. Add the single varargs version (leave `addPositive` unchanged):

```java
    @SafeVarargs
    public static Map<Integer, Integer> combine(Map<Integer, Integer> inventory,
                                                Map<Integer, Integer> equipment,
                                                Map<Integer, Integer>... extras) {
        Map<Integer, Integer> out = new HashMap<>();
        addPositive(out, inventory);
        addPositive(out, equipment);
        for (Map<Integer, Integer> extra : extras) {
            addPositive(out, extra);
        }
        return out;
    }
```

(The existing call sites `combine(inv, equip)` and `combine(inv, equip, pouch)` bind to 0 and 1 `extras` — no other change needed. `@SafeVarargs` suppresses the generic-varargs warning; it is valid on a `static` method.)

- [ ] **Step 4: Run it to verify it passes + the full suite**

Run: `./gradlew test --tests 'com.goodrunetracker.adapter.CarriedSnapshotsTest'` then `./gradlew test`
Expected: PASS (the new 4-arg test plus all existing `combine` tests, including the rune-pouch 3-arg and the original 2-arg).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/CarriedSnapshots.java \
        src/test/java/com/goodrunetracker/adapter/CarriedSnapshotsTest.java
git commit -m "feat: varargs CarriedSnapshots.combine (any number of extra container sources)"
```

---

## Task 3: ChargedItemReader + wiring + charge/shoot behavior tests

Read the scale-weapon charge varbits into carried, and mark carried dirty on charge varbit changes. Add `TrackingService` behavior tests. Glue is verified in-client.

**Files:**
- Create: `src/main/java/com/goodrunetracker/adapter/runelite/ChargedItemReader.java`
- Modify: `src/main/java/com/goodrunetracker/adapter/runelite/ClientCarriedSnapshotSupplier.java`
- Modify: `src/main/java/com/goodrunetracker/adapter/runelite/GoodRuneTrackerPlugin.java`
- Test: `src/test/java/com/goodrunetracker/adapter/TrackingServiceTest.java`

- [ ] **Step 1: Add TrackingService behavior tests**

These use the fake `CarriedSnapshotSupplier` to simulate the combined carried map (item 12934 = Zulrah's scales). Add to `TrackingServiceTest.java`:

```java
    @Test
    public void chargingAScaleWeaponIsNotASupply() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        carried.carried.put(12934, 1000); // 1000 Zulrah's scales (inventory or weapon -- combined)
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"));
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession(); // baseline: 12934 -> 1000

        // Charge the weapon: scales move inventory -> weapon, combined total unchanged.
        carried.carried.put(12934, 1000);
        service.markCarriedDirty();
        clock.now = 10_000;
        service.onTick();

        assertEquals(0, service.currentSnapshot().get().suppliesGp);
    }

    @Test
    public void shootingAScaleWeaponBooksScalesAsSupply() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        carried.carried.put(12934, 1000);
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"));
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession(); // baseline: 12934 -> 1000

        // Shoot 300 scales' worth from the weapon: combined total drops, no inventory event.
        carried.carried.put(12934, 700);
        service.markCarriedDirty();
        clock.now = 10_000;
        service.onTick();

        assertEquals(300, service.currentSnapshot().get().suppliesGp); // oneGp valuer -> 300
    }
```

- [ ] **Step 2: Run them**

Run: `./gradlew test --tests 'com.goodrunetracker.adapter.TrackingServiceTest'`
Expected: PASS. (Characterization — the existing ledger nets an unchanged total to nothing and books a decrease as a supply; these lock that in for scale charges.)

- [ ] **Step 3: Create ChargedItemReader.java**

```java
package com.goodrunetracker.adapter.runelite;

import com.goodrunetracker.adapter.ChargedItems;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;

/**
 * Reads the charge counts of the "1 charge = 1 stored item" weapons and folds them into carried.
 * The initial registry is the Zulrah's-scale weapons (blowpipe / serpentine helm / toxic staff),
 * which all store Zulrah's scales, so their counts sum under one item id.
 */
public final class ChargedItemReader {

    // Zulrah's scales item id -- the consumable these weapons store (1 charge = 1 scale).
    private static final int ZULRAH_SCALE = 12934;

    private static final int[] CHARGE_VARBITS = {
        VarbitID.CHARGES_TOXIC_BLOWPIPE_QUANTITY,
        VarbitID.CHARGES_SERPENTINE_HELM_QUANTITY,
        VarbitID.CHARGES_TOXIC_STAFF_OF_THE_DEAD_QUANTITY,
    };
    private static final int[] ITEM_IDS = {
        ZULRAH_SCALE, ZULRAH_SCALE, ZULRAH_SCALE,
    };

    private final Client client;

    public ChargedItemReader(Client client) {
        this.client = client;
    }

    /** Current charged-item contents as itemId -&gt; quantity (empty if none charged). */
    public Map<Integer, Integer> contents() {
        return ChargedItems.contents(CHARGE_VARBITS, ITEM_IDS, client::getVarbitValue);
    }

    /** True if {@code varbitId} is one of the tracked charge varbits. */
    public static boolean isChargeVarbit(int varbitId) {
        for (int v : CHARGE_VARBITS) {
            if (v == varbitId) {
                return true;
            }
        }
        return false;
    }
}
```

- [ ] **Step 4: Feed it into ClientCarriedSnapshotSupplier**

In `ClientCarriedSnapshotSupplier.java`, add a `ChargedItemReader` field and pass its contents as another `combine` source. Update the field block, ctor, and `currentCarried()`:

```java
    private final Client client;
    private final RunePouchReader pouch;
    private final ChargedItemReader charged;

    public ClientCarriedSnapshotSupplier(Client client) {
        this.client = client;
        this.pouch = new RunePouchReader(client);
        this.charged = new ChargedItemReader(client);
    }

    @Override
    public Map<Integer, Integer> currentCarried() {
        return CarriedSnapshots.combine(
                toMap(client.getItemContainer(InventoryID.INVENTORY)),
                toMap(client.getItemContainer(InventoryID.EQUIPMENT)),
                pouch.contents(),
                charged.contents());
    }
```

(Leave `toMap` and imports otherwise unchanged — `ChargedItemReader` is same-package.)

- [ ] **Step 5: Extend the VarbitChanged hook in the plugin**

In `GoodRuneTrackerPlugin.java`, change the `onVarbitChanged` guard to also fire on a charge varbit:

```java
    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        if (service != null
                && (RunePouchReader.isRunePouchVarbit(event.getVarbitId())
                    || ChargedItemReader.isChargeVarbit(event.getVarbitId()))) {
            service.markCarriedDirty();
        }
    }
```

- [ ] **Step 6: Compile + run the full suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Launch the dev client and verify in-client**

Run: `./gradlew runClient`

Checklist (logged in, tracking a trip, with a scale-charged weapon — needs a blowpipe/serpentine helm/toxic staff):
1. **Shoot:** attack with a charged blowpipe/serpentine helm (no loose scales in inventory) → the consumed **scales** appear as a supply used in the trip.
2. **Charge:** add scales to the weapon mid-trip → Supplies does **not** jump (no double-count).
3. **No weapon:** without any of these weapons, behavior is unchanged.
4. Confirm the `CHARGES_…_QUANTITY` value equals the scale count and that item 12934 is Zulrah's scales (the on-ground/supply line should read "Zulrah's scales").

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/runelite/ChargedItemReader.java \
        src/main/java/com/goodrunetracker/adapter/runelite/ClientCarriedSnapshotSupplier.java \
        src/main/java/com/goodrunetracker/adapter/runelite/GoodRuneTrackerPlugin.java \
        src/test/java/com/goodrunetracker/adapter/TrackingServiceTest.java
git commit -m "feat: fold scale-charged weapons into combined carried + mark dirty on charge varbits"
```

---

## Final verification

- [ ] `./gradlew test` — all green (`ChargedItemsTest`, the `CarriedSnapshots` varargs case, the `TrackingService` charge/shoot tests).
- [ ] Re-read the spec and confirm each requirement maps to a task: pure assembly (T1), varargs combine (T2), reader + supplier wiring + hook extension + charge/shoot behavior (T3).
- [ ] Dispatch a final code review, then use **superpowers:finishing-a-development-branch** to open the PR.
