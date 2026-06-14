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
