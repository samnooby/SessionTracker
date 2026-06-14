# Good Rune Tracker — Phase 2a: Adapter Logic Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build all the RuneLite-adapter *logic* — item-key serialization, potion pricing, item valuation, per-account JSON persistence, and the session/trip lifecycle state machine — as plain Java behind injected interfaces, fully unit-tested without a live RuneLite client.

**Architecture:** A new `com.goodrunetracker.adapter` package sits on top of the Phase 1 `com.goodrunetracker.core` domain core. `TrackingService` is the lifecycle state machine; it takes its RuneLite-bound collaborators (clock, inventory snapshot supplier, item-name lookup, panel view, persistence) as interfaces so it runs headless in tests. Phase 2b supplies the real RuneLite implementations and the `@Subscribe` wiring.

**Tech Stack:** Java 11, Gradle, JUnit 4, Gson (provided by the RuneLite client at runtime; declared `compileOnly` + `testImplementation` here). No Lombok (avoided for JDK 24/25 compatibility; plain Java throughout).

**Reference:** Phase 2 spec at `docs/superpowers/specs/2026-06-14-phase2-runelite-adapter-design.md`. Builds on the merged Phase 1 core.

---

## Core types this plan depends on (Phase 1, already present)

- `com.goodrunetracker.core.item.ItemKey` — `ItemKey.item(int)`, `ItemKey.potion(String)`, `isPotion()`, `itemId()`, `potionFamily()` (the latter two throw `IllegalStateException` on the wrong variant).
- `com.goodrunetracker.core.item.Doses.parse(String) -> Optional<DoseForm>`; `DoseForm.family()`, `DoseForm.dose()`.
- `com.goodrunetracker.core.item.CarriedNormalizer.normalize(Map<Integer,Integer>, IntFunction<String>) -> Map<ItemKey,Integer>`.
- `com.goodrunetracker.core.item.ItemValuer` — `long value(ItemKey, int)`.
- `com.goodrunetracker.core.TripLedger` — `recordKill(String, Map<ItemKey,Integer>)`, `updateCarried(Map<ItemKey,Integer>)`, `recordXp(String, long)`, `build(String,long,long,boolean) -> Trip`.
- `com.goodrunetracker.core.Trip` — `id()`, `startMillis()`, `endMillis()`, `died()`, `kills()`, `dropped()`, `pickedUp()`, `missed()`, `suppliesUsed()`, `xpGained()`, `totalKills()`, `totalXp()`, `pickedUpValue(ItemValuer)`, `suppliesValue(ItemValuer)`, `missedValue(ItemValuer)`, `netProfit(ItemValuer)`.

**Units:** item quantities `int`; gp `long`; xp `long`; epoch-millis `long`. Captured **unit prices** are `long` (gp per single item / per dose).

---

## File Structure (all new, under `src/main/java/com/goodrunetracker/adapter/`)

- `ItemKeyCodec.java` — `ItemKey` ↔ string token.
- `PotionRegistry.java` — learns a representative `(itemId, dose)` per potion family.
- `ItemPriceSource.java` — interface: gp price of a raw item id.
- `FrozenItemValuer.java` — `ItemValuer` from a captured unit-price map.
- `LiveItemValuer.java` — `ItemValuer` from live prices + the potion registry.
- `StoredTrip.java`, `StoredSession.java` — Gson DTOs (string-keyed maps).
- `SessionMapper.java` — core `Trip` (+ unit prices) ↔ `StoredTrip`.
- `SessionStore.java` — per-account JSON read/write.
- `Clock.java`, `CarriedSnapshotSupplier.java`, `PanelView.java` — injected collaborator interfaces.
- `TripSnapshot.java` — the live readout value object the panel renders.
- `TrackingService.java` — the lifecycle state machine.

Mirrored tests under `src/test/java/com/goodrunetracker/adapter/`.

---

## Task 1: Add Gson dependency

**Files:**
- Modify: `build.gradle`

- [ ] **Step 1: Add Gson to dependencies**

In `build.gradle`, replace the existing `dependencies { ... }` block with:

```groovy
dependencies {
    compileOnly group: 'net.runelite', name: 'client', version: runeLiteVersion
    compileOnly 'com.google.code.gson:gson:2.10.1'

    testImplementation group: 'net.runelite', name: 'client', version: runeLiteVersion
    testImplementation 'junit:junit:4.13.2'
    testImplementation 'com.google.code.gson:gson:2.10.1'
}
```

(`compileOnly` for Gson because the RuneLite client bundles it at runtime — we must not re-bundle it for the Plugin Hub. `testImplementation` so our unit tests have it on the test classpath.)

- [ ] **Step 2: Verify the build still succeeds**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL` (all existing Phase 1 tests still pass; no new sources yet).

- [ ] **Step 3: Commit**

```bash
git add build.gradle
git commit -m "chore: add Gson dependency for Phase 2 persistence"
```

---

## Task 2: `ItemKeyCodec`

**Files:**
- Create: `src/main/java/com/goodrunetracker/adapter/ItemKeyCodec.java`
- Test: `src/test/java/com/goodrunetracker/adapter/ItemKeyCodecTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.goodrunetracker.adapter;

import static org.junit.Assert.*;
import com.goodrunetracker.core.item.ItemKey;
import org.junit.Test;

public class ItemKeyCodecTest {

    @Test
    public void roundTripsItemKeys() {
        ItemKey key = ItemKey.item(560);
        assertEquals("item:560", ItemKeyCodec.encode(key));
        assertEquals(key, ItemKeyCodec.decode("item:560"));
    }

    @Test
    public void roundTripsPotionKeys() {
        ItemKey key = ItemKey.potion("Prayer potion");
        assertEquals("potion:Prayer potion", ItemKeyCodec.encode(key));
        assertEquals(key, ItemKeyCodec.decode("potion:Prayer potion"));
    }

    @Test
    public void preservesColonsInPotionFamily() {
        // Decode must keep everything after the first "potion:" prefix.
        ItemKey key = ItemKey.potion("Weird: brew");
        assertEquals(key, ItemKeyCodec.decode(ItemKeyCodec.encode(key)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownToken() {
        ItemKeyCodec.decode("mystery:1");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.goodrunetracker.adapter.ItemKeyCodecTest`
Expected: FAIL — `ItemKeyCodec` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.goodrunetracker.adapter;

import com.goodrunetracker.core.item.ItemKey;

/** Serializes {@link ItemKey} to/from a compact string token for JSON map keys. */
public final class ItemKeyCodec {

    private static final String ITEM = "item:";
    private static final String POTION = "potion:";

    private ItemKeyCodec() {
    }

    public static String encode(ItemKey key) {
        return key.isPotion() ? POTION + key.potionFamily() : ITEM + key.itemId();
    }

    public static ItemKey decode(String token) {
        if (token.startsWith(ITEM)) {
            return ItemKey.item(Integer.parseInt(token.substring(ITEM.length())));
        }
        if (token.startsWith(POTION)) {
            return ItemKey.potion(token.substring(POTION.length()));
        }
        throw new IllegalArgumentException("Unrecognized ItemKey token: " + token);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.goodrunetracker.adapter.ItemKeyCodecTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/ItemKeyCodec.java src/test/java/com/goodrunetracker/adapter/ItemKeyCodecTest.java
git commit -m "feat: ItemKey string codec for persistence"
```

---

## Task 3: `PotionRegistry`

**Files:**
- Create: `src/main/java/com/goodrunetracker/adapter/PotionRegistry.java`
- Test: `src/test/java/com/goodrunetracker/adapter/PotionRegistryTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.goodrunetracker.adapter;

import static org.junit.Assert.*;
import java.util.Optional;
import org.junit.Test;

public class PotionRegistryTest {

    @Test
    public void learnsRepresentativeForAPotionFamily() {
        PotionRegistry registry = new PotionRegistry();
        registry.observe(139, "Prayer potion(3)");
        Optional<PotionRegistry.Rep> rep = registry.representativeFor("Prayer potion");
        assertTrue(rep.isPresent());
        assertEquals(139, rep.get().itemId());
        assertEquals(3, rep.get().dose());
    }

    @Test
    public void prefersTheHighestDoseFormSeen() {
        PotionRegistry registry = new PotionRegistry();
        registry.observe(139, "Prayer potion(3)");
        registry.observe(2434, "Prayer potion(4)");
        registry.observe(141, "Prayer potion(2)");
        PotionRegistry.Rep rep = registry.representativeFor("Prayer potion").get();
        assertEquals(2434, rep.itemId());
        assertEquals(4, rep.dose());
    }

    @Test
    public void ignoresNonPotionItems() {
        PotionRegistry registry = new PotionRegistry();
        registry.observe(4151, "Abyssal whip");
        assertFalse(registry.representativeFor("Abyssal whip").isPresent());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.goodrunetracker.adapter.PotionRegistryTest`
Expected: FAIL — `PotionRegistry` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.goodrunetracker.adapter;

import com.goodrunetracker.core.item.Doses;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Learns a representative dose-form item id for each potion family as raw item
 * snapshots are observed. The core collapses potions to a family name and loses
 * the id; this restores an id so the family can be priced per dose.
 */
public final class PotionRegistry {

    /** A representative dose form: which item id, and how many doses it carries. */
    public static final class Rep {
        private final int itemId;
        private final int dose;

        Rep(int itemId, int dose) {
            this.itemId = itemId;
            this.dose = dose;
        }

        public int itemId() {
            return itemId;
        }

        public int dose() {
            return dose;
        }
    }

    private final Map<String, Rep> reps = new HashMap<>();

    public void observe(int itemId, String name) {
        Doses.parse(name).ifPresent(form -> {
            Rep current = reps.get(form.family());
            if (current == null || form.dose() > current.dose) {
                reps.put(form.family(), new Rep(itemId, form.dose()));
            }
        });
    }

    public Optional<Rep> representativeFor(String family) {
        return Optional.ofNullable(reps.get(family));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.goodrunetracker.adapter.PotionRegistryTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/PotionRegistry.java src/test/java/com/goodrunetracker/adapter/PotionRegistryTest.java
git commit -m "feat: PotionRegistry learns representative dose form per family"
```

---

## Task 4: Valuation — `ItemPriceSource`, `FrozenItemValuer`, `LiveItemValuer`

**Files:**
- Create: `src/main/java/com/goodrunetracker/adapter/ItemPriceSource.java`
- Create: `src/main/java/com/goodrunetracker/adapter/FrozenItemValuer.java`
- Create: `src/main/java/com/goodrunetracker/adapter/LiveItemValuer.java`
- Test: `src/test/java/com/goodrunetracker/adapter/FrozenItemValuerTest.java`
- Test: `src/test/java/com/goodrunetracker/adapter/LiveItemValuerTest.java`

- [ ] **Step 1: Create the `ItemPriceSource` interface**

```java
package com.goodrunetracker.adapter;

/** Live gp price of a single raw item id. Implemented in Phase 2b via ItemManager. */
public interface ItemPriceSource {
    int price(int itemId);
}
```

- [ ] **Step 2: Write the failing `FrozenItemValuer` test**

```java
package com.goodrunetracker.adapter;

import static org.junit.Assert.*;
import com.goodrunetracker.core.item.ItemKey;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class FrozenItemValuerTest {

    @Test
    public void valuesAtCapturedUnitPrices() {
        Map<ItemKey, Long> prices = new HashMap<>();
        prices.put(ItemKey.item(560), 200L);
        FrozenItemValuer valuer = new FrozenItemValuer(prices);
        assertEquals(2000, valuer.value(ItemKey.item(560), 10));
    }

    @Test
    public void unknownKeyValuesToZero() {
        FrozenItemValuer valuer = new FrozenItemValuer(new HashMap<>());
        assertEquals(0, valuer.value(ItemKey.item(999), 5));
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew test --tests com.goodrunetracker.adapter.FrozenItemValuerTest`
Expected: FAIL — `FrozenItemValuer` does not exist.

- [ ] **Step 4: Implement `FrozenItemValuer`**

```java
package com.goodrunetracker.adapter;

import com.goodrunetracker.core.item.ItemKey;
import com.goodrunetracker.core.item.ItemValuer;
import java.util.HashMap;
import java.util.Map;

/** Values items at unit prices captured when a trip ended (stable history). */
public final class FrozenItemValuer implements ItemValuer {

    private final Map<ItemKey, Long> unitPrices;

    public FrozenItemValuer(Map<ItemKey, Long> unitPrices) {
        this.unitPrices = new HashMap<>(unitPrices);
    }

    @Override
    public long value(ItemKey key, int quantity) {
        return unitPrices.getOrDefault(key, 0L) * quantity;
    }
}
```

- [ ] **Step 5: Write the failing `LiveItemValuer` test**

```java
package com.goodrunetracker.adapter;

import static org.junit.Assert.*;
import com.goodrunetracker.core.item.ItemKey;
import org.junit.Test;

public class LiveItemValuerTest {

    // Fake price source: every item id is worth its id number, in gp.
    private final ItemPriceSource pricesEqualId = itemId -> itemId;

    @Test
    public void pricesNormalItemsByGePriceTimesQuantity() {
        LiveItemValuer valuer = new LiveItemValuer(pricesEqualId, new PotionRegistry());
        // item 100 -> 100 gp each, x3 = 300
        assertEquals(300, valuer.value(ItemKey.item(100), 3));
    }

    @Test
    public void pricesPotionsPerDoseViaRegistry() {
        PotionRegistry registry = new PotionRegistry();
        registry.observe(1000, "Prayer potion(4)"); // rep id 1000, dose 4 -> price 1000/4 = 250/dose
        LiveItemValuer valuer = new LiveItemValuer(pricesEqualId, registry);
        assertEquals(1000, valuer.value(ItemKey.potion("Prayer potion"), 4)); // 250 * 4 doses
        assertEquals(250, valuer.unitValue(ItemKey.potion("Prayer potion")));
    }

    @Test
    public void unknownPotionFamilyValuesToZero() {
        LiveItemValuer valuer = new LiveItemValuer(pricesEqualId, new PotionRegistry());
        assertEquals(0, valuer.value(ItemKey.potion("Mystery brew"), 4));
    }
}
```

- [ ] **Step 6: Run it to verify it fails**

Run: `./gradlew test --tests com.goodrunetracker.adapter.LiveItemValuerTest`
Expected: FAIL — `LiveItemValuer` does not exist.

- [ ] **Step 7: Implement `LiveItemValuer`**

```java
package com.goodrunetracker.adapter;

import com.goodrunetracker.core.item.ItemKey;
import com.goodrunetracker.core.item.ItemValuer;

/** Values items at current GE prices; potions priced per dose via the registry. */
public final class LiveItemValuer implements ItemValuer {

    private final ItemPriceSource prices;
    private final PotionRegistry potions;

    public LiveItemValuer(ItemPriceSource prices, PotionRegistry potions) {
        this.prices = prices;
        this.potions = potions;
    }

    @Override
    public long value(ItemKey key, int quantity) {
        if (!key.isPotion()) {
            return (long) prices.price(key.itemId()) * quantity;
        }
        return potions.representativeFor(key.potionFamily())
                .map(rep -> (long) (prices.price(rep.itemId()) / rep.dose()) * quantity)
                .orElse(0L);
    }

    /** Value of one unit (or one dose, for potions) — used to capture prices at trip end. */
    public long unitValue(ItemKey key) {
        return value(key, 1);
    }
}
```

- [ ] **Step 8: Run both valuation tests to verify they pass**

Run: `./gradlew test --tests "com.goodrunetracker.adapter.FrozenItemValuerTest" --tests "com.goodrunetracker.adapter.LiveItemValuerTest"`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/ItemPriceSource.java src/main/java/com/goodrunetracker/adapter/FrozenItemValuer.java src/main/java/com/goodrunetracker/adapter/LiveItemValuer.java src/test/java/com/goodrunetracker/adapter/FrozenItemValuerTest.java src/test/java/com/goodrunetracker/adapter/LiveItemValuerTest.java
git commit -m "feat: live and frozen item valuers with per-dose potion pricing"
```

---

## Task 5: Persistence — DTOs, `SessionMapper`, `SessionStore`

**Files:**
- Create: `src/main/java/com/goodrunetracker/adapter/StoredTrip.java`
- Create: `src/main/java/com/goodrunetracker/adapter/StoredSession.java`
- Create: `src/main/java/com/goodrunetracker/adapter/SessionMapper.java`
- Create: `src/main/java/com/goodrunetracker/adapter/SessionStore.java`
- Test: `src/test/java/com/goodrunetracker/adapter/SessionMapperTest.java`
- Test: `src/test/java/com/goodrunetracker/adapter/SessionStoreTest.java`

- [ ] **Step 1: Create the DTOs**

`StoredTrip.java`:

```java
package com.goodrunetracker.adapter;

import java.util.Map;

/** Serializable form of a trip: quantities (string-keyed) plus captured unit prices. */
public final class StoredTrip {
    public String id;
    public long startMillis;
    public long endMillis;
    public boolean died;
    public Map<String, Integer> kills;
    public Map<String, Integer> dropped;
    public Map<String, Integer> pickedUp;
    public Map<String, Integer> missed;
    public Map<String, Integer> suppliesUsed;
    public Map<String, Long> xpGained;
    public Map<String, Long> unitPrices;
}
```

`StoredSession.java`:

```java
package com.goodrunetracker.adapter;

import java.util.List;

/** Serializable form of a session. */
public final class StoredSession {
    public String id;
    public String accountHash;
    public String category;
    public String name;
    public long startMillis;
    public long endMillis;
    public List<StoredTrip> trips;
}
```

- [ ] **Step 2: Write the failing `SessionMapper` test**

```java
package com.goodrunetracker.adapter;

import static org.junit.Assert.*;
import com.goodrunetracker.core.Trip;
import com.goodrunetracker.core.item.ItemKey;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class SessionMapperTest {

    private Trip sampleTrip() {
        Map<String, Integer> kills = new HashMap<>();
        kills.put("Demonic gorilla", 2);
        Map<ItemKey, Integer> dropped = new HashMap<>();
        dropped.put(ItemKey.item(560), 100);
        Map<ItemKey, Integer> pickedUp = new HashMap<>();
        pickedUp.put(ItemKey.item(560), 60);
        Map<ItemKey, Integer> missed = new HashMap<>();
        missed.put(ItemKey.item(560), 40);
        Map<ItemKey, Integer> supplies = new HashMap<>();
        supplies.put(ItemKey.potion("Prayer potion"), 3);
        Map<String, Long> xp = new HashMap<>();
        xp.put("RANGED", 5000L);
        return new Trip("t1", 0, 60_000, false, kills, dropped, pickedUp, missed, supplies, xp);
    }

    @Test
    public void encodesTripWithItemKeyTokens() {
        Map<ItemKey, Long> prices = new HashMap<>();
        prices.put(ItemKey.item(560), 5L);
        prices.put(ItemKey.potion("Prayer potion"), 250L);

        StoredTrip stored = SessionMapper.toStored(sampleTrip(), prices);

        assertEquals("t1", stored.id);
        assertEquals(Integer.valueOf(100), stored.dropped.get("item:560"));
        assertEquals(Integer.valueOf(3), stored.suppliesUsed.get("potion:Prayer potion"));
        assertEquals(Long.valueOf(250L), stored.unitPrices.get("potion:Prayer potion"));
    }

    @Test
    public void roundTripsBackToCoreTrip() {
        Map<ItemKey, Long> prices = new HashMap<>();
        prices.put(ItemKey.item(560), 5L);
        StoredTrip stored = SessionMapper.toStored(sampleTrip(), prices);

        Trip restored = SessionMapper.toTrip(stored);
        assertEquals(2, restored.totalKills());
        assertEquals(Integer.valueOf(60), restored.pickedUp().get(ItemKey.item(560)));
        assertEquals(Integer.valueOf(3), restored.suppliesUsed().get(ItemKey.potion("Prayer potion")));

        Map<ItemKey, Long> restoredPrices = SessionMapper.unitPrices(stored);
        assertEquals(Long.valueOf(5L), restoredPrices.get(ItemKey.item(560)));
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew test --tests com.goodrunetracker.adapter.SessionMapperTest`
Expected: FAIL — `SessionMapper` does not exist.

- [ ] **Step 4: Implement `SessionMapper`**

```java
package com.goodrunetracker.adapter;

import com.goodrunetracker.core.Trip;
import com.goodrunetracker.core.item.ItemKey;
import java.util.HashMap;
import java.util.Map;

/** Converts a core {@link Trip} (plus captured unit prices) to/from its stored form. */
public final class SessionMapper {

    private SessionMapper() {
    }

    public static StoredTrip toStored(Trip trip, Map<ItemKey, Long> unitPrices) {
        StoredTrip stored = new StoredTrip();
        stored.id = trip.id();
        stored.startMillis = trip.startMillis();
        stored.endMillis = trip.endMillis();
        stored.died = trip.died();
        stored.kills = new HashMap<>(trip.kills());
        stored.dropped = encode(trip.dropped());
        stored.pickedUp = encode(trip.pickedUp());
        stored.missed = encode(trip.missed());
        stored.suppliesUsed = encode(trip.suppliesUsed());
        stored.xpGained = new HashMap<>(trip.xpGained());
        stored.unitPrices = encodeLong(unitPrices);
        return stored;
    }

    public static Trip toTrip(StoredTrip stored) {
        return new Trip(stored.id, stored.startMillis, stored.endMillis, stored.died,
                new HashMap<>(stored.kills),
                decode(stored.dropped), decode(stored.pickedUp),
                decode(stored.missed), decode(stored.suppliesUsed),
                new HashMap<>(stored.xpGained));
    }

    public static Map<ItemKey, Long> unitPrices(StoredTrip stored) {
        Map<ItemKey, Long> out = new HashMap<>();
        stored.unitPrices.forEach((token, price) -> out.put(ItemKeyCodec.decode(token), price));
        return out;
    }

    private static Map<String, Integer> encode(Map<ItemKey, Integer> map) {
        Map<String, Integer> out = new HashMap<>();
        map.forEach((key, qty) -> out.put(ItemKeyCodec.encode(key), qty));
        return out;
    }

    private static Map<String, Long> encodeLong(Map<ItemKey, Long> map) {
        Map<String, Long> out = new HashMap<>();
        map.forEach((key, value) -> out.put(ItemKeyCodec.encode(key), value));
        return out;
    }

    private static Map<ItemKey, Integer> decode(Map<String, Integer> map) {
        Map<ItemKey, Integer> out = new HashMap<>();
        map.forEach((token, qty) -> out.put(ItemKeyCodec.decode(token), qty));
        return out;
    }
}
```

- [ ] **Step 5: Run the mapper test to verify it passes**

Run: `./gradlew test --tests com.goodrunetracker.adapter.SessionMapperTest`
Expected: PASS.

- [ ] **Step 6: Write the failing `SessionStore` test**

```java
package com.goodrunetracker.adapter;

import static org.junit.Assert.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.Test;

public class SessionStoreTest {

    private StoredSession sampleSession(String id, String accountHash) {
        StoredTrip trip = new StoredTrip();
        trip.id = "trip-1";
        trip.startMillis = 0;
        trip.endMillis = 60_000;
        trip.died = false;
        trip.kills = new HashMap<>();
        trip.kills.put("Demonic gorilla", 2);
        trip.dropped = new HashMap<>();
        trip.pickedUp = new HashMap<>();
        trip.missed = new HashMap<>();
        trip.suppliesUsed = new HashMap<>();
        trip.xpGained = new HashMap<>();
        trip.unitPrices = new HashMap<>();
        trip.dropped.put("item:560", 100);

        StoredSession session = new StoredSession();
        session.id = id;
        session.accountHash = accountHash;
        session.category = "Demonic Gorillas";
        session.name = "evening";
        session.startMillis = 0;
        session.endMillis = 60_000;
        session.trips = new ArrayList<>();
        session.trips.add(trip);
        return session;
    }

    @Test
    public void savesAndLoadsASessionPerAccount() throws Exception {
        Path root = Files.createTempDirectory("grt-store");
        SessionStore store = new SessionStore(root);

        store.save(sampleSession("s1", "acct-A"));

        List<StoredSession> loaded = store.load("acct-A");
        assertEquals(1, loaded.size());
        StoredSession s = loaded.get(0);
        assertEquals("s1", s.id);
        assertEquals("Demonic Gorillas", s.category);
        assertEquals(1, s.trips.size());
        assertEquals(Integer.valueOf(100), s.trips.get(0).dropped.get("item:560"));
    }

    @Test
    public void loadIsolatesByAccount() throws Exception {
        Path root = Files.createTempDirectory("grt-store");
        SessionStore store = new SessionStore(root);
        store.save(sampleSession("s1", "acct-A"));
        store.save(sampleSession("s2", "acct-B"));

        assertEquals(1, store.load("acct-A").size());
        assertEquals(1, store.load("acct-B").size());
    }

    @Test
    public void loadOfUnknownAccountIsEmpty() throws Exception {
        Path root = Files.createTempDirectory("grt-store");
        SessionStore store = new SessionStore(root);
        assertTrue(store.load("nobody").isEmpty());
    }
}
```

- [ ] **Step 7: Run it to verify it fails**

Run: `./gradlew test --tests com.goodrunetracker.adapter.SessionStoreTest`
Expected: FAIL — `SessionStore` does not exist.

- [ ] **Step 8: Implement `SessionStore`**

```java
package com.goodrunetracker.adapter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Reads/writes sessions as one JSON file per session under a per-account directory. */
public final class SessionStore {

    private final Path root;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public SessionStore(Path root) {
        this.root = root;
    }

    public void save(StoredSession session) {
        try {
            Path dir = root.resolve(session.accountHash);
            Files.createDirectories(dir);
            Path file = dir.resolve(session.id + ".json");
            Files.write(file, gson.toJson(session).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save session " + session.id, e);
        }
    }

    public List<StoredSession> load(String accountHash) {
        Path dir = root.resolve(accountHash);
        if (!Files.isDirectory(dir)) {
            return new ArrayList<>();
        }
        List<StoredSession> sessions = new ArrayList<>();
        try {
            List<Path> files;
            try (Stream<Path> stream = Files.list(dir)) {
                files = stream.filter(p -> p.toString().endsWith(".json"))
                        .collect(Collectors.toList());
            }
            for (Path file : files) {
                String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                sessions.add(gson.fromJson(json, StoredSession.class));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load sessions for " + accountHash, e);
        }
        return sessions;
    }
}
```

- [ ] **Step 9: Run the store test to verify it passes**

Run: `./gradlew test --tests com.goodrunetracker.adapter.SessionStoreTest`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/StoredTrip.java src/main/java/com/goodrunetracker/adapter/StoredSession.java src/main/java/com/goodrunetracker/adapter/SessionMapper.java src/main/java/com/goodrunetracker/adapter/SessionStore.java src/test/java/com/goodrunetracker/adapter/SessionMapperTest.java src/test/java/com/goodrunetracker/adapter/SessionStoreTest.java
git commit -m "feat: per-account JSON persistence with captured prices"
```

---

## Task 6: Collaborator interfaces, `TripSnapshot`, and `TrackingService` core lifecycle

**Files:**
- Create: `src/main/java/com/goodrunetracker/adapter/Clock.java`
- Create: `src/main/java/com/goodrunetracker/adapter/CarriedSnapshotSupplier.java`
- Create: `src/main/java/com/goodrunetracker/adapter/PanelView.java`
- Create: `src/main/java/com/goodrunetracker/adapter/TripSnapshot.java`
- Create: `src/main/java/com/goodrunetracker/adapter/TrackingService.java`
- Test: `src/test/java/com/goodrunetracker/adapter/TrackingServiceTest.java`

This task builds the lifecycle "happy path": start a session (which starts trip 1), feed inventory/kills/xp per tick, and end the session — persisting the result. Bank rollover, discard, and death are added in Task 7.

**Contract reminder:** `onTick()` is the only place `ledger.updateCarried(...)` is called; `markCarriedDirty()` just flags that inventory changed since the last tick.

- [ ] **Step 1: Create the collaborator interfaces and `TripSnapshot`**

`Clock.java`:

```java
package com.goodrunetracker.adapter;

/** Supplies the current time and fresh unique ids; faked in tests. */
public interface Clock {
    long nowMillis();

    String newId();
}
```

`CarriedSnapshotSupplier.java`:

```java
package com.goodrunetracker.adapter;

import java.util.Map;

/** Returns the current combined inventory+equipment as raw itemId -> quantity. */
public interface CarriedSnapshotSupplier {
    Map<Integer, Integer> currentCarried();
}
```

`PanelView.java`:

```java
package com.goodrunetracker.adapter;

/** The narrow view the tracking service drives; implemented by the Swing panel in Phase 2b. */
public interface PanelView {
    void refresh();

    void showDeathPrompt();
}
```

`TripSnapshot.java`:

```java
package com.goodrunetracker.adapter;

/** Immutable live readout of the in-progress trip, for the panel. */
public final class TripSnapshot {
    public final int tripNumber;
    public final long durationMillis;
    public final int kills;
    public final long pickedGp;
    public final long groundGp;
    public final long suppliesGp;
    public final long totalXp;
    public final long gpPerHour;

    public TripSnapshot(int tripNumber, long durationMillis, int kills, long pickedGp,
                        long groundGp, long suppliesGp, long totalXp, long gpPerHour) {
        this.tripNumber = tripNumber;
        this.durationMillis = durationMillis;
        this.kills = kills;
        this.pickedGp = pickedGp;
        this.groundGp = groundGp;
        this.suppliesGp = suppliesGp;
        this.totalXp = totalXp;
        this.gpPerHour = gpPerHour;
    }
}
```

- [ ] **Step 2: Write the failing `TrackingService` test**

```java
package com.goodrunetracker.adapter;

import static org.junit.Assert.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class TrackingServiceTest {

    // --- Test doubles ---

    static final class FakeClock implements Clock {
        long now = 0;
        int ids = 0;

        public long nowMillis() {
            return now;
        }

        public String newId() {
            return "id-" + (++ids);
        }
    }

    static final class FakeCarried implements CarriedSnapshotSupplier {
        Map<Integer, Integer> carried = new HashMap<>();

        public Map<Integer, Integer> currentCarried() {
            return new HashMap<>(carried);
        }
    }

    static final class FakePanel implements PanelView {
        int refreshes = 0;
        int deathPrompts = 0;

        public void refresh() {
            refreshes++;
        }

        public void showDeathPrompt() {
            deathPrompts++;
        }
    }

    // Every item id is worth 1gp; potions are ignored for these tests.
    private final ItemPriceSource oneGp = id -> 1;

    private TrackingService newService(FakeClock clock, FakeCarried carried, FakePanel panel,
                                       SessionStore store) {
        PotionRegistry potions = new PotionRegistry();
        LiveItemValuer valuer = new LiveItemValuer(oneGp, potions);
        // Item names: only matters for potions; return a non-dose name for everything.
        return new TrackingService(clock, carried, id -> "Item " + id, potions, valuer, store,
                panel, "acct-A");
    }

    @Test
    public void startSessionStartsATripAndBaselinesInventory() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        carried.carried.put(560, 50); // start carrying 50 of item 560
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"));
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession();
        assertTrue(service.isTracking());
        assertTrue(service.currentSnapshot().isPresent());
        assertEquals(0, service.currentSnapshot().get().kills);
    }

    @Test
    public void killThenTickPickupIsTracked() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"));
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession();           // trip 1, baseline empty inventory

        Map<Integer, Integer> drop = new HashMap<>();
        drop.put(560, 100);
        service.onKill("Demonic gorilla", drop);   // gorilla drops 100 of item 560

        carried.carried.put(560, 60);     // you pick up 60
        service.markCarriedDirty();
        clock.now = 30_000;
        service.onTick();

        TripSnapshot snap = service.currentSnapshot().get();
        assertEquals(1, snap.kills);
        assertEquals(60, snap.pickedGp);   // 60 items at 1gp
        assertEquals(40, snap.groundGp);   // 40 left on the ground
    }

    @Test
    public void endSessionPersistsAndDefaultsCategoryToFirstMonster() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        Path root = Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root);
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession();
        Map<Integer, Integer> drop = new HashMap<>();
        drop.put(560, 100);
        service.onKill("Demonic gorilla", drop);
        carried.carried.put(560, 100);
        service.markCarriedDirty();
        clock.now = 60_000;
        service.onTick();
        service.endSession();

        assertFalse(service.isTracking());
        List<StoredSession> saved = store.load("acct-A");
        assertEquals(1, saved.size());
        assertEquals("Demonic gorilla", saved.get(0).category);
        assertEquals(1, saved.get(0).trips.size());
        assertEquals(Integer.valueOf(100), saved.get(0).trips.get(0).pickedUp.get("item:560"));
    }

    @Test
    public void emptyTripIsNotPersisted() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        Path root = Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root);
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession();   // no kills, no supplies, no xp
        clock.now = 5_000;
        service.endSession();

        // Session had only an empty trip -> nothing worth persisting.
        assertTrue(store.load("acct-A").isEmpty());
    }

    @Test
    public void xpFirstObservationPrimesBaselineThenCounts() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"));
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession();
        service.onXp("RANGED", 100_000);   // prime: no gain counted
        service.onXp("RANGED", 100_500);   // +500
        service.onXp("RANGED", 100_800);   // +300
        assertEquals(800, service.currentSnapshot().get().totalXp);
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew test --tests com.goodrunetracker.adapter.TrackingServiceTest`
Expected: FAIL — `TrackingService` does not exist.

- [ ] **Step 4: Implement `TrackingService` (lifecycle core)**

```java
package com.goodrunetracker.adapter;

import com.goodrunetracker.core.Trip;
import com.goodrunetracker.core.TripLedger;
import com.goodrunetracker.core.item.CarriedNormalizer;
import com.goodrunetracker.core.item.ItemKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * Session/trip lifecycle state machine. RuneLite-bound collaborators are injected
 * as interfaces so this runs headless in tests. While a session is active there is
 * always exactly one active trip.
 */
public final class TrackingService {

    private static final long MILLIS_PER_HOUR = 3_600_000L;

    private final Clock clock;
    private final CarriedSnapshotSupplier carried;
    private final IntFunction<String> names;
    private final PotionRegistry potions;
    private final LiveItemValuer valuer;
    private final SessionStore store;
    private final PanelView panel;
    private final String accountHash;

    private StoredSession activeSession;
    private TripLedger ledger;
    private String tripId;
    private long tripStartMillis;
    private boolean tripDied;
    private boolean inventoryDirty;
    private boolean awaitingDeathChoice;
    private String firstKillNpc;
    private final Map<String, Long> lastXp = new HashMap<>();

    public TrackingService(Clock clock, CarriedSnapshotSupplier carried, IntFunction<String> names,
                           PotionRegistry potions, LiveItemValuer valuer, SessionStore store,
                           PanelView panel, String accountHash) {
        this.clock = clock;
        this.carried = carried;
        this.names = names;
        this.potions = potions;
        this.valuer = valuer;
        this.store = store;
        this.panel = panel;
        this.accountHash = accountHash;
    }

    public boolean isTracking() {
        return activeSession != null;
    }

    public void startSession() {
        if (activeSession != null) {
            return;
        }
        activeSession = new StoredSession();
        activeSession.id = clock.newId();
        activeSession.accountHash = accountHash;
        activeSession.category = null;
        activeSession.name = "";
        activeSession.startMillis = clock.nowMillis();
        activeSession.trips = new ArrayList<>();
        firstKillNpc = null;
        startTrip();
    }

    private void startTrip() {
        ledger = new TripLedger();
        tripId = clock.newId();
        tripStartMillis = clock.nowMillis();
        tripDied = false;
        inventoryDirty = false;
        ledger.updateCarried(normalize(carried.currentCarried()));
        panel.refresh();
    }

    public void markCarriedDirty() {
        inventoryDirty = true;
    }

    public void onTick() {
        if (ledger == null || awaitingDeathChoice) {
            return;
        }
        if (inventoryDirty) {
            ledger.updateCarried(normalize(carried.currentCarried()));
            inventoryDirty = false;
        }
        panel.refresh();
    }

    public void onKill(String npc, Map<Integer, Integer> rawDrops) {
        if (ledger == null || awaitingDeathChoice) {
            return;
        }
        if (firstKillNpc == null) {
            firstKillNpc = npc;
        }
        ledger.recordKill(npc, normalize(rawDrops));
    }

    public void onXp(String skill, long totalXp) {
        Long previous = lastXp.put(skill, totalXp);
        if (previous == null) {
            return; // first observation just primes the baseline
        }
        long delta = totalXp - previous;
        if (ledger != null && !awaitingDeathChoice && delta > 0) {
            ledger.recordXp(skill, delta);
        }
    }

    public Optional<TripSnapshot> currentSnapshot() {
        if (ledger == null) {
            return Optional.empty();
        }
        long now = clock.nowMillis();
        Trip trip = ledger.build(tripId, tripStartMillis, now, tripDied);
        long picked = trip.pickedUpValue(valuer);
        long ground = trip.missedValue(valuer);
        long supplies = trip.suppliesValue(valuer);
        long duration = now - tripStartMillis;
        long net = picked - supplies;
        long gpPerHour = duration <= 0 ? 0 : net * MILLIS_PER_HOUR / duration;
        int tripNumber = activeSession.trips.size() + 1;
        return Optional.of(new TripSnapshot(tripNumber, duration, trip.totalKills(),
                picked, ground, supplies, trip.totalXp(), gpPerHour));
    }

    public void endSession() {
        if (activeSession == null) {
            return;
        }
        if (ledger != null) {
            endTrip();
        }
        if (activeSession.category == null) {
            activeSession.category = firstKillNpc != null ? firstKillNpc : "Uncategorized";
        }
        activeSession.endMillis = clock.nowMillis();
        if (!activeSession.trips.isEmpty()) {
            store.save(activeSession);
        }
        activeSession = null;
        ledger = null;
        firstKillNpc = null;
        panel.refresh();
    }

    private void endTrip() {
        if (ledger == null) {
            return;
        }
        Trip trip = ledger.build(tripId, tripStartMillis, clock.nowMillis(), tripDied);
        ledger = null;
        if (trip.totalKills() == 0 && trip.suppliesUsed().isEmpty() && trip.totalXp() == 0) {
            return; // discard empty trip
        }
        Map<ItemKey, Long> unitPrices = captureUnitPrices(trip);
        activeSession.trips.add(SessionMapper.toStored(trip, unitPrices));
        activeSession.endMillis = trip.endMillis();
        store.save(activeSession);
    }

    private Map<ItemKey, Long> captureUnitPrices(Trip trip) {
        Map<ItemKey, Long> prices = new HashMap<>();
        for (ItemKey key : allKeys(trip)) {
            prices.put(key, valuer.unitValue(key));
        }
        return prices;
    }

    private static Set<ItemKey> allKeys(Trip trip) {
        Set<ItemKey> keys = new HashSet<>();
        keys.addAll(trip.dropped().keySet());
        keys.addAll(trip.pickedUp().keySet());
        keys.addAll(trip.missed().keySet());
        keys.addAll(trip.suppliesUsed().keySet());
        return keys;
    }

    private Map<ItemKey, Integer> normalize(Map<Integer, Integer> raw) {
        raw.forEach((id, qty) -> potions.observe(id, names.apply(id)));
        return CarriedNormalizer.normalize(raw, names);
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew test --tests com.goodrunetracker.adapter.TrackingServiceTest`
Expected: PASS (5 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/Clock.java src/main/java/com/goodrunetracker/adapter/CarriedSnapshotSupplier.java src/main/java/com/goodrunetracker/adapter/PanelView.java src/main/java/com/goodrunetracker/adapter/TripSnapshot.java src/main/java/com/goodrunetracker/adapter/TrackingService.java src/test/java/com/goodrunetracker/adapter/TrackingServiceTest.java
git commit -m "feat: TrackingService session/trip lifecycle core"
```

---

## Task 7: `TrackingService` — bank rollover, discard, and death

**Files:**
- Modify: `src/main/java/com/goodrunetracker/adapter/TrackingService.java`
- Test: `src/test/java/com/goodrunetracker/adapter/TrackingServiceDeathAndBankTest.java`

This task adds the remaining lifecycle transitions: a bank opening rolls one trip into the next, `discardTrip()` drops the in-progress trip, and death flags the trip and defers a keep/discard choice — critically, no tick is fed while awaiting that choice, so a death's inventory loss is never booked as supplies.

- [ ] **Step 1: Write the failing test**

```java
package com.goodrunetracker.adapter;

import static org.junit.Assert.*;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class TrackingServiceDeathAndBankTest {

    static final class FakeClock implements Clock {
        long now = 0;
        int ids = 0;

        public long nowMillis() {
            return now;
        }

        public String newId() {
            return "id-" + (++ids);
        }
    }

    static final class FakeCarried implements CarriedSnapshotSupplier {
        Map<Integer, Integer> carried = new HashMap<>();

        public Map<Integer, Integer> currentCarried() {
            return new HashMap<>(carried);
        }
    }

    static final class FakePanel implements PanelView {
        int deathPrompts = 0;

        public void refresh() {
        }

        public void showDeathPrompt() {
            deathPrompts++;
        }
    }

    private final ItemPriceSource oneGp = id -> 1;

    private TrackingService newService(FakeClock clock, FakeCarried carried, FakePanel panel,
                                       SessionStore store) {
        PotionRegistry potions = new PotionRegistry();
        return new TrackingService(clock, carried, id -> "Item " + id, potions,
                new LiveItemValuer(oneGp, potions), store, panel, "acct-A");
    }

    private void killOnce(TrackingService service, FakeCarried carried, FakeClock clock) {
        Map<Integer, Integer> drop = new HashMap<>();
        drop.put(560, 100);
        service.onKill("Demonic gorilla", drop);
        carried.carried.put(560, carried.carried.getOrDefault(560, 0) + 100);
        service.markCarriedDirty();
        clock.now += 10_000;
        service.onTick();
    }

    @Test
    public void bankOpeningRollsTripOverAndKeepsTracking() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"));
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession();
        killOnce(service, carried, clock);
        service.onBankOpened();          // ends trip 1, starts trip 2

        assertTrue(service.isTracking());
        assertEquals(2, service.currentSnapshot().get().tripNumber);
        service.endSession();
        assertEquals(1, store.load("acct-A").get(0).trips.size()); // only the non-empty trip 1
    }

    @Test
    public void discardTripDropsItButKeepsSession() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"));
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession();
        killOnce(service, carried, clock);
        service.discardTrip();           // throw away trip 1

        assertTrue(service.isTracking());
        assertEquals(1, service.currentSnapshot().get().tripNumber);
        service.endSession();
        assertTrue(store.load("acct-A").isEmpty());  // discarded trip never persisted
    }

    @Test
    public void deathPromptsAndPausesTickFeeding() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"));
        FakePanel panel = new FakePanel();
        TrackingService service = newService(clock, carried, panel, store);

        service.startSession();
        killOnce(service, carried, clock);          // 100 supplies-worth carried, 100 picked

        service.onLocalPlayerDeath();
        assertEquals(1, panel.deathPrompts);

        // Death dropped everything; a tick now must NOT book it as supplies.
        carried.carried.clear();
        service.markCarriedDirty();
        clock.now += 10_000;
        service.onTick();
        assertEquals(0, service.currentSnapshot().get().suppliesGp);

        service.resolveDeath(true);                 // keep the trip (marked died)
        service.endSession();
        StoredTrip saved = store.load("acct-A").get(0).trips.get(0);
        assertTrue(saved.died);
        assertTrue(saved.suppliesUsed.isEmpty());
    }

    @Test
    public void deathDiscardThrowsAwayTheDeadTrip() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"));
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession();
        killOnce(service, carried, clock);
        service.onLocalPlayerDeath();
        service.resolveDeath(false);                // discard
        assertTrue(service.isTracking());           // a fresh trip is running
        service.endSession();
        assertTrue(store.load("acct-A").isEmpty());
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew test --tests com.goodrunetracker.adapter.TrackingServiceDeathAndBankTest`
Expected: FAIL — `onBankOpened`, `discardTrip`, `onLocalPlayerDeath`, `resolveDeath` do not exist.

- [ ] **Step 3: Add the new methods to `TrackingService`**

Add these methods to `TrackingService` (place them after `onXp(...)`):

```java
    public void onBankOpened() {
        if (ledger == null || awaitingDeathChoice) {
            return;
        }
        endTrip();
        if (activeSession != null) {
            startTrip();
        }
    }

    public void discardTrip() {
        ledger = null;
        if (activeSession != null) {
            startTrip();
        }
    }

    public void onLocalPlayerDeath() {
        if (ledger == null || awaitingDeathChoice) {
            return;
        }
        tripDied = true;
        awaitingDeathChoice = true; // stops onTick from feeding the post-death snapshot
        panel.showDeathPrompt();
    }

    public void resolveDeath(boolean keep) {
        if (!awaitingDeathChoice) {
            return;
        }
        awaitingDeathChoice = false;
        if (keep) {
            endTrip();
            if (activeSession != null) {
                startTrip();
            }
        } else {
            discardTrip();
        }
    }
```

- [ ] **Step 4: Run the new test to verify it passes**

Run: `./gradlew test --tests com.goodrunetracker.adapter.TrackingServiceDeathAndBankTest`
Expected: PASS (4 tests).

- [ ] **Step 5: Run the full suite**

Run: `./gradlew test`
Expected: `BUILD SUCCESSFUL` — all Phase 1 and Phase 2a tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/TrackingService.java src/test/java/com/goodrunetracker/adapter/TrackingServiceDeathAndBankTest.java
git commit -m "feat: bank rollover, discard, and death handling in TrackingService"
```

---

## Phase 2a done — what comes next (Phase 2b, separate plan)

- **RuneLite-backed implementations:** `ItemPriceSource` via `ItemManager.getItemPrice`, `CarriedSnapshotSupplier` via `Client.getItemContainer(INVENTORY/EQUIPMENT)`, item-name lookup via `ItemManager.getItemComposition`, `Clock` via `System.currentTimeMillis` + `UUID`, and the account hash via `Client.getAccountHash`.
- **`GoodRuneTrackerPlugin`** (`@PluginDescriptor`) with `@Subscribe` handlers translating `GameTick`, `ItemContainerChanged`, `NpcLootReceived`, `StatChanged`, `WidgetLoaded` (bank), and `ActorDeath` into `TrackingService` calls.
- **`GoodRuneTrackerConfig`** and **`GoodRuneTrackerPanel`** (the minimal Swing panel implementing `PanelView`, with Start/Stop, End/Discard, live readout, and death keep/discard buttons).
- **`runelite-plugin.properties`** descriptor resource so the external plugin loads.
- Verified by a manual in-client checklist (the `@Subscribe` wiring and Swing cannot be meaningfully unit-tested).

Wiring captured prices into history/stats display (building a `FrozenItemValuer` per stored trip) lands in **Phase 3** with the tabbed UI.
