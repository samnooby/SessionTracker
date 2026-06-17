package com.sessiontracker.core;

import static org.junit.Assert.*;
import com.sessiontracker.core.item.ItemKey;
import com.sessiontracker.core.item.ItemValuer;
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
    public void gpPerHourValuesEachTripWithItsOwnValuer() {
        java.util.Map<com.sessiontracker.core.item.ItemKey, Integer> picked = new java.util.HashMap<>();
        picked.put(com.sessiontracker.core.item.ItemKey.item(560), 10);
        Trip a = new Trip("a", 0, 3_600_000L, false, new java.util.HashMap<>(),
                new java.util.HashMap<>(), new java.util.HashMap<>(picked), new java.util.HashMap<>(),
                new java.util.HashMap<>(), new java.util.HashMap<>());
        Trip b = new Trip("b", 3_600_000L, 7_200_000L, false, new java.util.HashMap<>(),
                new java.util.HashMap<>(), new java.util.HashMap<>(picked), new java.util.HashMap<>(),
                new java.util.HashMap<>(), new java.util.HashMap<>());
        Session s = new Session("s", "acct", "cat", "name", java.util.Arrays.asList(a, b));

        java.util.function.Function<Trip, com.sessiontracker.core.item.ItemValuer> perTrip =
                t -> (key, qty) -> (t.id().equals("a") ? 2L : 5L) * qty;

        assertEquals(70L, s.totalNetProfit(perTrip));
        assertEquals(35L, s.gpPerHour(perTrip));
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
