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
