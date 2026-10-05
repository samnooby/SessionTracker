package com.sessiontracker.core;

import static org.junit.Assert.*;
import com.sessiontracker.core.item.ItemKey;
import com.sessiontracker.core.item.ItemValuer;
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
    public void gpPerHourIsTimeWeightedAcrossSessions() {
        // Session A: 100 gp over 1h -> 100/h ; Session B: 1000 gp over 0.5h -> 2000/h.
        // A naive average of per-session rates would be 1050; the time-weighted rate
        // is total 1100 gp / 1.5h = 733.
        Session a = session("a", "Cat", trip("a1", 0, 3_600_000, 100, 0));
        Session b = session("b", "Cat", trip("b1", 0, 1_800_000, 1000, 0));
        CategoryStats stats = CategoryStats.from("Cat", Arrays.asList(a, b), oneGp);
        assertEquals(2, stats.sessionCount());
        assertEquals(2, stats.tripCount());
        assertEquals(733, stats.gpPerHour());
        assertEquals(550, stats.avgNetProfitPerTrip());
    }

    @Test
    public void averagesSuppliesPerTripPerItem() {
        ItemKey shark = ItemKey.item(385);
        Map<ItemKey, Integer> s1Supplies = new HashMap<>();
        s1Supplies.put(shark, 4);
        Trip a = new Trip("a", 0, 1_800_000, false, new HashMap<>(), new HashMap<>(),
                new HashMap<>(), new HashMap<>(), s1Supplies, new HashMap<>());
        Map<ItemKey, Integer> s2Supplies = new HashMap<>();
        s2Supplies.put(shark, 2);
        Trip b = new Trip("b", 1_800_000, 3_600_000, false, new HashMap<>(), new HashMap<>(),
                new HashMap<>(), new HashMap<>(), s2Supplies, new HashMap<>());
        Session sess = new Session("s1", "acct", "Cat", "", Arrays.asList(a, b));
        CategoryStats stats = CategoryStats.from("Cat", Arrays.asList(sess), oneGp);
        // (4 + 2) / 2 trips = 3.0 sharks per trip
        assertEquals(3.0, stats.avgSuppliesPerTrip().get(shark), 0.0001);
    }

    @Test
    public void aggregateValuesEachTripWithItsOwnValuer() {
        java.util.Map<com.sessiontracker.core.item.ItemKey, Integer> picked = new java.util.HashMap<>();
        picked.put(com.sessiontracker.core.item.ItemKey.item(560), 10);
        Trip a = new Trip("a", 0, 3_600_000L, false, new java.util.HashMap<>(),
                new java.util.HashMap<>(), new java.util.HashMap<>(picked), new java.util.HashMap<>(),
                new java.util.HashMap<>(), new java.util.HashMap<>());
        Trip b = new Trip("b", 3_600_000L, 7_200_000L, false, new java.util.HashMap<>(),
                new java.util.HashMap<>(), new java.util.HashMap<>(picked), new java.util.HashMap<>(),
                new java.util.HashMap<>(), new java.util.HashMap<>());
        Session s = new Session("s", "acct", "Vorkath", "name", java.util.Arrays.asList(a, b));

        java.util.function.Function<Trip, com.sessiontracker.core.item.ItemValuer> perTrip =
                t -> (key, qty) -> (t.id().equals("a") ? 2L : 5L) * qty;

        java.util.Map<String, CategoryStats> out =
                CategoryAggregator.aggregate(java.util.Arrays.asList(s), perTrip);
        CategoryStats stats = out.get("Vorkath");
        assertEquals(2, stats.tripCount());
        assertEquals(35L, stats.avgNetProfitPerTrip());
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

    @Test
    public void averageSessionDurationIsWallClockPerSession() {
        Session a = session("a", "Cat", trip("a1", 0, 3_600_000, 100, 0)); // 1h wall clock
        Session b = session("b", "Cat", trip("b1", 0, 1_800_000, 100, 0)); // 0.5h wall clock
        CategoryStats stats = CategoryStats.from("Cat", Arrays.asList(a, b), oneGp);
        // (3_600_000 + 1_800_000) / 2 sessions
        assertEquals(2_700_000L, stats.avgSessionDurationMillis());
    }

    @Test
    public void averageSessionDurationIsZeroWithNoSessions() {
        CategoryStats stats = CategoryStats.from("Cat", java.util.Collections.emptyList(), oneGp);
        assertEquals(0L, stats.avgSessionDurationMillis());
    }

    private Trip timedTrip(String id, long start, long end, int kills,
                           Map<String, KillTimes> times, long combatMillis) {
        Map<String, Integer> killMap = new HashMap<>();
        killMap.put("Demonic gorilla", kills);
        return new Trip(id, start, end, false, killMap, new HashMap<>(), new HashMap<>(),
                new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(),
                times, combatMillis);
    }

    @Test
    public void killsPerHourIsOverWallClock() {
        Session s1 = session("s1", "Cat",
                trip("a", 0, 1_800_000, 0, 20),
                trip("b", 2_700_000, 3_600_000, 0, 10));
        CategoryStats stats = CategoryStats.from("Cat", Arrays.asList(s1), oneGp);
        assertEquals(30.0, stats.killsPerHour(), 1e-9);
    }

    @Test
    public void killTimesMergeAcrossTripsAndSessions() {
        Map<String, KillTimes> a = new HashMap<>();
        a.put("Demonic gorilla", new KillTimes(2, 100_000, 40_000));
        Map<String, KillTimes> b = new HashMap<>();
        b.put("Demonic gorilla", new KillTimes(1, 35_000, 35_000));
        Session s1 = session("s1", "Cat", timedTrip("a", 0, 600_000, 2, a, 100_000));
        Session s2 = session("s2", "Cat", timedTrip("b", 0, 600_000, 1, b, 35_000));
        KillTimes merged = CategoryStats.from("Cat", Arrays.asList(s1, s2), oneGp)
                .killTimes().get("Demonic gorilla");
        assertEquals(3, merged.count());
        assertEquals(45_000, merged.averageMillis());
        assertEquals(35_000, merged.fastestMillis());
    }

    @Test
    public void combatUptimeIsFightTimeOverWallClockOfTimedSessionsOnly() {
        // Timed session: 30 min fighting across a 1h wall clock (trips 0-30m and 40-60m).
        Session timed = session("s1", "Cat",
                timedTrip("a", 0, 1_800_000, 5, new HashMap<>(), 1_200_000),
                timedTrip("b", 2_400_000, 3_600_000, 5, new HashMap<>(), 600_000));
        // A session saved before fight timing existed must not dilute the uptime.
        Session old = session("s2", "Cat", trip("c", 0, 3_600_000, 0, 10));
        CategoryStats stats = CategoryStats.from("Cat", Arrays.asList(timed, old), oneGp);
        assertTrue(stats.hasCombatTime());
        assertEquals(0.5, stats.combatUptime(), 1e-9);
    }

    @Test
    public void noFightTimeMeansNoUptime() {
        Session s1 = session("s1", "Cat", trip("a", 0, 3_600_000, 0, 10));
        CategoryStats stats = CategoryStats.from("Cat", Arrays.asList(s1), oneGp);
        assertFalse(stats.hasCombatTime());
        assertTrue(stats.killTimes().isEmpty());
    }
}
