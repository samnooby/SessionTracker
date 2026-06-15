package com.goodrunetracker.adapter;

import static org.junit.Assert.*;
import com.goodrunetracker.core.Trip;
import com.goodrunetracker.core.item.ItemKey;
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
        SessionStore store = new SessionStore(root);
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
        SessionStore store = new SessionStore(root);
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
        SessionStore store = new SessionStore(root);
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
}
