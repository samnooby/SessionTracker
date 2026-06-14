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
