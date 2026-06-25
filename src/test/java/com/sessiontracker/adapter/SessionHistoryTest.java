package com.sessiontracker.adapter;

import static org.junit.Assert.*;
import com.sessiontracker.core.Trip;
import com.sessiontracker.core.item.ItemKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import org.junit.Test;

public class SessionHistoryTest {

    private final IntFunction<String> names = id -> "Item " + id;

    private static Map<ItemKey, Integer> qty(ItemKey k, int n) {
        Map<ItemKey, Integer> m = new HashMap<>();
        m.put(k, n);
        return m;
    }

    private static StoredTrip trip(String id, long start, long end, int killCount,
                                   Map<ItemKey, Integer> picked, Map<ItemKey, Integer> missed,
                                   Map<ItemKey, Integer> supplies, Map<ItemKey, Long> prices) {
        Map<String, Integer> kills = new HashMap<>();
        if (killCount > 0) {
            kills.put("Vorkath", killCount);
        }
        Trip t = new Trip(id, start, end, false, kills, new HashMap<>(), picked, missed,
                supplies, new HashMap<>());
        return SessionMapper.toStored(t, prices);
    }

    private static void save(SessionStore store, String acct, String id, String category,
                             String name, long start, long end, List<StoredTrip> trips) {
        StoredSession s = new StoredSession();
        s.id = id;
        s.accountHash = acct;
        s.category = category;
        s.name = name;
        s.startMillis = start;
        s.endMillis = end;
        s.trips = trips;
        store.save(s);
    }

    @Test
    public void sessionsAreListedNewestFirstWithSummaryValues() throws Exception {
        Path root = Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root, new com.google.gson.Gson());
        ItemKey coins = ItemKey.item(560);
        Map<ItemKey, Long> price = new HashMap<>();
        price.put(coins, 1L);
        save(store, "acct", "old", "Vorkath", "morning", 0, 3_600_000L,
                Arrays.asList(trip("t1", 0, 3_600_000L, 5, qty(coins, 100),
                        new HashMap<>(), new HashMap<>(), price)));
        save(store, "acct", "new", "Zulrah", "evening", 10_000_000L, 13_600_000L,
                Arrays.asList(trip("t2", 10_000_000L, 13_600_000L, 3, qty(coins, 50),
                        new HashMap<>(), new HashMap<>(), price)));

        SessionHistory history = new SessionHistory(store, "acct", names);
        List<SessionHistory.SessionSummary> list = history.sessionsNewestFirst();

        assertEquals(2, list.size());
        assertEquals("new", list.get(0).sessionId);
        assertEquals("Zulrah", list.get(0).category);
        assertEquals(1, list.get(0).tripCount);
        assertEquals(50L, list.get(0).netProfit);
        assertEquals(50L, list.get(0).gpPerHour);
        assertEquals("old", list.get(1).sessionId);
    }

    @Test
    public void tripsForReturnsPerTripSummaries() throws Exception {
        Path root = Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root, new com.google.gson.Gson());
        ItemKey coins = ItemKey.item(560);
        Map<ItemKey, Long> price = new HashMap<>();
        price.put(coins, 1L);
        save(store, "acct", "s", "Vorkath", "", 0, 3_600_000L,
                Arrays.asList(trip("t1", 0, 1_800_000L, 5, qty(coins, 80), new HashMap<>(),
                        new HashMap<>(), price)));

        SessionHistory history = new SessionHistory(store, "acct", names);
        List<SessionHistory.TripSummary> trips = history.tripsFor("s");
        assertEquals(1, trips.size());
        assertEquals("t1", trips.get(0).tripId);
        assertEquals(5, trips.get(0).kills);
        assertEquals(80L, trips.get(0).netProfit);
        assertEquals(1_800_000L, trips.get(0).durationMillis);
    }

    @Test
    public void tripDetailGroupsPickedMissedAndSupplies() throws Exception {
        Path root = Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root, new com.google.gson.Gson());
        ItemKey coins = ItemKey.item(560);
        ItemKey shark = ItemKey.item(385);
        Map<ItemKey, Long> price = new HashMap<>();
        price.put(coins, 1L);
        price.put(shark, 1000L);
        save(store, "acct", "s", "Vorkath", "", 0, 3_600_000L,
                Arrays.asList(trip("t1", 0, 3_600_000L, 5, qty(coins, 100),
                        qty(coins, 40), qty(shark, 3), price)));

        SessionHistory history = new SessionHistory(store, "acct", names);
        SessionHistory.TripDetail detail = history.tripDetail("s", "t1");

        assertEquals(1, detail.pickedUp.size());
        assertEquals("Item 560", detail.pickedUp.get(0).label);
        assertEquals(100, detail.pickedUp.get(0).quantity);
        assertEquals(100L, detail.pickedUp.get(0).gpValue);
        assertEquals(40L, detail.missedValue);
        assertEquals(1, detail.suppliesUsed.size());
        assertEquals(3000L, detail.suppliesUsed.get(0).gpValue);
        assertEquals(-2_900L, detail.netProfit);
    }

    @Test
    public void tripDetailShowsKeptPickedUpAndUsedLoot() throws Exception {
        // Looted 4 sharks, ate 3: picked-up shows the 1 kept; the 3 eaten show as used loot.
        Path root = Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root, new com.google.gson.Gson());
        ItemKey shark = ItemKey.item(385);
        Map<ItemKey, Long> price = new HashMap<>();
        price.put(shark, 1000L);
        Map<ItemKey, Integer> picked = new HashMap<>();
        picked.put(shark, 4);
        Map<ItemKey, Integer> consumed = new HashMap<>();
        consumed.put(shark, 3);
        Trip t = new Trip("t1", 0, 3_600_000L, false, new HashMap<>(), new HashMap<>(),
                picked, new HashMap<>(), new HashMap<>(), new HashMap<>(), consumed, new HashMap<>());
        save(store, "acct", "s", "Vorkath", "", 0, 3_600_000L,
                Arrays.asList(SessionMapper.toStored(t, price)));

        SessionHistory history = new SessionHistory(store, "acct", names);
        SessionHistory.TripDetail d = history.tripDetail("s", "t1");

        assertEquals(1, d.pickedUp.size());
        assertEquals(1, d.pickedUp.get(0).quantity);          // 4 looted - 3 eaten = 1 kept
        assertEquals(1000L, d.pickedUp.get(0).gpValue);
        assertEquals(1, d.usedLoot.size());
        assertEquals("Item 385", d.usedLoot.get(0).label);
        assertEquals(3, d.usedLoot.get(0).quantity);
        assertEquals(3000L, d.usedLoot.get(0).gpValue);
        assertEquals(1000L, d.netProfit);                     // 4000 picked - 3000 consumed
    }

    @Test
    public void categoryStatsAreSortedByGpPerHourDescending() throws Exception {
        Path root = Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root, new com.google.gson.Gson());
        ItemKey coins = ItemKey.item(560);
        Map<ItemKey, Long> price = new HashMap<>();
        price.put(coins, 1L);
        save(store, "acct", "v", "Vorkath", "", 0, 3_600_000L,
                Arrays.asList(trip("vt", 0, 3_600_000L, 1, qty(coins, 100), new HashMap<>(),
                        new HashMap<>(), price)));
        save(store, "acct", "z", "Zulrah", "", 0, 3_600_000L,
                Arrays.asList(trip("zt", 0, 3_600_000L, 1, qty(coins, 300), new HashMap<>(),
                        new HashMap<>(), price)));

        SessionHistory history = new SessionHistory(store, "acct", names);
        List<SessionHistory.CategoryStatsView> cats = history.categoryStats();
        assertEquals(2, cats.size());
        assertEquals("Zulrah", cats.get(0).category);
        assertEquals(300L, cats.get(0).gpPerHour);
        assertEquals("Vorkath", cats.get(1).category);
    }

    @Test
    public void recategorizeWritesThroughAndRefilesIntoStats() throws Exception {
        Path root = Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root, new com.google.gson.Gson());
        ItemKey coins = ItemKey.item(560);
        Map<ItemKey, Long> price = new HashMap<>();
        price.put(coins, 1L);
        save(store, "acct", "s", "Vorkath", "old name", 0, 3_600_000L,
                Arrays.asList(trip("t1", 0, 3_600_000L, 1, qty(coins, 100), new HashMap<>(),
                        new HashMap<>(), price)));

        SessionHistory history = new SessionHistory(store, "acct", names);
        history.rename("s", "new name");
        history.recategorize("s", "Boss farming");

        SessionHistory reloaded = new SessionHistory(store, "acct", names);
        assertEquals("new name", reloaded.sessionsNewestFirst().get(0).name);
        List<SessionHistory.CategoryStatsView> cats = reloaded.categoryStats();
        assertEquals(1, cats.size());
        assertEquals("Boss farming", cats.get(0).category);
        assertTrue(reloaded.categories().contains("Boss farming"));
    }

    @Test
    public void tripDetailListsXpPerSkillAlphabetically() throws Exception {
        Path root = Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root, new com.google.gson.Gson());
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

    @Test
    public void tripDetailListsKillsByNpcMostKilledFirst() throws Exception {
        Path root = Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root, new com.google.gson.Gson());
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

    @Test
    public void sessionSummaryExposesPerSessionAverages() throws Exception {
        Path root = Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root, new com.google.gson.Gson());
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

    @Test
    public void categoryDetailListsPerSkillXpAverages() throws Exception {
        Path root = Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root, new com.google.gson.Gson());
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
        assertEquals("Attack", d.xpAverages.get(0).skill);
        assertEquals(200L, d.xpAverages.get(0).avgXpPerTrip);
        assertEquals(400L, d.xpAverages.get(0).xpPerHour);
        assertEquals("Ranged", d.xpAverages.get(1).skill);
        assertEquals(50L, d.xpAverages.get(1).avgXpPerTrip);
        assertEquals(100L, d.xpAverages.get(1).xpPerHour);
    }

    @Test
    public void categoryDetailSeparatesUsedLootFromKeptPickedAndGathered() throws Exception {
        Path root = Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root, new com.google.gson.Gson());
        ItemKey shark = ItemKey.item(385);
        ItemKey logs = ItemKey.item(1511);
        Map<ItemKey, Long> price = new HashMap<>();
        price.put(shark, 10L);
        price.put(logs, 5L);
        // 1 trip: looted 4 sharks (ate 3), gathered 5 logs (burned 2).
        Map<ItemKey, Integer> picked = new HashMap<>();
        picked.put(shark, 4);
        Map<ItemKey, Integer> gathered = new HashMap<>();
        gathered.put(logs, 5);
        Map<ItemKey, Integer> consumed = new HashMap<>();
        consumed.put(shark, 3);
        consumed.put(logs, 2);
        Trip t = new Trip("t1", 0, 3_600_000L, false, new HashMap<>(), new HashMap<>(),
                picked, new HashMap<>(), new HashMap<>(), gathered, consumed, new HashMap<>());
        save(store, "acct", "s", "Mixed", "", 0, 3_600_000L,
                Arrays.asList(SessionMapper.toStored(t, price)));

        SessionHistory history = new SessionHistory(store, "acct", names);
        SessionHistory.CategoryDetail d = history.categoryDetail("Mixed");

        // Picked up shows only what's kept: 1 shark (10gp), not the gross 4.
        assertEquals(1, d.pickedAverages.size());
        assertEquals(1.0, d.pickedAverages.get(0).avgQtyPerTrip, 0.0001);
        assertEquals(10L, d.avgPickedGpPerTrip);
        // Gathered shows kept: 3 logs (15gp).
        assertEquals(1, d.gatheredAverages.size());
        assertEquals(3.0, d.gatheredAverages.get(0).avgQtyPerTrip, 0.0001);
        assertEquals(15L, d.avgGatheredGpPerTrip);
        // Used loot is its own section: 3 sharks (30gp) + 2 logs (10gp) = 40gp.
        assertEquals(2, d.usedLootAverages.size());
        assertEquals(40L, d.avgUsedLootGpPerTrip);
    }

    @Test
    public void categoryDetailAveragesSuppliesPerTripWithTotal() throws Exception {
        Path root = Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root, new com.google.gson.Gson());
        ItemKey coins = ItemKey.item(560);
        ItemKey brew = ItemKey.item(6685);
        Map<ItemKey, Long> price = new HashMap<>();
        price.put(coins, 1L);
        price.put(brew, 1000L);
        List<StoredTrip> trips = new ArrayList<>();
        trips.add(trip("t1", 0, 3_600_000L, 2, qty(coins, 100), new HashMap<>(), qty(brew, 2), price));
        trips.add(trip("t2", 3_600_000L, 7_200_000L, 4, qty(coins, 100), new HashMap<>(), qty(brew, 4), price));
        save(store, "acct", "s", "Vorkath", "", 0, 7_200_000L, trips);

        SessionHistory history = new SessionHistory(store, "acct", names);
        SessionHistory.CategoryDetail d = history.categoryDetail("Vorkath");
        assertEquals(1, d.supplies.size());
        assertEquals("Item 6685", d.supplies.get(0).label);
        assertEquals(3.0, d.supplies.get(0).avgQtyPerTrip, 0.0001);
        assertEquals(3000L, d.supplies.get(0).avgGpPerTrip);
        assertEquals(3000L, d.avgTotalSuppliesGpPerTrip);
        assertEquals(3, d.avgKillsPerTrip, 0.0001);
        // 6000gp supplies over 2h wall-clock -> 3000/hr
        assertEquals(3000L, d.suppliesGpPerHour);
    }

    @Test
    public void categoryDetailAveragesPickedDroppedAndLeftOnGroundPerItem() throws Exception {
        Path root = Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root, new com.google.gson.Gson());
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

    @Test
    public void categoryDetailListsPerNpcKillAveragesMostKilledFirst() throws Exception {
        Path root = Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root, new com.google.gson.Gson());
        Map<String, Integer> kA = new HashMap<>();
        kA.put("Goblin", 20);
        kA.put("Cow", 10);
        Map<String, Integer> kB = new HashMap<>();
        kB.put("Goblin", 20);
        Trip t1 = new Trip("t1", 0, 1_800_000L, false, kA, new HashMap<>(),
                new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
        Trip t2 = new Trip("t2", 1_800_000L, 3_600_000L, false, kB, new HashMap<>(),
                new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
        save(store, "acct", "s", "Vorkath", "", 0, 3_600_000L,
                Arrays.asList(SessionMapper.toStored(t1, new HashMap<>()),
                        SessionMapper.toStored(t2, new HashMap<>())));

        SessionHistory history = new SessionHistory(store, "acct", names);
        SessionHistory.CategoryDetail d = history.categoryDetail("Vorkath");

        assertEquals(2, d.killAverages.size());
        assertEquals("Goblin", d.killAverages.get(0).npc);
        assertEquals(20.0, d.killAverages.get(0).avgPerTrip, 0.0001);
        assertEquals(40.0, d.killAverages.get(0).perHour, 0.0001);
        assertEquals("Cow", d.killAverages.get(1).npc);
        assertEquals(5.0, d.killAverages.get(1).avgPerTrip, 0.0001);
        assertEquals(10.0, d.killAverages.get(1).perHour, 0.0001);
    }

    @Test
    public void categoryDetailAveragesGatheredPerItemAndSplitsGpPerHour() throws Exception {
        Path root = Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root, new com.google.gson.Gson());
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
        SessionStore store = new SessionStore(root, new com.google.gson.Gson());
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

    @Test
    public void normalItemLineCarriesItemIdAndIsNotPotion() throws Exception {
        Path root = Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root, new com.google.gson.Gson());
        ItemKey coins = ItemKey.item(560);
        Map<ItemKey, Long> price = new HashMap<>();
        price.put(coins, 1L);
        save(store, "acct", "s", "Vorkath", "", 0, 3_600_000L,
                Arrays.asList(trip("t1", 0, 3_600_000L, 1, qty(coins, 100),
                        new HashMap<>(), new HashMap<>(), price)));

        SessionHistory history = new SessionHistory(store, "acct", names);
        SessionHistory.ItemLine line = history.tripDetail("s", "t1").pickedUp.get(0);

        assertEquals(Integer.valueOf(560), line.iconItemId);
        assertFalse(line.isPotion);
        assertEquals(1, line.dosesPerPotion);
    }

    @Test
    public void potionLineCarriesIconAndDoseMetadataFromRegistry() throws Exception {
        Path root = Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root, new com.google.gson.Gson());
        ItemKey coins = ItemKey.item(560);
        ItemKey prayer = ItemKey.potion("Prayer potion");
        Map<ItemKey, Long> price = new HashMap<>();
        price.put(coins, 1L);
        price.put(prayer, 100L);
        save(store, "acct", "s", "Vorkath", "", 0, 3_600_000L,
                Arrays.asList(trip("t1", 0, 3_600_000L, 1, qty(coins, 100),
                        new HashMap<>(), qty(prayer, 8), price)));

        PotionRegistry registry = new PotionRegistry();
        registry.observe(2434, "Prayer potion(4)");
        SessionHistory history = new SessionHistory(store, "acct", names, registry);
        SessionHistory.ItemLine line = history.tripDetail("s", "t1").suppliesUsed.get(0);

        assertEquals("Prayer potion", line.label);
        assertTrue(line.isPotion);
        assertEquals(Integer.valueOf(2434), line.iconItemId);
        assertEquals(4, line.dosesPerPotion);
    }

    @Test
    public void unknownPotionHasNoIconAndDefaultsToFourDoses() throws Exception {
        Path root = Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root, new com.google.gson.Gson());
        ItemKey coins = ItemKey.item(560);
        ItemKey prayer = ItemKey.potion("Prayer potion");
        Map<ItemKey, Long> price = new HashMap<>();
        price.put(coins, 1L);
        price.put(prayer, 100L);
        save(store, "acct", "s", "Vorkath", "", 0, 3_600_000L,
                Arrays.asList(trip("t1", 0, 3_600_000L, 1, qty(coins, 100),
                        new HashMap<>(), qty(prayer, 8), price)));

        // 3-arg constructor -> empty registry, family never observed.
        SessionHistory history = new SessionHistory(store, "acct", names);
        SessionHistory.ItemLine line = history.tripDetail("s", "t1").suppliesUsed.get(0);

        assertNull(line.iconItemId);
        assertTrue(line.isPotion);
        assertEquals(4, line.dosesPerPotion);
    }

    @Test
    public void supplyAverageCarriesPotionMetadata() throws Exception {
        Path root = Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root, new com.google.gson.Gson());
        ItemKey coins = ItemKey.item(560);
        ItemKey prayer = ItemKey.potion("Prayer potion");
        Map<ItemKey, Long> price = new HashMap<>();
        price.put(coins, 1L);
        price.put(prayer, 100L);
        save(store, "acct", "s", "Vorkath", "", 0, 3_600_000L,
                Arrays.asList(trip("t1", 0, 3_600_000L, 1, qty(coins, 100),
                        new HashMap<>(), qty(prayer, 8), price)));

        PotionRegistry registry = new PotionRegistry();
        registry.observe(2434, "Prayer potion(4)");
        SessionHistory history = new SessionHistory(store, "acct", names, registry);
        SessionHistory.ItemAverage avg = history.categoryDetail("Vorkath").supplies.get(0);

        assertEquals("Prayer potion", avg.label);
        assertTrue(avg.isPotion);
        assertEquals(4, avg.dosesPerPotion);
    }

    @Test
    public void sessionSummaryExposesAvgKillsPerTrip() throws Exception {
        Path root = Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root, new com.google.gson.Gson());
        Map<String, Integer> killsA = new HashMap<>();
        killsA.put("Goblin", 3);
        Map<String, Integer> killsB = new HashMap<>();
        killsB.put("Goblin", 1);
        killsB.put("Cow", 2);
        Trip t1 = new Trip("t1", 0, 1_800_000L, false, killsA, new HashMap<>(),
                new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
        Trip t2 = new Trip("t2", 1_800_000L, 3_600_000L, false, killsB, new HashMap<>(),
                new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
        save(store, "acct", "s", "Vorkath", "", 0, 3_600_000L,
                Arrays.asList(SessionMapper.toStored(t1, new HashMap<>()),
                        SessionMapper.toStored(t2, new HashMap<>())));

        SessionHistory history = new SessionHistory(store, "acct", names);
        SessionHistory.SessionSummary sum = history.sessionsNewestFirst().get(0);

        assertEquals(3.0, sum.avgKillsPerTrip, 0.0001);
    }

    @Test
    public void deleteSessionRemovesItFromTheList() throws Exception {
        Path root = Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root, new com.google.gson.Gson());
        ItemKey coins = ItemKey.item(560);
        Map<ItemKey, Long> price = new HashMap<>();
        price.put(coins, 1L);
        save(store, "acct", "keep", "Vorkath", "morning", 0, 3_600_000L,
                Arrays.asList(trip("t1", 0, 3_600_000L, 5, qty(coins, 100),
                        new HashMap<>(), new HashMap<>(), price)));
        save(store, "acct", "drop", "Zulrah", "evening", 10_000_000L, 13_600_000L,
                Arrays.asList(trip("t2", 10_000_000L, 13_600_000L, 3, qty(coins, 50),
                        new HashMap<>(), new HashMap<>(), price)));

        SessionHistory history = new SessionHistory(store, "acct", names);
        history.deleteSession("drop");

        List<SessionHistory.SessionSummary> list = history.sessionsNewestFirst();
        assertEquals(1, list.size());
        assertEquals("keep", list.get(0).sessionId);
    }

    @Test
    public void deleteTripRemovesOnlyThatTripAndRecomputesStats() throws Exception {
        Path root = Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root, new com.google.gson.Gson());
        ItemKey coins = ItemKey.item(560);
        Map<ItemKey, Long> price = new HashMap<>();
        price.put(coins, 1L);
        save(store, "acct", "s", "Vorkath", "morning", 0, 7_200_000L, Arrays.asList(
                trip("t1", 0, 3_600_000L, 5, qty(coins, 100), new HashMap<>(), new HashMap<>(), price),
                trip("t2", 3_600_000L, 7_200_000L, 3, qty(coins, 50), new HashMap<>(), new HashMap<>(), price)));

        SessionHistory history = new SessionHistory(store, "acct", names);
        history.deleteTrip("s", "t1");

        List<SessionHistory.TripSummary> trips = history.tripsFor("s");
        assertEquals(1, trips.size());
        assertEquals("t2", trips.get(0).tripId);
        // Stats reflect only the surviving trip: net profit is now t2's 50 coins.
        assertEquals(50L, history.sessionsNewestFirst().get(0).netProfit);
    }

    @Test
    public void deletingLastTripDeletesTheWholeSession() throws Exception {
        Path root = Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root, new com.google.gson.Gson());
        ItemKey coins = ItemKey.item(560);
        Map<ItemKey, Long> price = new HashMap<>();
        price.put(coins, 1L);
        save(store, "acct", "s", "Vorkath", "morning", 0, 3_600_000L, Arrays.asList(
                trip("only", 0, 3_600_000L, 5, qty(coins, 100), new HashMap<>(), new HashMap<>(), price)));

        SessionHistory history = new SessionHistory(store, "acct", names);
        history.deleteTrip("s", "only");

        assertTrue(history.sessionsNewestFirst().isEmpty());
    }
}
