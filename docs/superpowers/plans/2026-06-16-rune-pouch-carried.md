# Rune Pouch in Combined Carried — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fold rune-pouch contents into the combined "carried" map so stashing runes nets to zero (no false supply) and casting from the pouch is correctly counted as a supply used.

**Architecture:** A pure `RunePouch.contents(...)` assembly helper (RuneLite-free, headless-tested) + a client-bound `RunePouchReader` that reads the slot varbits and resolves rune item ids from the game cache enum. `CarriedSnapshots.combine` gains a 3-source overload; `ClientCarriedSnapshotSupplier` feeds the pouch as the third source; the plugin marks carried dirty on rune-pouch `VarbitChanged`. The pure core `TripLedger` is unchanged.

**Tech Stack:** Java 11, JUnit 4, RuneLite (`Varbits`, `Client.getVarbitValue`, `Client.getEnum`/`EnumComposition`, `VarbitChanged`). Build/test: `./gradlew test`.

---

## File structure

- New: `src/main/java/com/goodrunetracker/adapter/RunePouch.java` — pure assembly helper.
- New: `src/main/java/com/goodrunetracker/adapter/runelite/RunePouchReader.java` — varbit read + cache-enum resolver.
- Modify: `CarriedSnapshots.java` — 3-source `combine` overload (2-arg delegates).
- Modify: `ClientCarriedSnapshotSupplier.java` — read the pouch, pass as 3rd source.
- Modify: `GoodRuneTrackerPlugin.java` — `@Subscribe onVarbitChanged` → `markCarriedDirty()` on rune-pouch varbits.
- Tests: `RunePouchTest.java` (new), `CarriedSnapshotsTest.java` (extend), `TrackingServiceTest.java` (extend — stash/cast behavior).

---

## Task 1: RunePouch pure assembly helper

`contents(int[] types, int[] amounts, IntUnaryOperator typeToItemId)` → `Map<Integer,Integer>` (runeItemId → qty). The rune-id resolution is injected, so this is fully headless-tested.

**Files:**
- Create: `src/main/java/com/goodrunetracker/adapter/RunePouch.java`
- Test: `src/test/java/com/goodrunetracker/adapter/RunePouchTest.java`

- [ ] **Step 1: Write the failing test**

Create `RunePouchTest.java`:

```java
package com.goodrunetracker.adapter;

import static org.junit.Assert.*;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import org.junit.Test;

public class RunePouchTest {

    // type 1 -> air rune (556), type 2 -> water rune (555), anything else -> unknown (0)
    private static final IntUnaryOperator RESOLVER = t -> t == 1 ? 556 : t == 2 ? 555 : 0;

    @Test
    public void mapsSlotsToRuneItemIdsAndSkipsUnknownTypes() {
        int[] types = {1, 2, 0, 9};      // slot3 empty type, slot4 unknown type
        int[] amounts = {100, 50, 0, 30}; // slot3 zero amount, slot4 unknown -> skipped
        Map<Integer, Integer> c = RunePouch.contents(types, amounts, RESOLVER);
        assertEquals(2, c.size());
        assertEquals(Integer.valueOf(100), c.get(556));
        assertEquals(Integer.valueOf(50), c.get(555));
    }

    @Test
    public void sumsTheSameRuneAcrossSlotsAndSkipsZeroAmounts() {
        int[] types = {1, 1, 1};
        int[] amounts = {30, 0, 20};     // slot2 zero amount skipped
        Map<Integer, Integer> c = RunePouch.contents(types, amounts, RESOLVER);
        assertEquals(1, c.size());
        assertEquals(Integer.valueOf(50), c.get(556)); // 30 + 20
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew test --tests 'com.goodrunetracker.adapter.RunePouchTest'`
Expected: FAIL — `RunePouch` does not exist.

- [ ] **Step 3: Create RunePouch.java**

```java
package com.goodrunetracker.adapter;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/**
 * Pure assembly of rune-pouch slot data into a runeItemId -&gt; quantity map. RuneLite-free:
 * the per-slot type value is resolved to a rune item id via the injected {@code typeToItemId}
 * (a resolver returning &le; 0 means "unknown type", and that slot is skipped).
 */
public final class RunePouch {

    private RunePouch() {
    }

    public static Map<Integer, Integer> contents(int[] types, int[] amounts,
                                                 IntUnaryOperator typeToItemId) {
        Map<Integer, Integer> out = new HashMap<>();
        int slots = Math.min(types.length, amounts.length);
        for (int i = 0; i < slots; i++) {
            int amount = amounts[i];
            if (amount <= 0) {
                continue;
            }
            int itemId = typeToItemId.applyAsInt(types[i]);
            if (itemId <= 0) {
                continue;
            }
            out.merge(itemId, amount, Integer::sum);
        }
        return out;
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew test --tests 'com.goodrunetracker.adapter.RunePouchTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/RunePouch.java \
        src/test/java/com/goodrunetracker/adapter/RunePouchTest.java
git commit -m "feat: RunePouch pure assembly helper (slots -> runeId quantities)"
```

---

## Task 2: CarriedSnapshots 3-source combine

Add a `combine(inventory, equipment, pouch)` overload; the existing 2-arg overload delegates with an empty pouch.

**Files:**
- Modify: `src/main/java/com/goodrunetracker/adapter/CarriedSnapshots.java`
- Test: `src/test/java/com/goodrunetracker/adapter/CarriedSnapshotsTest.java`

- [ ] **Step 1: Write the failing test**

Add to `CarriedSnapshotsTest.java` (it already has a `map(int... idQtyPairs)` helper):

```java
    @Test
    public void combinesPouchAsAThirdSource() {
        Map<Integer, Integer> result =
                CarriedSnapshots.combine(map(556, 100), map(560, 1), map(556, 50));
        assertEquals(Integer.valueOf(150), result.get(556)); // 100 inventory + 50 pouch
        assertEquals(Integer.valueOf(1), result.get(560));
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew test --tests 'com.goodrunetracker.adapter.CarriedSnapshotsTest'`
Expected: FAIL — no 3-arg `combine`.

- [ ] **Step 3: Add the 3-source overload and delegate the 2-arg one**

In `CarriedSnapshots.java`, add `import java.util.Collections;`. Replace the existing 2-arg `combine` so it delegates, and add the 3-arg version:

```java
    public static Map<Integer, Integer> combine(Map<Integer, Integer> inventory,
                                                Map<Integer, Integer> equipment) {
        return combine(inventory, equipment, Collections.emptyMap());
    }

    public static Map<Integer, Integer> combine(Map<Integer, Integer> inventory,
                                                Map<Integer, Integer> equipment,
                                                Map<Integer, Integer> pouch) {
        Map<Integer, Integer> out = new HashMap<>();
        addPositive(out, inventory);
        addPositive(out, equipment);
        addPositive(out, pouch);
        return out;
    }
```

(Leave the private `addPositive` helper unchanged.)

- [ ] **Step 4: Run it to verify it passes + the full suite**

Run: `./gradlew test --tests 'com.goodrunetracker.adapter.CarriedSnapshotsTest'` then `./gradlew test`
Expected: PASS (new + existing, including the unchanged 2-arg behavior).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/CarriedSnapshots.java \
        src/test/java/com/goodrunetracker/adapter/CarriedSnapshotsTest.java
git commit -m "feat: 3-source CarriedSnapshots.combine (inventory + equipment + pouch)"
```

---

## Task 3: RuneLite glue + stash/cast behavior tests

Wire the pouch into the live carried snapshot and mark carried dirty on rune-pouch varbit changes. Add `TrackingService` behavior tests that lock in stash-nets-to-zero / cast-is-a-supply. The glue is verified in-client.

**Files:**
- Create: `src/main/java/com/goodrunetracker/adapter/runelite/RunePouchReader.java`
- Modify: `src/main/java/com/goodrunetracker/adapter/runelite/ClientCarriedSnapshotSupplier.java`
- Modify: `src/main/java/com/goodrunetracker/adapter/runelite/GoodRuneTrackerPlugin.java`
- Test: `src/test/java/com/goodrunetracker/adapter/TrackingServiceTest.java`

- [ ] **Step 1: Add TrackingService behavior tests (lock in the netting/booking)**

These use the existing fake `CarriedSnapshotSupplier` to simulate the *combined* carried map
(inventory + pouch folded together). They verify the ledger's promise: a stash leaves the
combined total unchanged (no supply), and a pouch cast is a net decrease (a supply). Add to
`TrackingServiceTest.java`:

```java
    @Test
    public void stashingRunesIntoThePouchIsNotASupply() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        carried.carried.put(556, 100); // 100 air runes carried (inventory or pouch — combined)
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"));
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession(); // baseline: 556 -> 100

        // Stash all 100 runes into the pouch: combined total is unchanged.
        carried.carried.put(556, 100);
        service.markCarriedDirty();
        clock.now = 10_000;
        service.onTick();

        assertEquals(0, service.currentSnapshot().get().suppliesGp);
    }

    @Test
    public void castingRunesFromThePouchIsASupply() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        carried.carried.put(556, 100);
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"));
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession(); // baseline: 556 -> 100

        // Cast 3 runes from the pouch: combined total drops by 3, no inventory event needed.
        carried.carried.put(556, 97);
        service.markCarriedDirty();
        clock.now = 10_000;
        service.onTick();

        assertEquals(3, service.currentSnapshot().get().suppliesGp); // oneGp valuer -> 3 runes = 3
    }
```

- [ ] **Step 2: Run them**

Run: `./gradlew test --tests 'com.goodrunetracker.adapter.TrackingServiceTest'`
Expected: PASS. (These are characterization tests — the existing ledger already nets a
zero-delta to nothing and books a decrease as a supply; they lock that behavior in for runes.)

- [ ] **Step 3: Create RunePouchReader.java**

```java
package com.goodrunetracker.adapter.runelite;

import com.goodrunetracker.adapter.RunePouch;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.Varbits;

/** Reads the rune pouch's slot varbits and resolves rune item ids via the game cache enum. */
public final class RunePouchReader {

    // The rune pouch's per-slot type varbit holds an index; this cache enum maps it to a rune
    // item id (the same enum RuneLite's own runepouch plugin reads).
    private static final int RUNE_POUCH_RUNE_ENUM = 982;

    private static final int[] TYPE_VARBITS = {
        Varbits.RUNE_POUCH_RUNE1, Varbits.RUNE_POUCH_RUNE2,
        Varbits.RUNE_POUCH_RUNE3, Varbits.RUNE_POUCH_RUNE4,
    };
    private static final int[] AMOUNT_VARBITS = {
        Varbits.RUNE_POUCH_AMOUNT1, Varbits.RUNE_POUCH_AMOUNT2,
        Varbits.RUNE_POUCH_AMOUNT3, Varbits.RUNE_POUCH_AMOUNT4,
    };

    private final Client client;

    public RunePouchReader(Client client) {
        this.client = client;
    }

    /** Current rune-pouch contents as runeItemId -&gt; quantity (empty if no pouch / empty pouch). */
    public Map<Integer, Integer> contents() {
        int[] types = new int[TYPE_VARBITS.length];
        int[] amounts = new int[AMOUNT_VARBITS.length];
        for (int i = 0; i < types.length; i++) {
            types[i] = client.getVarbitValue(TYPE_VARBITS[i]);
            amounts[i] = client.getVarbitValue(AMOUNT_VARBITS[i]);
        }
        return RunePouch.contents(types, amounts, this::itemIdForType);
    }

    private int itemIdForType(int typeValue) {
        if (typeValue <= 0) {
            return 0;
        }
        EnumComposition e = client.getEnum(RUNE_POUCH_RUNE_ENUM);
        return e == null ? 0 : e.getIntValue(typeValue);
    }

    /** True if {@code varbitId} is one of the rune-pouch slot varbits (type or amount). */
    public static boolean isRunePouchVarbit(int varbitId) {
        for (int v : TYPE_VARBITS) {
            if (v == varbitId) {
                return true;
            }
        }
        for (int v : AMOUNT_VARBITS) {
            if (v == varbitId) {
                return true;
            }
        }
        return false;
    }
}
```

- [ ] **Step 4: Feed the pouch into ClientCarriedSnapshotSupplier**

In `ClientCarriedSnapshotSupplier.java`, add a `RunePouchReader` field built from the client,
and pass its contents as the third `combine` source:

```java
    private final Client client;
    private final RunePouchReader pouch;

    public ClientCarriedSnapshotSupplier(Client client) {
        this.client = client;
        this.pouch = new RunePouchReader(client);
    }

    @Override
    public Map<Integer, Integer> currentCarried() {
        return CarriedSnapshots.combine(
                toMap(client.getItemContainer(InventoryID.INVENTORY)),
                toMap(client.getItemContainer(InventoryID.EQUIPMENT)),
                pouch.contents());
    }
```

(Leave `toMap` unchanged.)

- [ ] **Step 5: Add the VarbitChanged hook in the plugin**

In `GoodRuneTrackerPlugin.java`, add `import net.runelite.api.events.VarbitChanged;`. Add a
handler (next to the other `@Subscribe` methods):

```java
    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        if (service != null && RunePouchReader.isRunePouchVarbit(event.getVarbitId())) {
            service.markCarriedDirty();
        }
    }
```

- [ ] **Step 6: Compile + run the full suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Launch the dev client and verify in-client**

Run: `./gradlew runClient`

Checklist (logged in, carrying a rune pouch with runes, tracking a trip):
1. **Stash:** put runes into the pouch → the Supplies / GP figures do **not** change (no false supply).
2. **Cast:** cast a spell using runes that come from the pouch (no loose-inventory runes of that
   type) → the consumed runes appear as a supply used.
3. **No pouch:** without a rune pouch, behavior is unchanged from before.
4. Confirm a 4-rune (divine) pouch reads its 4th slot.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/runelite/RunePouchReader.java \
        src/main/java/com/goodrunetracker/adapter/runelite/ClientCarriedSnapshotSupplier.java \
        src/main/java/com/goodrunetracker/adapter/runelite/GoodRuneTrackerPlugin.java \
        src/test/java/com/goodrunetracker/adapter/TrackingServiceTest.java
git commit -m "feat: read rune pouch into combined carried + mark dirty on pouch varbit changes"
```

---

## Final verification

- [ ] `./gradlew test` — all green (RunePouchTest, the new CarriedSnapshots + TrackingService cases).
- [ ] Re-read the spec and confirm each requirement maps to a task: pure assembly (T1), 3-source combine (T2), reader + supplier wiring + VarbitChanged hook + stash/cast behavior (T3).
- [ ] Dispatch a final code review, then use **superpowers:finishing-a-development-branch** to open the PR.
