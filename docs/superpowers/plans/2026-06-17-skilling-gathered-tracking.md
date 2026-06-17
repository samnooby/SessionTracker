# Skilling (Resources Gathered) Tracking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Capture resources gathered while skilling (logs/fish/ore) as a first-class, separate section of each trip/session — count and GP value — and fold gathered value into the overall net profit alongside combat loot.

**Architecture:** A new `gathered` bucket on `TripLedger` captures the per-tick inventory gains that today fall through as "untracked generic gains" (the surplus at `TripLedger.reconcilePickup` after kill-loot and dropped-brought reconciliation). It threads through `Trip` → `StoredTrip`(JSON) → `TripSnapshot`/`SessionSnapshot`(live) → `SessionHistory` roll-ups → Swing UI, mirroring the existing `pickedUp` flow. `Trip.netProfit` becomes `pickedUp + gathered − supplies`; combat profit (picked) and gather profit (gathered) are reported as two separate gross figures, with supplies a single shared cost line.

**Tech Stack:** Java 11, RuneLite plugin API, Gson (JSON persistence), JUnit 4, Gradle.

## Global Constraints

- Build/test runs via `./gradlew test`; full build via `./gradlew build`.
- Single test class: `./gradlew test --tests "<fully.qualified.ClassName>"`.
- JUnit 4 (`org.junit.Test`, `org.junit.Assert.*`).
- `Trip` is immutable; all map fields are defensively copied in the constructor and returned via `Collections.unmodifiableMap`.
- Item quantities are keyed by `ItemKey`; persisted JSON keys are `ItemKeyCodec` tokens (e.g. `item:560`, `potion:Prayer potion`).
- Backward compatibility: session JSON written before this feature has no `gathered` field; it must deserialize to an empty map, never an NPE.
- Preserve the existing overload idiom (see `CategoryStats.from`, `Session.gpPerHour`): add a new wider `Trip` constructor and keep the existing one delegating, so existing call sites and tests compile unchanged.

---

### Task 1: `gathered` bucket in the core domain (`TripLedger` + `Trip`)

**Files:**
- Modify: `src/main/java/com/goodrunetracker/core/Trip.java`
- Modify: `src/main/java/com/goodrunetracker/core/TripLedger.java`
- Test: `src/test/java/com/goodrunetracker/core/TripLedgerTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces:
  - `Trip(String id, long startMillis, long endMillis, boolean died, Map<String,Integer> kills, Map<ItemKey,Integer> dropped, Map<ItemKey,Integer> pickedUp, Map<ItemKey,Integer> missed, Map<ItemKey,Integer> suppliesUsed, Map<ItemKey,Integer> gathered, Map<String,Long> xpGained)` — new 11-arg canonical constructor.
  - Existing 10-arg `Trip(...)` retained, delegates with an empty `gathered`.
  - `Map<ItemKey,Integer> Trip.gathered()` — unmodifiable.
  - `long Trip.gatheredValue(ItemValuer)`.
  - `long Trip.netProfit(ItemValuer)` now returns `pickedUpValue + gatheredValue − suppliesValue`.

- [ ] **Step 1: Write the failing tests in `TripLedgerTest.java`**

Add these three test methods inside the `TripLedgerTest` class (the `carried(...)` helper and `oneGp` valuer already exist in this file):

```java
    @Test
    public void nonLootGainIsCapturedAsGathered() {
        TripLedger ledger = new TripLedger();
        ledger.updateCarried(carried());                       // baseline empty, no kills
        ledger.updateCarried(carried(ItemKey.item(1511), 27)); // chopped 27 logs
        Trip trip = ledger.build("t1", 0, 60_000, false);
        assertEquals(Integer.valueOf(27), trip.gathered().get(ItemKey.item(1511)));
        assertTrue(trip.pickedUp().isEmpty());
    }

    @Test
    public void gainBeyondGroundPoolGoesToGatheredNotMissed() {
        TripLedger ledger = new TripLedger();
        ledger.updateCarried(carried());                       // baseline empty
        ledger.recordKill("x", carried(ItemKey.item(560), 30));
        ledger.updateCarried(carried(ItemKey.item(560), 50));  // 30 is loot, 20 is a generic gain
        Trip trip = ledger.build("t1", 0, 60_000, false);
        assertEquals(Integer.valueOf(30), trip.pickedUp().get(ItemKey.item(560)));
        assertEquals(Integer.valueOf(20), trip.gathered().get(ItemKey.item(560)));
    }

    @Test
    public void netProfitIncludesGatheredMinusSupplies() {
        TripLedger ledger = new TripLedger();
        ledger.updateCarried(carried(ItemKey.item(385), 5));   // brought 5 sharks
        ledger.updateCarried(carried(ItemKey.item(385), 3,
                ItemKey.item(1511), 100));                     // ate 2 sharks, gathered 100 logs
        Trip trip = ledger.build("t1", 0, 60_000, false);
        assertEquals(100, trip.gatheredValue(oneGp));
        assertEquals(2, trip.suppliesValue(oneGp));
        assertEquals(98, trip.netProfit(oneGp));               // 0 picked + 100 gathered - 2 supplies
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew test --tests "com.goodrunetracker.core.TripLedgerTest"`
Expected: FAIL — compile error, `gathered()` / `gatheredValue` not defined on `Trip`.

- [ ] **Step 3: Add the `gathered` field, constructor overload, and methods to `Trip.java`**

In `src/main/java/com/goodrunetracker/core/Trip.java`, add the field after `suppliesUsed` (line 20):

```java
    private final Map<ItemKey, Integer> gathered;
```

Replace the existing constructor (lines 23-37) with a delegating 10-arg constructor plus the new 11-arg canonical one:

```java
    public Trip(String id, long startMillis, long endMillis, boolean died,
                Map<String, Integer> kills, Map<ItemKey, Integer> dropped,
                Map<ItemKey, Integer> pickedUp, Map<ItemKey, Integer> missed,
                Map<ItemKey, Integer> suppliesUsed, Map<String, Long> xpGained) {
        this(id, startMillis, endMillis, died, kills, dropped, pickedUp, missed,
                suppliesUsed, new HashMap<>(), xpGained);
    }

    public Trip(String id, long startMillis, long endMillis, boolean died,
                Map<String, Integer> kills, Map<ItemKey, Integer> dropped,
                Map<ItemKey, Integer> pickedUp, Map<ItemKey, Integer> missed,
                Map<ItemKey, Integer> suppliesUsed, Map<ItemKey, Integer> gathered,
                Map<String, Long> xpGained) {
        this.id = id;
        this.startMillis = startMillis;
        this.endMillis = endMillis;
        this.died = died;
        this.kills = new HashMap<>(kills);
        this.dropped = new HashMap<>(dropped);
        this.pickedUp = new HashMap<>(pickedUp);
        this.missed = new HashMap<>(missed);
        this.suppliesUsed = new HashMap<>(suppliesUsed);
        this.gathered = new HashMap<>(gathered);
        this.xpGained = new HashMap<>(xpGained);
    }
```

Add the accessor next to `pickedUp()` (after line 69):

```java
    public Map<ItemKey, Integer> gathered() {
        return Collections.unmodifiableMap(gathered);
    }
```

Add the value method next to `pickedUpValue` (after line 101):

```java
    public long gatheredValue(ItemValuer valuer) {
        return value(gathered, valuer);
    }
```

Replace `netProfit` (lines 111-113) to include gathered:

```java
    public long netProfit(ItemValuer valuer) {
        return pickedUpValue(valuer) + gatheredValue(valuer) - suppliesValue(valuer);
    }
```

- [ ] **Step 4: Capture the generic gain in `TripLedger.java`**

In `src/main/java/com/goodrunetracker/core/TripLedger.java`, add the field after `droppedBrought` (line 28):

```java
    private final Map<ItemKey, Integer> gathered = new HashMap<>();
```

Replace the trailing comment in `reconcilePickup` (line 90) with the capture:

```java
        // Any further gain is a non-loot inventory gain (e.g. a gathered resource).
        if (remaining > 0) {
            gathered.merge(key, remaining, Integer::sum);
        }
```

Update the `build(...)` return (lines 137-138) to pass `gathered` into the 11-arg constructor:

```java
        return new Trip(id, startMillis, endMillis, died,
                kills, dropped, pickedUp, missed, suppliesUsed, gathered, xp);
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew test --tests "com.goodrunetracker.core.TripLedgerTest"`
Expected: PASS (all methods, including the three new ones).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/goodrunetracker/core/Trip.java src/main/java/com/goodrunetracker/core/TripLedger.java src/test/java/com/goodrunetracker/core/TripLedgerTest.java
git commit -m "feat: capture non-loot inventory gains as gathered resources"
```

---

### Task 2: Persist `gathered` through JSON (`StoredTrip` + `SessionMapper`)

**Files:**
- Modify: `src/main/java/com/goodrunetracker/adapter/StoredTrip.java`
- Modify: `src/main/java/com/goodrunetracker/adapter/SessionMapper.java`
- Test: `src/test/java/com/goodrunetracker/adapter/SessionMapperTest.java`

**Interfaces:**
- Consumes: `Trip.gathered()`, the 11-arg `Trip` constructor (Task 1).
- Produces: `StoredTrip.gathered` (`Map<String,Integer>`); `SessionMapper.toStored`/`toTrip` round-trip it; `toTrip` tolerates a null `gathered` (old files).

- [ ] **Step 1: Write the failing tests in `SessionMapperTest.java`**

Add a gathered entry to the existing `sampleTrip()` helper so the round-trip covers it. Replace the `sampleTrip()` body's supplies/xp/return section so it uses the 11-arg constructor with a gathered map:

```java
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
        Map<ItemKey, Integer> gathered = new HashMap<>();
        gathered.put(ItemKey.item(1511), 27);
        Map<String, Long> xp = new HashMap<>();
        xp.put("RANGED", 5000L);
        return new Trip("t1", 0, 60_000, false, kills, dropped, pickedUp, missed, supplies,
                gathered, xp);
    }
```

Then add these two test methods:

```java
    @Test
    public void encodesAndRoundTripsGathered() {
        Map<ItemKey, Long> prices = new HashMap<>();
        prices.put(ItemKey.item(1511), 50L);
        StoredTrip stored = SessionMapper.toStored(sampleTrip(), prices);
        assertEquals(Integer.valueOf(27), stored.gathered.get("item:1511"));

        Trip restored = SessionMapper.toTrip(stored);
        assertEquals(Integer.valueOf(27), restored.gathered().get(ItemKey.item(1511)));
    }

    @Test
    public void toTripTreatsMissingGatheredAsEmpty() {
        StoredTrip stored = SessionMapper.toStored(sampleTrip(), new HashMap<>());
        stored.gathered = null; // simulate a session saved before the feature existed
        Trip restored = SessionMapper.toTrip(stored);
        assertTrue(restored.gathered().isEmpty());
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew test --tests "com.goodrunetracker.adapter.SessionMapperTest"`
Expected: FAIL — `StoredTrip.gathered` does not exist (compile error).

- [ ] **Step 3: Add the `gathered` field to `StoredTrip.java`**

In `src/main/java/com/goodrunetracker/adapter/StoredTrip.java`, add after `suppliesUsed` (line 15):

```java
    public Map<String, Integer> gathered;
```

- [ ] **Step 4: Map `gathered` both directions in `SessionMapper.java`, null-safely**

In `toStored` (after line 30) add:

```java
        stored.gathered = encode(trip.gathered());
```

In `toTrip` (lines 36-42), pass decoded gathered into the 11-arg constructor:

```java
    public static Trip toTrip(StoredTrip stored) {
        return new Trip(stored.id, stored.startMillis, stored.endMillis, stored.died,
                new HashMap<>(stored.kills),
                decode(stored.dropped), decode(stored.pickedUp),
                decode(stored.missed), decode(stored.suppliesUsed),
                decode(stored.gathered),
                new HashMap<>(stored.xpGained));
    }
```

Make `decode` null-safe by adding a guard at the top of the method (lines 90-94):

```java
    private static Map<ItemKey, Integer> decode(Map<String, Integer> map) {
        Map<ItemKey, Integer> out = new HashMap<>();
        if (map == null) {
            return out;
        }
        map.forEach((token, qty) -> out.put(ItemKeyCodec.decode(token), qty));
        return out;
    }
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew test --tests "com.goodrunetracker.adapter.SessionMapperTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/StoredTrip.java src/main/java/com/goodrunetracker/adapter/SessionMapper.java src/test/java/com/goodrunetracker/adapter/SessionMapperTest.java
git commit -m "feat: persist gathered resources in stored trips"
```

---

### Task 3: Live snapshots, valuation capture, and net profit (`TrackingService` + `TripSnapshot` + `SessionSnapshot`)

**Files:**
- Modify: `src/main/java/com/goodrunetracker/adapter/TripSnapshot.java`
- Modify: `src/main/java/com/goodrunetracker/adapter/SessionSnapshot.java`
- Modify: `src/main/java/com/goodrunetracker/adapter/TrackingService.java`
- Test: `src/test/java/com/goodrunetracker/adapter/TrackingServiceTest.java`

**Interfaces:**
- Consumes: `Trip.gatheredValue`, `Trip.gathered()`, `Trip.netProfit` (Task 1).
- Produces:
  - `TripSnapshot.gatheredGp` (`long`), added as the last constructor parameter.
  - `SessionSnapshot.gatheredGp` (`long`), added as the last constructor parameter.

- [ ] **Step 1: Write the failing tests in `TrackingServiceTest.java`**

Add these two test methods (the `FakeClock`/`FakeCarried`/`FakePanel` helpers, `newService(...)`, and `oneGp` source already exist in this file):

```java
    @Test
    public void gatheredResourcesAppearInSnapshotAndPersist() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        Path root = Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root);
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession();                 // baseline empty
        carried.carried.put(1511, 27);          // chopped 27 logs, no kill
        service.markCarriedDirty();
        clock.now = 30_000;
        service.onTick();

        assertEquals(27, service.currentSnapshot().get().gatheredGp); // oneGp valuer
        service.endSession();

        StoredSession saved = store.load("acct-A").get(0);
        Trip trip = SessionMapper.toTrip(saved.trips.get(0));
        assertEquals(Integer.valueOf(27), trip.gathered().get(ItemKey.item(1511)));
    }

    @Test
    public void gatheredAddsToNetProfit() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"));
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession();
        carried.carried.put(1511, 100);         // gathered 100 logs (gross, no supplies)
        service.markCarriedDirty();
        clock.now = 3_600_000L;                 // 1 hour
        service.onTick();
        service.onBankOpened();                 // ends the trip, persisting gathered=100

        SessionSnapshot snap = service.currentSessionSnapshot().get();
        assertEquals(100L, snap.netProfit);     // 0 picked + 100 gathered - 0 supplies
        assertEquals(100L, snap.gatheredGp);
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew test --tests "com.goodrunetracker.adapter.TrackingServiceTest"`
Expected: FAIL — `TripSnapshot.gatheredGp` / `SessionSnapshot.gatheredGp` not defined.

- [ ] **Step 3: Add `gatheredGp` to `TripSnapshot.java`**

In `src/main/java/com/goodrunetracker/adapter/TripSnapshot.java`, add the field after `suppliesGp` (line 11):

```java
    public final long gatheredGp;
```

Add the parameter to the constructor (after `suppliesGp` in the signature on line 19) and assign it. The full constructor becomes:

```java
    public TripSnapshot(int tripNumber, long durationMillis, int kills, long pickedGp,
                        long groundGp, long suppliesGp, long gatheredGp, long totalXp, long gpPerHour,
                        List<SkillXp> xpBySkill, List<NpcKills> killsByNpc) {
        this.tripNumber = tripNumber;
        this.durationMillis = durationMillis;
        this.kills = kills;
        this.pickedGp = pickedGp;
        this.groundGp = groundGp;
        this.suppliesGp = suppliesGp;
        this.gatheredGp = gatheredGp;
        this.totalXp = totalXp;
        this.gpPerHour = gpPerHour;
        this.xpBySkill = xpBySkill;
        this.killsByNpc = killsByNpc;
    }
```

- [ ] **Step 4: Add `gatheredGp` to `SessionSnapshot.java`**

Read `src/main/java/com/goodrunetracker/adapter/SessionSnapshot.java` first. It currently has fields `tripCount`, `netProfit`, `totalXp`, `gpPerHour` and a matching constructor. Add a `public final long gatheredGp;` field and a `long gatheredGp` parameter (place it last before any list fields; for this class place it after `gpPerHour`), assigning it in the constructor body. Example final shape:

```java
    public SessionSnapshot(int tripCount, long netProfit, long totalXp, long gpPerHour,
                           long gatheredGp) {
        this.tripCount = tripCount;
        this.netProfit = netProfit;
        this.totalXp = totalXp;
        this.gpPerHour = gpPerHour;
        this.gatheredGp = gatheredGp;
    }
```

- [ ] **Step 5: Wire gathered through `TrackingService.java`**

In `computeSnapshot()` (lines 232-245), add the gathered value, fold it into net, and pass it to the snapshot:

```java
    private TripSnapshot computeSnapshot() {
        long now = clock.nowMillis();
        Trip trip = ledger.build(tripId, tripStartMillis, now, tripDied);
        long picked = trip.pickedUpValue(valuer);
        long ground = trip.missedValue(valuer);
        long supplies = trip.suppliesValue(valuer);
        long gathered = trip.gatheredValue(valuer);
        long duration = now - tripStartMillis;
        long net = picked + gathered - supplies;
        long gpPerHour = duration <= 0 ? 0 : net * MILLIS_PER_HOUR / duration;
        int tripNumber = activeSession.trips.size() + 1;
        return new TripSnapshot(tripNumber, duration, trip.totalKills(),
                picked, ground, supplies, gathered, trip.totalXp(), gpPerHour,
                SkillXp.sortedFrom(trip.xpGained()), NpcKills.sortedByCountDesc(trip.kills()));
    }
```

In `computeSessionSnapshot()` (lines 247-265), accumulate gathered for completed trips and the current trip, and include it in net:

```java
    private SessionSnapshot computeSessionSnapshot() {
        long net = 0;
        long xp = 0;
        long gathered = 0;
        for (StoredTrip st : activeSession.trips) {
            Trip t = SessionMapper.toTrip(st);
            FrozenItemValuer frozen = new FrozenItemValuer(SessionMapper.unitPrices(st));
            net += t.netProfit(frozen);
            xp += t.totalXp();
            gathered += t.gatheredValue(frozen);
        }
        int tripCount = activeSession.trips.size();
        if (cachedSnapshot != null) {
            net += cachedSnapshot.pickedGp + cachedSnapshot.gatheredGp - cachedSnapshot.suppliesGp;
            xp += cachedSnapshot.totalXp;
            gathered += cachedSnapshot.gatheredGp;
            tripCount += 1;
        }
        long wallClock = clock.nowMillis() - activeSession.startMillis;
        long gpPerHour = wallClock <= 0 ? 0 : net * MILLIS_PER_HOUR / wallClock;
        return new SessionSnapshot(tripCount, net, xp, gpPerHour, gathered);
    }
```

In `endTrip()` add gathered to the empty-trip guard (line 299) so a gather-only run still persists:

```java
        if (trip.totalKills() == 0 && trip.suppliesUsed().isEmpty() && trip.totalXp() == 0
                && trip.gathered().isEmpty()) {
            return;
        }
```

In `allKeys(Trip)` (lines 316-323) add gathered keys so gathered items get a captured unit price:

```java
        keys.addAll(trip.gathered().keySet());
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew test --tests "com.goodrunetracker.adapter.TrackingServiceTest"`
Expected: PASS (new methods plus all pre-existing ones — pre-existing tests have no non-loot gains, so `gatheredGp` is 0 and net is unchanged).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/TripSnapshot.java src/main/java/com/goodrunetracker/adapter/SessionSnapshot.java src/main/java/com/goodrunetracker/adapter/TrackingService.java src/test/java/com/goodrunetracker/adapter/TrackingServiceTest.java
git commit -m "feat: surface gathered resources in live trip and session snapshots"
```

---

### Task 4: History roll-ups — gather averages, per-hour split, trip detail (`SessionHistory`)

**Files:**
- Modify: `src/main/java/com/goodrunetracker/adapter/SessionHistory.java`
- Test: `src/test/java/com/goodrunetracker/adapter/SessionHistoryTest.java`

**Interfaces:**
- Consumes: `Trip.gathered()`, `Trip.pickedUpValue`, `Trip.gatheredValue` (Task 1); existing `itemAverages(...)` helper and `ItemAverage` carrier.
- Produces, on `SessionHistory.CategoryDetail`, new public final fields (added as the last constructor parameters, in this order):
  - `List<ItemAverage> gatheredAverages`
  - `long avgGatheredGpPerTrip`
  - `long combatGpPerHour`
  - `long gatherGpPerHour`
- Produces, on `SessionHistory.TripDetail`, new public final fields (added as the last constructor parameters, in this order):
  - `List<ItemLine> gathered`
  - `long gatheredValue`

- [ ] **Step 1: Write the failing tests in `SessionHistoryTest.java`**

Add these two test methods (the `qty`, `trip`, `save` helpers and `names` already exist):

```java
    @Test
    public void categoryDetailAveragesGatheredPerItemAndSplitsGpPerHour() throws Exception {
        Path root = Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root);
        ItemKey coins = ItemKey.item(560);
        ItemKey logs = ItemKey.item(1511);
        Map<ItemKey, Long> price = new HashMap<>();
        price.put(coins, 1L);
        price.put(logs, 10L);

        // Trip A: picked 100 coins (combat), gathered 20 logs. 1h.
        Map<ItemKey, Integer> pickedA = new HashMap<>();
        pickedA.put(coins, 100);
        Map<ItemKey, Integer> gatheredA = new HashMap<>();
        gatheredA.put(logs, 20);
        Trip t1 = new Trip("t1", 0, 3_600_000L, false, new HashMap<>(), new HashMap<>(),
                pickedA, new HashMap<>(), new HashMap<>(), gatheredA, new HashMap<>());
        // Trip B: gathered 40 logs only. 1h.
        Map<ItemKey, Integer> gatheredB = new HashMap<>();
        gatheredB.put(logs, 40);
        Trip t2 = new Trip("t2", 3_600_000L, 7_200_000L, false, new HashMap<>(), new HashMap<>(),
                new HashMap<>(), new HashMap<>(), new HashMap<>(), gatheredB, new HashMap<>());
        save(store, "acct", "s", "Woodcutting", "", 0, 7_200_000L,
                Arrays.asList(SessionMapper.toStored(t1, price), SessionMapper.toStored(t2, price)));

        SessionHistory history = new SessionHistory(store, "acct", names);
        SessionHistory.CategoryDetail d = history.categoryDetail("Woodcutting");

        // Gathered: logs 60qty / 600gp total over 2 trips -> 30qty, 300gp avg
        assertEquals(1, d.gatheredAverages.size());
        assertEquals("Item 1511", d.gatheredAverages.get(0).label);
        assertEquals(30.0, d.gatheredAverages.get(0).avgQtyPerTrip, 0.0001);
        assertEquals(300L, d.gatheredAverages.get(0).avgGpPerTrip);
        assertEquals(300L, d.avgGatheredGpPerTrip);

        // 2h wall-clock: combat 100gp -> 50/hr; gather 600gp -> 300/hr; overall net 700gp -> 350/hr
        assertEquals(50L, d.combatGpPerHour);
        assertEquals(300L, d.gatherGpPerHour);
        assertEquals(350L, d.gpPerHour);
    }

    @Test
    public void tripDetailIncludesGatheredAndCombinedNetProfit() throws Exception {
        Path root = Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root);
        ItemKey logs = ItemKey.item(1511);
        Map<ItemKey, Long> price = new HashMap<>();
        price.put(logs, 10L);
        Map<ItemKey, Integer> gathered = new HashMap<>();
        gathered.put(logs, 25);
        Trip t = new Trip("t1", 0, 3_600_000L, false, new HashMap<>(), new HashMap<>(),
                new HashMap<>(), new HashMap<>(), new HashMap<>(), gathered, new HashMap<>());
        save(store, "acct", "s", "Woodcutting", "", 0, 3_600_000L,
                Arrays.asList(SessionMapper.toStored(t, price)));

        SessionHistory history = new SessionHistory(store, "acct", names);
        SessionHistory.TripDetail d = history.tripDetail("s", "t1");

        assertEquals(1, d.gathered.size());
        assertEquals("Item 1511", d.gathered.get(0).label);
        assertEquals(25, d.gathered.get(0).quantity);
        assertEquals(250L, d.gathered.get(0).gpValue);
        assertEquals(250L, d.gatheredValue);
        assertEquals(250L, d.netProfit);    // 0 picked + 250 gathered - 0 supplies
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew test --tests "com.goodrunetracker.adapter.SessionHistoryTest"`
Expected: FAIL — `CategoryDetail.gatheredAverages` / `TripDetail.gathered` not defined.

- [ ] **Step 3: Add the new fields to the `CategoryDetail` carrier**

In `src/main/java/com/goodrunetracker/adapter/SessionHistory.java`, in `CategoryDetail` (lines 401-441), add four fields after `avgDroppedGpPerTrip`:

```java
        public final List<ItemAverage> gatheredAverages;
        public final long avgGatheredGpPerTrip;
        public final long combatGpPerHour;
        public final long gatherGpPerHour;
```

Add the four matching parameters to the end of the constructor signature and assign them:

```java
        public CategoryDetail(long gpPerHour, long xpPerHour, long avgNetProfitPerTrip,
                              long avgMissedPerTrip, long avgTripDurationMillis, double avgKillsPerTrip,
                              List<ItemAverage> supplies, long avgTotalSuppliesGpPerTrip,
                              List<SkillXpAverage> xpAverages, List<NpcKillAverage> killAverages,
                              List<ItemAverage> pickedAverages, long avgPickedGpPerTrip,
                              List<ItemAverage> missedAverages, List<ItemAverage> droppedAverages,
                              long avgDroppedGpPerTrip, List<ItemAverage> gatheredAverages,
                              long avgGatheredGpPerTrip, long combatGpPerHour, long gatherGpPerHour) {
            // ... existing assignments unchanged ...
            this.gatheredAverages = gatheredAverages;
            this.avgGatheredGpPerTrip = avgGatheredGpPerTrip;
            this.combatGpPerHour = combatGpPerHour;
            this.gatherGpPerHour = gatherGpPerHour;
        }
```

- [ ] **Step 4: Compute gather averages and the per-hour split in `categoryDetail(...)`**

In `categoryDetail(String category)` (lines 106-157), add a gathered `ItemAverages` next to the existing ones (after line 115):

```java
        ItemAverages gatheredAvg = itemAverages(sessions, fn, tripCount, Trip::gathered);
```

Add combat/gather GP accumulation inside the existing wall-clock loop. Replace the loop at lines 117-126 with one that also sums combat and gather gp:

```java
        Map<String, Long> skillTotalXp = new TreeMap<>(); // TreeMap -> alphabetical
        long totalWallClock = 0;
        long totalCombatGp = 0;
        long totalGatherGp = 0;
        for (Session s : sessions) {
            totalWallClock += s.wallClockMillis();
            for (Trip t : s.trips()) {
                ItemValuer v = fn.apply(t);
                totalCombatGp += t.pickedUpValue(v);
                totalGatherGp += t.gatheredValue(v);
                for (Map.Entry<String, Long> e : t.xpGained().entrySet()) {
                    skillTotalXp.merge(e.getKey(), e.getValue(), Long::sum);
                }
            }
        }
        long combatGpPerHour = totalWallClock <= 0 ? 0 : totalCombatGp * MILLIS_PER_HOUR / totalWallClock;
        long gatherGpPerHour = totalWallClock <= 0 ? 0 : totalGatherGp * MILLIS_PER_HOUR / totalWallClock;
```

Update the `return new CategoryDetail(...)` (lines 152-156) to pass the four new arguments:

```java
        return new CategoryDetail(cs.gpPerHour(), cs.xpPerHour(), cs.avgNetProfitPerTrip(),
                cs.avgMissedPerTrip(), cs.avgTripDurationMillis(), cs.avgKillsPerTrip(),
                supplyAvg.items, supplyAvg.avgTotalGpPerTrip, xpAverages, killAverages,
                pickedAvg.items, pickedAvg.avgTotalGpPerTrip,
                missedAvg.items, droppedAvg.items, droppedAvg.avgTotalGpPerTrip,
                gatheredAvg.items, gatheredAvg.avgTotalGpPerTrip, combatGpPerHour, gatherGpPerHour);
```

- [ ] **Step 5: Add gathered to the `TripDetail` carrier and `tripDetail(...)`**

In the `TripDetail` class (lines 326-346), add two fields after `suppliesUsed`:

```java
        public final List<ItemLine> gathered;
        public final long gatheredValue;
```

Add them as the last two constructor parameters and assign them:

```java
        public TripDetail(List<ItemLine> pickedUp, List<ItemLine> leftOnGround,
                          List<ItemLine> suppliesUsed, long netProfit, long missedValue,
                          List<SkillXp> xpGained, List<NpcKills> killsByNpc,
                          List<ItemLine> gathered, long gatheredValue) {
            this.pickedUp = pickedUp;
            this.leftOnGround = leftOnGround;
            this.suppliesUsed = suppliesUsed;
            this.netProfit = netProfit;
            this.missedValue = missedValue;
            this.xpGained = xpGained;
            this.killsByNpc = killsByNpc;
            this.gathered = gathered;
            this.gatheredValue = gatheredValue;
        }
```

Update the `tripDetail(...)` return (lines 86-88) to build and pass them:

```java
                return new TripDetail(lines(t.pickedUp(), v), lines(t.missed(), v),
                        lines(t.suppliesUsed(), v), t.netProfit(v), t.missedValue(v),
                        SkillXp.sortedFrom(t.xpGained()), NpcKills.sortedByCountDesc(t.kills()),
                        lines(t.gathered(), v), t.gatheredValue(v));
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew test --tests "com.goodrunetracker.adapter.SessionHistoryTest"`
Expected: PASS (new methods plus all pre-existing ones).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/SessionHistory.java src/test/java/com/goodrunetracker/adapter/SessionHistoryTest.java
git commit -m "feat: gather averages, combat/gather gp-per-hour split, gathered in trip detail"
```

---

### Task 5: Render the Gathered section in the UI (`NowTab` + `StatsTab` + `SessionsTab`)

**Files:**
- Modify: `src/main/java/com/goodrunetracker/adapter/runelite/NowTab.java`
- Modify: `src/main/java/com/goodrunetracker/adapter/runelite/StatsTab.java`
- Modify: `src/main/java/com/goodrunetracker/adapter/runelite/SessionsTab.java`

**Interfaces:**
- Consumes: `TripSnapshot.gatheredGp` (Task 3); `CategoryDetail.gatheredAverages`/`avgGatheredGpPerTrip`/`combatGpPerHour`/`gatherGpPerHour` and `TripDetail.gathered`/`gatheredValue` (Task 4).
- Produces: no new types. Swing rendering only — there are no unit tests for Swing in this project; verification is a successful compile + full build + the manual smoke run.

- [ ] **Step 1: Add a "Gathered" row to the NowTab current-trip card**

In `src/main/java/com/goodrunetracker/adapter/runelite/NowTab.java`, add a field next to `supplies` (after line 46):

```java
    private final JLabel gathered = Styles.valueLabel(Styles.GP);
```

In `currentCard()` add a Gathered row to the grid (after the Supplies row, after line 196):

```java
        grid.add(Styles.keyLabel("Gathered"));
        grid.add(gathered);
```

In `renderStats()` set it in the present branch (after line 347) and reset it in the empty branch (after line 357):

```java
        // present branch, after supplies.setText(...):
        gathered.setText(GpFormat.format(s.gatheredGp));
```

```java
        // empty branch, after supplies.setText("-"):
        gathered.setText("-");
```

- [ ] **Step 2: Add the Gathered averages table and gather/hr to the StatsTab detail**

In `src/main/java/com/goodrunetracker/adapter/runelite/StatsTab.java`, in `renderDetail(...)`, add a gathered item table next to the existing ones (after line 174, the "Avg picked up / trip" table):

```java
        addItemTable("Avg gathered / trip", d.gatheredAverages, d.avgGatheredGpPerTrip, Styles.GP);
```

Add a combat-vs-gather per-hour breakdown right under the existing `tiles(d.gpPerHour, d.xpPerHour)` call (after line 151). Render a small two-row grid so the split is visible:

```java
        JPanel split = new JPanel(new GridLayout(0, 2, 0, 3));
        split.setBackground(Styles.PANEL);
        split.setAlignmentX(Component.LEFT_ALIGNMENT);
        split.add(Styles.keyLabel("Combat GP/hr"));
        split.add(signedValue(d.combatGpPerHour));
        split.add(Styles.keyLabel("Gather GP/hr"));
        split.add(signedValue(d.gatherGpPerHour));
        Styles.capHeight(split);
        detailBody.add(split);
```

- [ ] **Step 3: Add a Gathered table to the SessionsTab trip detail**

Read `src/main/java/com/goodrunetracker/adapter/runelite/SessionsTab.java` first to find where it renders a `TripDetail` (it consumes `detail.pickedUp` / `detail.suppliesUsed` via a table helper). Following the exact pattern that file already uses for the picked/supplies tables, add a "Gathered" table sourced from `detail.gathered` with total `detail.gatheredValue`, placed adjacent to the picked-up table. Use the same color constant the picked-up table uses (`Styles.GP`). If the file has a local table helper, reuse it; do not introduce a new helper.

- [ ] **Step 4: Verify the whole project compiles and all tests pass**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL — compiles `NowTab`, `StatsTab`, `SessionsTab` and runs the full test suite green.

- [ ] **Step 5: Manual smoke test (RuneLite client)**

Launch the plugin via the existing run entry point (`src/test/java/com/goodrunetracker/adapter/runelite/GoodRuneTrackerPluginRun.java`), start tracking, and confirm: the Now tab shows a "Gathered" row that increases when inventory gains a non-loot item; the Stats detail for a skilling category shows "Avg gathered / trip" and the Combat/Gather GP/hr split; a sessions trip detail shows the Gathered table. (No automated assertion — visual confirmation only.)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/runelite/NowTab.java src/main/java/com/goodrunetracker/adapter/runelite/StatsTab.java src/main/java/com/goodrunetracker/adapter/runelite/SessionsTab.java
git commit -m "feat: render gathered resources across Now, Stats, and Sessions tabs"
```

---

## Known Limitations (carried from the spec)

- **Banking deposits still register as supplies.** `TrackingService.onBankOpened()` already ends the trip when the bank opens, so the gather run is captured correctly at bank-open. But the subsequent deposit happens in the *next* trip: inventory drops while the bank is open are charged to `suppliesUsed` (no bank-open guard exists). The gross Gathered section stays accurate; net profit and the supplies line can be polluted by deposits. Addressed by the follow-up "banking" task.
- **False positives.** Any non-loot inventory gain (shop purchase, bank withdrawal, quest reward) is counted as gathered. Acceptable for v1.

## Follow-up tasks (not in this plan)

1. **Banking config flag** — a RuneLite config setting to toggle whether opening the bank ends the trip, plus suppressing inventory deltas as supplies while the bank interface is open, so deposited gathered resources are not double-counted.
2. **Action counts & rates** — choose a signal (XP-event count recommended) and surface gathered actions/hr.
3. **Gathered false-positive filtering** — optionally exclude shop/bank/quest gains.

## Self-Review

- **Spec coverage:** gathered bucket (Task 1) ✓; valuation via captured prices (Tasks 2-3 `allKeys`) ✓; combined net profit = picked + gathered − supplies (Tasks 1, 3, 4) ✓; two separate gross profit figures — combat (`pickedUpValue`/`combatGpPerHour`/`pickedAverages`) and gather (`gatheredValue`/`gatherGpPerHour`/`gatheredAverages`) ✓; supplies as a single shared cost line (unchanged supplies bucket) ✓; dedicated Gathered UI section with its own averages (Tasks 4-5) ✓; backward-compatible JSON (Task 2) ✓; deferred banking flag + action counts documented ✓.
- **Placeholder scan:** every code step shows full code; the only prose-only step (Task 5 Step 3, SessionsTab) is explicit about pattern, source fields, color, and placement because the exact local helper must be read in-file first. No TBD/TODO.
- **Type consistency:** `gathered()` returns `Map<ItemKey,Integer>`; `gatheredValue`/`gatherProfit` return `long`; `TripSnapshot.gatheredGp`, `SessionSnapshot.gatheredGp`, `CategoryDetail.avgGatheredGpPerTrip`/`combatGpPerHour`/`gatherGpPerHour` are `long`; `CategoryDetail.gatheredAverages` and `TripDetail.gathered` match the existing `List<ItemAverage>` / `List<ItemLine>` types. The 11-arg `Trip` constructor order (`..., suppliesUsed, gathered, xpGained`) is used identically in `TripLedger.build`, `SessionMapper.toTrip`, and all new test constructions.
