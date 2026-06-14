# Good Rune Tracker — Phase 1: Domain Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Gradle project scaffold and the pure-Java domain core (item modeling, the event-ordered tracking ledger, Trip/Session models, and per-category statistics), fully unit-tested, with zero RuneLite dependencies.

**Architecture:** A RuneLite external-plugin Gradle project whose `com.goodrunetracker.core.*` packages contain only plain Java. The core is fed already-normalized data by an adapter (built in Phase 2). All tricky logic — dose normalization, the consume-vs-loot overlap, missed-value matching, and averaging — lives here and is verified with JUnit.

**Tech Stack:** Java 11, Gradle, JUnit 4 (the version RuneLite uses). RuneLite `client` is declared as a `compileOnly` dependency for later phases but is not used by any Phase 1 code.

**Reference:** Design spec at `docs/superpowers/specs/2026-06-14-good-rune-tracker-design.md`.

---

## Core contracts (read before starting)

These types are defined across the tasks below; this is the at-a-glance reference so later tasks stay consistent.

- `ItemKey` — identifies a tracked quantity: either `ItemKey.item(int id)` or `ItemKey.potion(String family)`.
- `DoseForm` — `{ String family, int dose }`, the parse result for a name like `"Prayer potion(4)"`.
- `Doses.parse(String name) -> Optional<DoseForm>`.
- `CarriedNormalizer.normalize(Map<Integer,Integer> rawCombined, IntFunction<String> nameLookup) -> Map<ItemKey,Integer>` — collapses potion ids into `potion(family)` keyed by **total doses**.
- `ItemValuer` — `long value(ItemKey key, int quantity)`.
- `TripLedger` — accumulates `recordKill`, `updateCarried` (**called once per settled game tick**), `recordXp`, then `build(...)` → `Trip`.
- `Trip` — immutable result: ids, times, `died`, `kills`, `dropped`, `pickedUp`, `missed`, `suppliesUsed`, `xpGained`, plus value helpers taking an `ItemValuer`.
- `Session` — wraps an ordered `List<Trip>` with wall-clock span; editable `category`/`name`; rate helpers.
- `CategoryStats` / `CategoryAggregator` — aggregate `List<Session>` into per-category averages.

**Units:** item quantities `int`; gp values `long`; xp `long`; timestamps epoch-millis `long`.

---

## File Structure

- `build.gradle`, `settings.gradle`, `.gitignore`, gradle wrapper — project scaffold.
- `src/main/java/com/goodrunetracker/core/item/ItemKey.java`
- `src/main/java/com/goodrunetracker/core/item/DoseForm.java`
- `src/main/java/com/goodrunetracker/core/item/Doses.java`
- `src/main/java/com/goodrunetracker/core/item/CarriedNormalizer.java`
- `src/main/java/com/goodrunetracker/core/item/ItemValuer.java`
- `src/main/java/com/goodrunetracker/core/TripLedger.java`
- `src/main/java/com/goodrunetracker/core/Trip.java`
- `src/main/java/com/goodrunetracker/core/Session.java`
- `src/main/java/com/goodrunetracker/core/CategoryStats.java`
- `src/main/java/com/goodrunetracker/core/CategoryAggregator.java`
- Mirrored test files under `src/test/java/com/goodrunetracker/...`.

---

## Task 1: Project scaffold

**Files:**
- Create: `settings.gradle`
- Create: `build.gradle`
- Create: `.gitignore`
- Create: gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`)

- [ ] **Step 1: Create `settings.gradle`**

```groovy
rootProject.name = 'good-rune-tracker'
```

- [ ] **Step 2: Create `build.gradle`**

```groovy
plugins {
    id 'java'
}

group = 'com.goodrunetracker'
version = '1.0-SNAPSHOT'

repositories {
    mavenLocal()
    maven { url = 'https://repo.runelite.net' }
    mavenCentral()
}

def runeLiteVersion = 'latest.release'

dependencies {
    compileOnly group: 'net.runelite', name: 'client', version: runeLiteVersion

    testImplementation group: 'net.runelite', name: 'client', version: runeLiteVersion
    testImplementation 'junit:junit:4.13.2'
}

// Compile to Java 11 bytecode (Plugin-Hub compatible) even on a newer JDK.
tasks.withType(JavaCompile) {
    options.encoding = 'UTF-8'
    options.release = 11
}

test {
    useJUnit()
}
```

Note: Lombok is intentionally omitted in Phase 1 (the domain core does not use
it). It will be added in Phase 2 — at a version that supports the installed JDK —
when the RuneLite adapter classes need it.

- [ ] **Step 3: Create `.gitignore`**

```gitignore
.gradle/
build/
*.iml
.idea/
.superpowers/
```

(`.superpowers/` may already be present from brainstorming — keep a single entry.)

- [ ] **Step 4: Generate the Gradle wrapper**

Run: `gradle wrapper --gradle-version 9.3.1`
(Uses the installed Gradle, which runs on the system JDK 24/25; Gradle 9.x is
required on that JVM. If `gradle` is missing: `brew install gradle` first.)
Expected: creates `gradlew`, `gradlew.bat`, and `gradle/wrapper/`.

- [ ] **Step 5: Verify the build compiles**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL` (no sources or tests yet; downloads the Gradle 9.3.1
distribution and the RuneLite client dependency).

- [ ] **Step 6: Commit**

```bash
git add settings.gradle build.gradle .gitignore gradlew gradlew.bat gradle
git commit -m "chore: scaffold RuneLite plugin Gradle project"
```

---

## Task 2: `ItemKey`

**Files:**
- Create: `src/main/java/com/goodrunetracker/core/item/ItemKey.java`
- Test: `src/test/java/com/goodrunetracker/core/item/ItemKeyTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.goodrunetracker.core.item;

import static org.junit.Assert.*;
import org.junit.Test;

public class ItemKeyTest {

    @Test
    public void itemKeysWithSameIdAreEqual() {
        assertEquals(ItemKey.item(995), ItemKey.item(995));
        assertEquals(ItemKey.item(995).hashCode(), ItemKey.item(995).hashCode());
    }

    @Test
    public void itemAndPotionKeysAreNeverEqual() {
        assertNotEquals(ItemKey.item(995), ItemKey.potion("Prayer potion"));
    }

    @Test
    public void exposesItsKind() {
        assertTrue(ItemKey.potion("Prayer potion").isPotion());
        assertFalse(ItemKey.item(995).isPotion());
        assertEquals(995, ItemKey.item(995).itemId());
        assertEquals("Prayer potion", ItemKey.potion("Prayer potion").potionFamily());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.goodrunetracker.core.item.ItemKeyTest`
Expected: FAIL — `ItemKey` does not exist (compilation error).

- [ ] **Step 3: Write minimal implementation**

```java
package com.goodrunetracker.core.item;

import java.util.Objects;

/** Identifies a tracked quantity: either a normal item (by id) or a potion family (by name). */
public final class ItemKey {

    private final int itemId;          // -1 for potion-family keys
    private final String potionFamily; // null for normal-item keys

    private ItemKey(int itemId, String potionFamily) {
        this.itemId = itemId;
        this.potionFamily = potionFamily;
    }

    public static ItemKey item(int itemId) {
        return new ItemKey(itemId, null);
    }

    public static ItemKey potion(String family) {
        return new ItemKey(-1, Objects.requireNonNull(family));
    }

    public boolean isPotion() {
        return potionFamily != null;
    }

    public int itemId() {
        return itemId;
    }

    public String potionFamily() {
        return potionFamily;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ItemKey)) {
            return false;
        }
        ItemKey other = (ItemKey) o;
        return itemId == other.itemId && Objects.equals(potionFamily, other.potionFamily);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId, potionFamily);
    }

    @Override
    public String toString() {
        return isPotion() ? "potion:" + potionFamily : "item:" + itemId;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.goodrunetracker.core.item.ItemKeyTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/goodrunetracker/core/item/ItemKey.java src/test/java/com/goodrunetracker/core/item/ItemKeyTest.java
git commit -m "feat: add ItemKey value type"
```

---

## Task 3: `Doses` and `DoseForm`

**Files:**
- Create: `src/main/java/com/goodrunetracker/core/item/DoseForm.java`
- Create: `src/main/java/com/goodrunetracker/core/item/Doses.java`
- Test: `src/test/java/com/goodrunetracker/core/item/DosesTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.goodrunetracker.core.item;

import static org.junit.Assert.*;
import java.util.Optional;
import org.junit.Test;

public class DosesTest {

    @Test
    public void parsesPotionDoseAndFamily() {
        Optional<DoseForm> form = Doses.parse("Prayer potion(4)");
        assertTrue(form.isPresent());
        assertEquals("Prayer potion", form.get().family());
        assertEquals(4, form.get().dose());
    }

    @Test
    public void parsesSingleDigitDoses() {
        assertEquals(2, Doses.parse("Saradomin brew(2)").get().dose());
    }

    @Test
    public void nonDosedItemsReturnEmpty() {
        assertFalse(Doses.parse("Abyssal whip").isPresent());
        assertFalse(Doses.parse("Clue scroll (easy)").isPresent());
    }

    @Test
    public void nullNameReturnsEmpty() {
        assertFalse(Doses.parse(null).isPresent());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.goodrunetracker.core.item.DosesTest`
Expected: FAIL — `Doses`/`DoseForm` do not exist.

- [ ] **Step 3: Write minimal implementation**

`DoseForm.java`:

```java
package com.goodrunetracker.core.item;

import java.util.Objects;

/** The parsed dose/charge information from an item name, e.g. {"Prayer potion", 4}. */
public final class DoseForm {

    private final String family;
    private final int dose;

    public DoseForm(String family, int dose) {
        this.family = family;
        this.dose = dose;
    }

    public String family() {
        return family;
    }

    public int dose() {
        return dose;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DoseForm)) {
            return false;
        }
        DoseForm other = (DoseForm) o;
        return dose == other.dose && Objects.equals(family, other.family);
    }

    @Override
    public int hashCode() {
        return Objects.hash(family, dose);
    }
}
```

`Doses.java`:

```java
package com.goodrunetracker.core.item;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses dose/charge information from item names like "Prayer potion(4)".
 * Matches a trailing "(<digits>)" suffix, which in OSRS denotes potion doses
 * (and, accepted for v1, charged jewellery like "Games necklace(8)").
 */
public final class Doses {

    private static final Pattern DOSE = Pattern.compile("^(.*)\\((\\d+)\\)$");

    private Doses() {
    }

    public static Optional<DoseForm> parse(String name) {
        if (name == null) {
            return Optional.empty();
        }
        Matcher matcher = DOSE.matcher(name.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String family = matcher.group(1).trim();
        int dose = Integer.parseInt(matcher.group(2));
        if (family.isEmpty() || dose <= 0) {
            return Optional.empty();
        }
        return Optional.of(new DoseForm(family, dose));
    }
}
```

Note: the `"Clue scroll (easy)"` test passes because `(easy)` is not `(\d+)`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.goodrunetracker.core.item.DosesTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/goodrunetracker/core/item/DoseForm.java src/main/java/com/goodrunetracker/core/item/Doses.java src/test/java/com/goodrunetracker/core/item/DosesTest.java
git commit -m "feat: parse dose forms from item names"
```

---

## Task 4: `CarriedNormalizer`

**Files:**
- Create: `src/main/java/com/goodrunetracker/core/item/CarriedNormalizer.java`
- Test: `src/test/java/com/goodrunetracker/core/item/CarriedNormalizerTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.goodrunetracker.core.item;

import static org.junit.Assert.*;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntFunction;
import org.junit.Test;

public class CarriedNormalizerTest {

    private final IntFunction<String> names = id -> {
        switch (id) {
            case 2434: return "Prayer potion(4)";
            case 139:  return "Prayer potion(3)";
            case 995:  return "Coins";
            default:   return "Unknown";
        }
    };

    @Test
    public void nonPotionsKeepTheirItemId() {
        Map<Integer, Integer> raw = new HashMap<>();
        raw.put(995, 100);
        Map<ItemKey, Integer> result = CarriedNormalizer.normalize(raw, names);
        assertEquals(Integer.valueOf(100), result.get(ItemKey.item(995)));
    }

    @Test
    public void potionsCollapseToFamilyTotalDoses() {
        Map<Integer, Integer> raw = new HashMap<>();
        raw.put(2434, 2); // 2 x Prayer potion(4) = 8 doses
        raw.put(139, 1);  // 1 x Prayer potion(3) = 3 doses
        Map<ItemKey, Integer> result = CarriedNormalizer.normalize(raw, names);
        assertEquals(Integer.valueOf(11), result.get(ItemKey.potion("Prayer potion")));
        assertFalse(result.containsKey(ItemKey.item(2434)));
    }

    @Test
    public void zeroOrNegativeQuantitiesAreDropped() {
        Map<Integer, Integer> raw = new HashMap<>();
        raw.put(995, 0);
        assertTrue(CarriedNormalizer.normalize(raw, names).isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.goodrunetracker.core.item.CarriedNormalizerTest`
Expected: FAIL — `CarriedNormalizer` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.goodrunetracker.core.item;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntFunction;

/**
 * Converts a raw combined inventory+equipment snapshot (itemId -> quantity)
 * into normalized {@link ItemKey} quantities, where potion ids collapse into a
 * single per-family key valued in total doses.
 */
public final class CarriedNormalizer {

    private CarriedNormalizer() {
    }

    public static Map<ItemKey, Integer> normalize(Map<Integer, Integer> rawCombined,
                                                   IntFunction<String> nameLookup) {
        Map<ItemKey, Integer> out = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : rawCombined.entrySet()) {
            int id = entry.getKey();
            int qty = entry.getValue();
            if (qty <= 0) {
                continue;
            }
            Optional<DoseForm> form = Doses.parse(nameLookup.apply(id));
            if (form.isPresent()) {
                out.merge(ItemKey.potion(form.get().family()), form.get().dose() * qty, Integer::sum);
            } else {
                out.merge(ItemKey.item(id), qty, Integer::sum);
            }
        }
        return out;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.goodrunetracker.core.item.CarriedNormalizerTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/goodrunetracker/core/item/CarriedNormalizer.java src/test/java/com/goodrunetracker/core/item/CarriedNormalizerTest.java
git commit -m "feat: normalize raw snapshots into dose-aware ItemKey quantities"
```

---

## Task 5: `ItemValuer` interface

**Files:**
- Create: `src/main/java/com/goodrunetracker/core/item/ItemValuer.java`

This is a one-method interface implemented by the Phase 2 adapter (via `ItemManager`) and by test fakes. No test of its own — it is exercised by Tasks 6–8.

- [ ] **Step 1: Create the interface**

```java
package com.goodrunetracker.core.item;

/** Returns the gp value of a quantity of an {@link ItemKey}. Implemented outside the core. */
public interface ItemValuer {
    long value(ItemKey key, int quantity);
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/goodrunetracker/core/item/ItemValuer.java
git commit -m "feat: add ItemValuer interface"
```

---

## Task 6: `TripLedger` and `Trip`

**Files:**
- Create: `src/main/java/com/goodrunetracker/core/Trip.java`
- Create: `src/main/java/com/goodrunetracker/core/TripLedger.java`
- Test: `src/test/java/com/goodrunetracker/core/TripLedgerTest.java`

**Key contract:** `updateCarried` must be called **once per settled game tick** with the combined inventory+equipment quantities (already normalized via `CarriedNormalizer`). Per-tick settling is what lets equipping gear net to zero while a real consumption shows as a decrease. Within `updateCarried`, a positive delta is reconciled against the still-on-the-ground pool (a pickup of kill loot, else an untracked generic gain); a negative delta is recorded as supplies used.

- [ ] **Step 1: Write the `Trip` result type**

```java
package com.goodrunetracker.core;

import com.goodrunetracker.core.item.ItemKey;
import com.goodrunetracker.core.item.ItemValuer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Immutable record of a single completed trip. Quantities only; gp is derived via an ItemValuer. */
public final class Trip {

    private final String id;
    private final long startMillis;
    private final long endMillis;
    private final boolean died;
    private final Map<String, Integer> kills;
    private final Map<ItemKey, Integer> dropped;
    private final Map<ItemKey, Integer> pickedUp;
    private final Map<ItemKey, Integer> missed;
    private final Map<ItemKey, Integer> suppliesUsed;
    private final Map<String, Long> xpGained;

    public Trip(String id, long startMillis, long endMillis, boolean died,
                Map<String, Integer> kills, Map<ItemKey, Integer> dropped,
                Map<ItemKey, Integer> pickedUp, Map<ItemKey, Integer> missed,
                Map<ItemKey, Integer> suppliesUsed, Map<String, Long> xpGained) {
        this.id = id;
        this.startMillis = startMillis;
        this.endMillis = endMillis;
        this.died = died;
        this.kills = new HashMap<>(kills);
        this.dropped = new HashMap<>(dropped);
        this.pickedUp = new HashMap<>(pickedUp);
        this.missed = new HashMap<>(missed);
        this.suppliesUsed = new HashMap<>(suppliesUsed);
        this.xpGained = new HashMap<>(xpGained);
    }

    public String id() {
        return id;
    }

    public long startMillis() {
        return startMillis;
    }

    public long endMillis() {
        return endMillis;
    }

    public long durationMillis() {
        return endMillis - startMillis;
    }

    public boolean died() {
        return died;
    }

    public Map<String, Integer> kills() {
        return Collections.unmodifiableMap(kills);
    }

    public Map<ItemKey, Integer> dropped() {
        return Collections.unmodifiableMap(dropped);
    }

    public Map<ItemKey, Integer> pickedUp() {
        return Collections.unmodifiableMap(pickedUp);
    }

    public Map<ItemKey, Integer> missed() {
        return Collections.unmodifiableMap(missed);
    }

    public Map<ItemKey, Integer> suppliesUsed() {
        return Collections.unmodifiableMap(suppliesUsed);
    }

    public Map<String, Long> xpGained() {
        return Collections.unmodifiableMap(xpGained);
    }

    public int totalKills() {
        int sum = 0;
        for (int c : kills.values()) {
            sum += c;
        }
        return sum;
    }

    public long totalXp() {
        long sum = 0;
        for (long v : xpGained.values()) {
            sum += v;
        }
        return sum;
    }

    public long pickedUpValue(ItemValuer valuer) {
        return value(pickedUp, valuer);
    }

    public long suppliesValue(ItemValuer valuer) {
        return value(suppliesUsed, valuer);
    }

    public long missedValue(ItemValuer valuer) {
        return value(missed, valuer);
    }

    public long netProfit(ItemValuer valuer) {
        return pickedUpValue(valuer) - suppliesValue(valuer);
    }

    private static long value(Map<ItemKey, Integer> items, ItemValuer valuer) {
        long sum = 0;
        for (Map.Entry<ItemKey, Integer> e : items.entrySet()) {
            sum += valuer.value(e.getKey(), e.getValue());
        }
        return sum;
    }
}
```

- [ ] **Step 2: Write the failing `TripLedger` test**

```java
package com.goodrunetracker.core;

import static org.junit.Assert.*;
import com.goodrunetracker.core.item.ItemKey;
import com.goodrunetracker.core.item.ItemValuer;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class TripLedgerTest {

    private static Map<ItemKey, Integer> carried(Object... pairs) {
        Map<ItemKey, Integer> m = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((ItemKey) pairs[i], (Integer) pairs[i + 1]);
        }
        return m;
    }

    // Values 1gp per unit of everything, so value == quantity. Keeps assertions simple.
    private final ItemValuer oneGp = (key, qty) -> qty;

    @Test
    public void countsKillsAndDroppedLoot() {
        TripLedger ledger = new TripLedger();
        ledger.recordKill("Demonic gorilla", carried(ItemKey.item(560), 100));
        ledger.recordKill("Demonic gorilla", carried(ItemKey.item(560), 50));
        Trip trip = ledger.build("t1", 0, 60_000, false);
        assertEquals(2, trip.totalKills());
        assertEquals(Integer.valueOf(150), trip.dropped().get(ItemKey.item(560)));
    }

    @Test
    public void pickedUpDrainsGroundPoolAndLeavesMissed() {
        TripLedger ledger = new TripLedger();
        ledger.updateCarried(carried()); // tick 0: empty start
        ledger.recordKill("Demonic gorilla", carried(ItemKey.item(560), 100));
        ledger.updateCarried(carried(ItemKey.item(560), 60)); // picked up 60 of the 100
        Trip trip = ledger.build("t1", 0, 60_000, false);
        assertEquals(Integer.valueOf(60), trip.pickedUp().get(ItemKey.item(560)));
        assertEquals(Integer.valueOf(40), trip.missed().get(ItemKey.item(560)));
    }

    @Test
    public void netDecreaseIsCountedAsSuppliesUsed() {
        TripLedger ledger = new TripLedger();
        ledger.updateCarried(carried(ItemKey.item(385), 4)); // start: 4 sharks
        ledger.updateCarried(carried(ItemKey.item(385), 1)); // ate 3
        Trip trip = ledger.build("t1", 0, 60_000, false);
        assertEquals(Integer.valueOf(3), trip.suppliesUsed().get(ItemKey.item(385)));
    }

    @Test
    public void overlappingLootAndSupplyAreBothCounted() {
        // Drink one of your own prayer doses on one tick, loot a (4) on a later tick.
        ItemKey prayer = ItemKey.potion("Prayer potion");
        TripLedger ledger = new TripLedger();
        ledger.updateCarried(carried(prayer, 12)); // start: 3 x (4) = 12 doses
        ledger.updateCarried(carried(prayer, 11)); // drank 1 dose -> supplies +1
        ledger.recordKill("Demonic gorilla", carried(prayer, 4)); // drop a (4)
        ledger.updateCarried(carried(prayer, 15)); // picked up 4 doses
        Trip trip = ledger.build("t1", 0, 60_000, false);
        assertEquals(Integer.valueOf(1), trip.suppliesUsed().get(prayer));
        assertEquals(Integer.valueOf(4), trip.pickedUp().get(prayer));
        assertNull(trip.missed().get(prayer));
    }

    @Test
    public void equipSettledWithinTickIsNeitherSupplyNorGain() {
        // Combined inventory+equipment is unchanged across settled ticks when equipping.
        TripLedger ledger = new TripLedger();
        ledger.updateCarried(carried(ItemKey.item(4151), 1)); // tick 0: whip carried
        ledger.updateCarried(carried(ItemKey.item(4151), 1)); // tick 1: still carried (now equipped)
        Trip trip = ledger.build("t1", 0, 60_000, false);
        assertTrue(trip.suppliesUsed().isEmpty());
    }

    @Test
    public void accumulatesXpPerSkill() {
        TripLedger ledger = new TripLedger();
        ledger.recordXp("RANGED", 5000);
        ledger.recordXp("RANGED", 1500);
        ledger.recordXp("HITPOINTS", 2000);
        Trip trip = ledger.build("t1", 0, 60_000, false);
        assertEquals(Long.valueOf(6500), trip.xpGained().get("RANGED"));
        assertEquals(8500, trip.totalXp());
    }

    @Test
    public void valueHelpersUseTheValuer() {
        TripLedger ledger = new TripLedger();
        ledger.updateCarried(carried(ItemKey.item(560), 0));
        ledger.recordKill("x", carried(ItemKey.item(560), 100));
        ledger.updateCarried(carried(ItemKey.item(560), 100)); // picked all up
        ledger.updateCarried(carried(ItemKey.item(560), 80));  // used 20
        Trip trip = ledger.build("t1", 0, 60_000, false);
        assertEquals(100, trip.pickedUpValue(oneGp));
        assertEquals(20, trip.suppliesValue(oneGp));
        assertEquals(80, trip.netProfit(oneGp));
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew test --tests com.goodrunetracker.core.TripLedgerTest`
Expected: FAIL — `TripLedger` does not exist.

- [ ] **Step 4: Write minimal `TripLedger` implementation**

```java
package com.goodrunetracker.core;

import com.goodrunetracker.core.item.ItemKey;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Event-ordered tracking ledger for a single in-progress trip.
 *
 * <p>Contract: {@link #updateCarried(Map)} must be called once per settled game
 * tick with the combined, dose-normalized inventory+equipment quantities. A
 * positive per-tick delta is reconciled against the still-on-the-ground pool
 * (kill loot picked up, else an untracked generic gain); a negative delta is
 * recorded as supplies used.
 */
public final class TripLedger {

    private final Map<String, Integer> kills = new HashMap<>();
    private final Map<ItemKey, Integer> dropped = new HashMap<>();
    private final Map<ItemKey, Integer> groundPool = new HashMap<>();
    private final Map<ItemKey, Integer> pickedUp = new HashMap<>();
    private final Map<ItemKey, Integer> suppliesUsed = new HashMap<>();
    private final Map<String, Long> xp = new HashMap<>();

    private Map<ItemKey, Integer> carried = null;

    public void recordKill(String npcName, Map<ItemKey, Integer> drops) {
        kills.merge(npcName, 1, Integer::sum);
        for (Map.Entry<ItemKey, Integer> e : drops.entrySet()) {
            dropped.merge(e.getKey(), e.getValue(), Integer::sum);
            groundPool.merge(e.getKey(), e.getValue(), Integer::sum);
        }
    }

    public void updateCarried(Map<ItemKey, Integer> settledCarried) {
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
                suppliesUsed.merge(key, -delta, Integer::sum);
            }
        }
        carried = new HashMap<>(settledCarried);
    }

    private void reconcilePickup(ItemKey key, int gained) {
        int onGround = groundPool.getOrDefault(key, 0);
        int fromGround = Math.min(gained, onGround);
        if (fromGround <= 0) {
            return; // untracked generic gain
        }
        pickedUp.merge(key, fromGround, Integer::sum);
        int remaining = onGround - fromGround;
        if (remaining == 0) {
            groundPool.remove(key);
        } else {
            groundPool.put(key, remaining);
        }
    }

    public void recordXp(String skill, long delta) {
        if (delta > 0) {
            xp.merge(skill, delta, Long::sum);
        }
    }

    public Trip build(String id, long startMillis, long endMillis, boolean died) {
        Map<ItemKey, Integer> missed = new HashMap<>();
        for (Map.Entry<ItemKey, Integer> e : dropped.entrySet()) {
            int remaining = e.getValue() - pickedUp.getOrDefault(e.getKey(), 0);
            if (remaining > 0) {
                missed.put(e.getKey(), remaining);
            }
        }
        return new Trip(id, startMillis, endMillis, died,
                kills, dropped, pickedUp, missed, suppliesUsed, xp);
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests com.goodrunetracker.core.TripLedgerTest`
Expected: PASS (all 7 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/goodrunetracker/core/Trip.java src/main/java/com/goodrunetracker/core/TripLedger.java src/test/java/com/goodrunetracker/core/TripLedgerTest.java
git commit -m "feat: event-ordered trip ledger with overlap and dose handling"
```

---

## Task 7: `Session`

**Files:**
- Create: `src/main/java/com/goodrunetracker/core/Session.java`
- Test: `src/test/java/com/goodrunetracker/core/SessionTest.java`

A session wraps an ordered list of trips plus a wall-clock span (first trip start to last trip end, including banking gaps). `category` and `name` are editable. Per the spec, session GP/hr and XP/hr use the wall-clock span.

- [ ] **Step 1: Write the failing test**

```java
package com.goodrunetracker.core;

import static org.junit.Assert.*;
import com.goodrunetracker.core.item.ItemKey;
import com.goodrunetracker.core.item.ItemValuer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class SessionTest {

    private final ItemValuer oneGp = (key, qty) -> qty;

    private Trip tripWithProfitAndXp(String id, long start, long end, int net, long xp) {
        Map<ItemKey, Integer> picked = new HashMap<>();
        picked.put(ItemKey.item(995), net); // net gp == net at 1gp each, no supplies
        Map<String, Long> xpMap = new HashMap<>();
        xpMap.put("RANGED", xp);
        return new Trip(id, start, end, false, new HashMap<>(), new HashMap<>(),
                picked, new HashMap<>(), new HashMap<>(), xpMap);
    }

    @Test
    public void wallClockSpansFirstStartToLastEnd() {
        Trip a = tripWithProfitAndXp("a", 0, 600_000, 100, 1000);
        Trip b = tripWithProfitAndXp("b", 900_000, 1_800_000, 200, 2000);
        Session session = new Session("s1", "acct", "Vorkath", "evening", Arrays.asList(a, b));
        assertEquals(1_800_000, session.wallClockMillis()); // includes the 5-min bank gap
    }

    @Test
    public void gpPerHourUsesWallClockIncludingGaps() {
        Trip a = tripWithProfitAndXp("a", 0, 600_000, 100, 0);
        Trip b = tripWithProfitAndXp("b", 900_000, 1_800_000, 200, 0);
        Session session = new Session("s1", "acct", "Vorkath", "evening", Arrays.asList(a, b));
        // 300 gp over half an hour -> 600 gp/hr
        assertEquals(600, session.gpPerHour(oneGp));
    }

    @Test
    public void xpPerHourUsesWallClock() {
        Trip a = tripWithProfitAndXp("a", 0, 1_800_000, 0, 30_000);
        Session session = new Session("s1", "acct", "Vorkath", "evening", Arrays.asList(a));
        assertEquals(60_000, session.xpPerHour()); // 30k xp over 30 min
    }

    @Test
    public void categoryAndNameAreEditable() {
        Session session = new Session("s1", "acct", "Vorkath", "evening",
                Arrays.asList(tripWithProfitAndXp("a", 0, 1000, 0, 0)));
        session.setCategory("Zulrah");
        session.setName("alt grind");
        assertEquals("Zulrah", session.category());
        assertEquals("alt grind", session.name());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.goodrunetracker.core.SessionTest`
Expected: FAIL — `Session` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.goodrunetracker.core;

import com.goodrunetracker.core.item.ItemValuer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** An ordered collection of trips for one activity, plus its editable labels. */
public final class Session {

    private static final long MILLIS_PER_HOUR = 3_600_000L;

    private final String id;
    private final String accountHash;
    private String category;
    private String name;
    private final List<Trip> trips;

    public Session(String id, String accountHash, String category, String name, List<Trip> trips) {
        this.id = id;
        this.accountHash = accountHash;
        this.category = category;
        this.name = name;
        this.trips = new ArrayList<>(trips);
    }

    public String id() {
        return id;
    }

    public String accountHash() {
        return accountHash;
    }

    public String category() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Trip> trips() {
        return Collections.unmodifiableList(trips);
    }

    public long wallClockMillis() {
        if (trips.isEmpty()) {
            return 0;
        }
        long first = Long.MAX_VALUE;
        long last = Long.MIN_VALUE;
        for (Trip t : trips) {
            first = Math.min(first, t.startMillis());
            last = Math.max(last, t.endMillis());
        }
        return last - first;
    }

    public long totalNetProfit(ItemValuer valuer) {
        long sum = 0;
        for (Trip t : trips) {
            sum += t.netProfit(valuer);
        }
        return sum;
    }

    public long totalXp() {
        long sum = 0;
        for (Trip t : trips) {
            sum += t.totalXp();
        }
        return sum;
    }

    public long gpPerHour(ItemValuer valuer) {
        long ms = wallClockMillis();
        return ms <= 0 ? 0 : totalNetProfit(valuer) * MILLIS_PER_HOUR / ms;
    }

    public long xpPerHour() {
        long ms = wallClockMillis();
        return ms <= 0 ? 0 : totalXp() * MILLIS_PER_HOUR / ms;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.goodrunetracker.core.SessionTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/goodrunetracker/core/Session.java src/test/java/com/goodrunetracker/core/SessionTest.java
git commit -m "feat: add Session with wall-clock rates"
```

---

## Task 8: `CategoryStats` and `CategoryAggregator`

**Files:**
- Create: `src/main/java/com/goodrunetracker/core/CategoryStats.java`
- Create: `src/main/java/com/goodrunetracker/core/CategoryAggregator.java`
- Test: `src/test/java/com/goodrunetracker/core/CategoryAggregatorTest.java`

`CategoryStats.from(...)` computes time-weighted GP/hr and XP/hr (total net gp / total wall-clock across sessions) plus per-trip averages. `CategoryAggregator.aggregate(...)` groups a flat list of sessions by category.

- [ ] **Step 1: Write the failing test**

```java
package com.goodrunetracker.core;

import static org.junit.Assert.*;
import com.goodrunetracker.core.item.ItemKey;
import com.goodrunetracker.core.item.ItemValuer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class CategoryAggregatorTest {

    private final ItemValuer oneGp = (key, qty) -> qty;

    private Trip trip(String id, long start, long end, int net, int kills) {
        Map<ItemKey, Integer> picked = new HashMap<>();
        picked.put(ItemKey.item(995), net);
        Map<String, Integer> killMap = new HashMap<>();
        killMap.put("Demonic gorilla", kills);
        return new Trip(id, start, end, false, killMap, new HashMap<>(),
                picked, new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    private Session session(String id, String category, Trip... trips) {
        return new Session(id, "acct", category, "", Arrays.asList(trips));
    }

    @Test
    public void aggregatesPerTripAveragesAndTimeWeightedRate() {
        Session s1 = session("s1", "Demonic Gorillas",
                trip("a", 0, 1_800_000, 100, 20),
                trip("b", 1_800_000, 3_600_000, 300, 30));
        CategoryStats stats = CategoryStats.from("Demonic Gorillas", Arrays.asList(s1), oneGp);

        assertEquals(1, stats.sessionCount());
        assertEquals(2, stats.tripCount());
        // 400 gp over 1 hour wall-clock
        assertEquals(400, stats.gpPerHour());
        // avg net per trip = (100 + 300) / 2
        assertEquals(200, stats.avgNetProfitPerTrip());
        // avg kills per trip = (20 + 30) / 2
        assertEquals(25.0, stats.avgKillsPerTrip(), 0.0001);
    }

    @Test
    public void groupsSessionsByCategory() {
        Session a = session("s1", "Vorkath", trip("a", 0, 3_600_000, 1000, 5));
        Session b = session("s2", "Zulrah", trip("b", 0, 3_600_000, 2000, 1));
        Map<String, CategoryStats> grouped =
                CategoryAggregator.aggregate(Arrays.asList(a, b), oneGp);
        assertEquals(2, grouped.size());
        assertEquals(1000, grouped.get("Vorkath").gpPerHour());
        assertEquals(2000, grouped.get("Zulrah").gpPerHour());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.goodrunetracker.core.CategoryAggregatorTest`
Expected: FAIL — `CategoryStats`/`CategoryAggregator` do not exist.

- [ ] **Step 3: Write minimal `CategoryStats`**

```java
package com.goodrunetracker.core;

import com.goodrunetracker.core.item.ItemValuer;
import java.util.List;

/** Aggregated averages across all sessions sharing a category. */
public final class CategoryStats {

    private static final long MILLIS_PER_HOUR = 3_600_000L;

    private final String category;
    private final int sessionCount;
    private final int tripCount;
    private final long gpPerHour;
    private final long xpPerHour;
    private final long avgTripDurationMillis;
    private final long avgNetProfitPerTrip;
    private final long avgMissedPerTrip;
    private final double avgKillsPerTrip;

    private CategoryStats(String category, int sessionCount, int tripCount, long gpPerHour,
                          long xpPerHour, long avgTripDurationMillis, long avgNetProfitPerTrip,
                          long avgMissedPerTrip, double avgKillsPerTrip) {
        this.category = category;
        this.sessionCount = sessionCount;
        this.tripCount = tripCount;
        this.gpPerHour = gpPerHour;
        this.xpPerHour = xpPerHour;
        this.avgTripDurationMillis = avgTripDurationMillis;
        this.avgNetProfitPerTrip = avgNetProfitPerTrip;
        this.avgMissedPerTrip = avgMissedPerTrip;
        this.avgKillsPerTrip = avgKillsPerTrip;
    }

    public static CategoryStats from(String category, List<Session> sessions, ItemValuer valuer) {
        int tripCount = 0;
        long totalWallClock = 0;
        long totalNet = 0;
        long totalXp = 0;
        long totalTripDuration = 0;
        long totalMissed = 0;
        int totalKills = 0;

        for (Session s : sessions) {
            totalWallClock += s.wallClockMillis();
            totalXp += s.totalXp();
            for (Trip t : s.trips()) {
                tripCount++;
                totalNet += t.netProfit(valuer);
                totalTripDuration += t.durationMillis();
                totalMissed += t.missedValue(valuer);
                totalKills += t.totalKills();
            }
        }

        long gpPerHour = totalWallClock <= 0 ? 0 : totalNet * MILLIS_PER_HOUR / totalWallClock;
        long xpPerHour = totalWallClock <= 0 ? 0 : totalXp * MILLIS_PER_HOUR / totalWallClock;
        long avgDuration = tripCount == 0 ? 0 : totalTripDuration / tripCount;
        long avgNet = tripCount == 0 ? 0 : totalNet / tripCount;
        long avgMissed = tripCount == 0 ? 0 : totalMissed / tripCount;
        double avgKills = tripCount == 0 ? 0 : (double) totalKills / tripCount;

        return new CategoryStats(category, sessions.size(), tripCount, gpPerHour, xpPerHour,
                avgDuration, avgNet, avgMissed, avgKills);
    }

    public String category() {
        return category;
    }

    public int sessionCount() {
        return sessionCount;
    }

    public int tripCount() {
        return tripCount;
    }

    public long gpPerHour() {
        return gpPerHour;
    }

    public long xpPerHour() {
        return xpPerHour;
    }

    public long avgTripDurationMillis() {
        return avgTripDurationMillis;
    }

    public long avgNetProfitPerTrip() {
        return avgNetProfitPerTrip;
    }

    public long avgMissedPerTrip() {
        return avgMissedPerTrip;
    }

    public double avgKillsPerTrip() {
        return avgKillsPerTrip;
    }
}
```

- [ ] **Step 4: Write minimal `CategoryAggregator`**

```java
package com.goodrunetracker.core;

import com.goodrunetracker.core.item.ItemValuer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Groups sessions by category and computes {@link CategoryStats} for each. */
public final class CategoryAggregator {

    private CategoryAggregator() {
    }

    public static Map<String, CategoryStats> aggregate(List<Session> sessions, ItemValuer valuer) {
        Map<String, List<Session>> byCategory = new LinkedHashMap<>();
        for (Session s : sessions) {
            byCategory.computeIfAbsent(s.category(), k -> new ArrayList<>()).add(s);
        }
        Map<String, CategoryStats> result = new HashMap<>();
        for (Map.Entry<String, List<Session>> e : byCategory.entrySet()) {
            result.put(e.getKey(), CategoryStats.from(e.getKey(), e.getValue(), valuer));
        }
        return result;
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests com.goodrunetracker.core.CategoryAggregatorTest`
Expected: PASS.

- [ ] **Step 6: Run the full suite and commit**

Run: `./gradlew test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

```bash
git add src/main/java/com/goodrunetracker/core/CategoryStats.java src/main/java/com/goodrunetracker/core/CategoryAggregator.java src/test/java/com/goodrunetracker/core/CategoryAggregatorTest.java
git commit -m "feat: per-category statistics aggregation"
```

---

## Phase 1 done — what comes next (not in this plan)

- **Phase 2 (RuneLite adapter):** `GoodRuneTrackerPlugin` with `@Subscribe` handlers for `NpcLootReceived`, `ItemContainerChanged` (batched per `GameTick` into a settled combined snapshot), `StatChanged`, the bank-interface trip-end trigger, and local-player death detection; an `ItemValuer`/name-lookup backed by `ItemManager`; and Gson JSON persistence under `~/.runelite/goodrunetracker/<accountHash>/`.
  - **Death ordering (carry-over from Phase 1 review):** the ledger books any net inventory decrease as supplies used, so a death's mass inventory loss would be miscounted if its post-death snapshot reaches `TripLedger.updateCarried`. The adapter must therefore handle death *before* feeding the post-death tick: on local-player death, stop feeding `updateCarried`, set the trip's `died` flag, and apply the keep/discard decision. Only resume per-tick snapshots once a fresh trip/baseline begins.
- **Phase 3 (UI):** the tabbed `PluginPanel` (Now / Sessions / Stats), config options, and session edit/delete.

## Implementation status

Phase 1 implemented on branch `feature/phase1-domain-core`: all 8 tasks complete, 32 unit tests passing, zero RuneLite imports in the core. Beyond the original plan, the following hardening was added during review: guarded `ItemKey` accessors, zero/negative-drop guards in `TripLedger`, locked tests for the pre-baseline and ground-pool edge cases, a multi-session time-weighting test, and `CategoryStats.avgSuppliesPerTrip()` (the per-item supplies average from the approved Stats design).
