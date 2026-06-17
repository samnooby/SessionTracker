package com.sessiontracker.core;

import static org.junit.Assert.*;
import com.sessiontracker.core.item.ItemKey;
import com.sessiontracker.core.item.ItemValuer;
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
    public void rebaselineAbsorbsDepositsWithoutCountingThemAsSupplies() {
        TripLedger ledger = new TripLedger();
        ledger.updateCarried(carried());                       // baseline empty
        ledger.updateCarried(carried(ItemKey.item(1521), 28)); // gathered 28 oak logs
        ledger.rebaseline(carried());                          // banked all 28 (deposit)
        ledger.updateCarried(carried(ItemKey.item(1521), 1));  // chopped 1 more after the bank
        Trip trip = ledger.build("t1", 0, 60_000, false);
        assertEquals(Integer.valueOf(29), trip.gathered().get(ItemKey.item(1521))); // deposit ignored, 28+1
        assertTrue(trip.suppliesUsed().isEmpty());
    }

    @Test
    public void rebaselineAbsorbsWithdrawalsWithoutCountingThemAsGathered() {
        TripLedger ledger = new TripLedger();
        ledger.updateCarried(carried());                       // baseline empty
        ledger.rebaseline(carried(ItemKey.item(385), 3));      // withdrew 3 sharks at the bank
        ledger.updateCarried(carried(ItemKey.item(385), 1));   // ate 2 after leaving
        Trip trip = ledger.build("t1", 0, 60_000, false);
        assertNull(trip.gathered().get(ItemKey.item(385)));    // withdrawal not gathered
        assertEquals(Integer.valueOf(2), trip.suppliesUsed().get(ItemKey.item(385))); // brought food eaten
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
    public void reportsTheFirstGatheredItem() {
        TripLedger ledger = new TripLedger();
        ledger.updateCarried(carried());
        ledger.updateCarried(carried(ItemKey.item(1521), 1));                       // first gather: oak log
        ledger.updateCarried(carried(ItemKey.item(1521), 1, ItemKey.item(377), 1)); // then a raw fish
        assertEquals(ItemKey.item(1521), ledger.firstGathered());
    }

    @Test
    public void firstGatheredIsNullWhenOnlyLootIsPickedUp() {
        TripLedger ledger = new TripLedger();
        ledger.updateCarried(carried());
        ledger.recordKill("x", carried(ItemKey.item(560), 10));
        ledger.updateCarried(carried(ItemKey.item(560), 10)); // pure loot pickup, not a gather
        assertNull(ledger.firstGathered());
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
        ledger.updateCarried(carried(ItemKey.item(560), 80));  // used 20 (of looted -> consumedLoot)
        Trip trip = ledger.build("t1", 0, 60_000, false);
        assertEquals(100, trip.pickedUpValue(oneGp));
        assertEquals(0, trip.suppliesValue(oneGp));            // consumed items were looted, not brought
        assertEquals(20, trip.consumedLootValue(oneGp));
        assertEquals(80, trip.netProfit(oneGp));
    }

    @Test
    public void killsBeforeFirstCarriedSnapshotAreAllMissed() {
        TripLedger ledger = new TripLedger();
        ledger.recordKill("x", carried(ItemKey.item(560), 100));
        Trip trip = ledger.build("t1", 0, 60_000, false);
        assertEquals(Integer.valueOf(100), trip.missed().get(ItemKey.item(560)));
        assertTrue(trip.pickedUp().isEmpty());
    }

    @Test
    public void gainBeyondGroundPoolCountsOnlyPoolAmountAsPickup() {
        TripLedger ledger = new TripLedger();
        ledger.updateCarried(carried()); // baseline empty
        ledger.recordKill("x", carried(ItemKey.item(560), 30));
        ledger.updateCarried(carried(ItemKey.item(560), 50)); // gained 50, only 30 dropped
        Trip trip = ledger.build("t1", 0, 60_000, false);
        assertEquals(Integer.valueOf(30), trip.pickedUp().get(ItemKey.item(560)));
        assertNull(trip.missed().get(ItemKey.item(560)));
    }

    @Test
    public void zeroQuantityDropsAreIgnored() {
        TripLedger ledger = new TripLedger();
        ledger.recordKill("x", carried(ItemKey.item(560), 0));
        Trip trip = ledger.build("t1", 0, 60_000, false);
        assertEquals(1, trip.totalKills());
        assertTrue(trip.dropped().isEmpty());
    }

    @Test
    public void missedValueUsesTheValuer() {
        TripLedger ledger = new TripLedger();
        ledger.updateCarried(carried());
        ledger.recordKill("x", carried(ItemKey.item(560), 100));
        ledger.updateCarried(carried(ItemKey.item(560), 60));
        Trip trip = ledger.build("t1", 0, 60_000, false);
        assertEquals(40, trip.missedValue(oneGp));
    }

    @Test
    public void droppingALootedItemReversesThePickupBackToMissed() {
        TripLedger ledger = new TripLedger();
        ledger.updateCarried(carried());                                   // baseline empty
        ledger.recordKill("x", carried(ItemKey.item(560), 100));           // dropped 100
        ledger.updateCarried(carried(ItemKey.item(560), 100));             // pick up all 100
        java.util.Set<ItemKey> dropped = new java.util.HashSet<>();
        dropped.add(ItemKey.item(560));
        ledger.updateCarried(carried(), dropped);                          // drop all 100
        Trip trip = ledger.build("t1", 0, 60_000, false);
        assertNull(trip.pickedUp().get(ItemKey.item(560)));
        assertEquals(Integer.valueOf(100), trip.missed().get(ItemKey.item(560)));
        assertTrue(trip.suppliesUsed().isEmpty());
    }

    @Test
    public void droppingABroughtItemCountsAsSupply() {
        TripLedger ledger = new TripLedger();
        ledger.updateCarried(carried(ItemKey.item(560), 5));               // brought 5, no kill
        java.util.Set<ItemKey> dropped = new java.util.HashSet<>();
        dropped.add(ItemKey.item(560));
        ledger.updateCarried(carried(), dropped);                          // drop all 5
        Trip trip = ledger.build("t1", 0, 60_000, false);
        assertEquals(Integer.valueOf(5), trip.suppliesUsed().get(ItemKey.item(560)));
    }

    @Test
    public void consumingALootedItemIsNotASupply() {
        // Loot 4 sharks then eat 3: the 3 eaten were free this trip, not a supply we brought.
        TripLedger ledger = new TripLedger();
        ledger.updateCarried(carried());
        ledger.recordKill("x", carried(ItemKey.item(385), 4));             // dropped 4 sharks
        ledger.updateCarried(carried(ItemKey.item(385), 4));               // pick up 4
        ledger.updateCarried(carried(ItemKey.item(385), 1));               // eat 3 (no drop set)
        Trip trip = ledger.build("t1", 0, 60_000, false);
        assertEquals(Integer.valueOf(4), trip.pickedUp().get(ItemKey.item(385))); // gross loot preserved
        assertTrue(trip.suppliesUsed().isEmpty());                          // nothing brought was spent
        assertEquals(Integer.valueOf(3), trip.consumedLoot().get(ItemKey.item(385)));
        assertEquals(1, trip.netProfit(oneGp));                             // kept 1 of 4
    }

    @Test
    public void buryingLootedBonesCostsNothingAndIsNotLeftOnGround() {
        // The motivating bug: loot bones, bury them -> no supply charge, not "left on ground", net 0.
        ItemKey bones = ItemKey.item(526);
        TripLedger ledger = new TripLedger();
        ledger.updateCarried(carried());
        ledger.recordKill("x", carried(bones, 1));
        ledger.updateCarried(carried(bones, 1));                            // pick up the bones
        ledger.updateCarried(carried());                                   // bury them (no drop set)
        Trip trip = ledger.build("t1", 0, 60_000, false);
        assertTrue(trip.suppliesUsed().isEmpty());
        assertNull(trip.missed().get(bones));                              // not left on the ground
        assertEquals(Integer.valueOf(1), trip.consumedLoot().get(bones));
        assertEquals(0, trip.netProfit(oneGp));
    }

    @Test
    public void consumingAGatheredItemIsNotASupply() {
        TripLedger ledger = new TripLedger();
        ledger.updateCarried(carried());
        ledger.updateCarried(carried(ItemKey.item(1511), 5));              // chop 5 logs (gathered)
        ledger.updateCarried(carried(ItemKey.item(1511), 3));              // burn/use 2 (no drop set)
        Trip trip = ledger.build("t1", 0, 60_000, false);
        assertEquals(Integer.valueOf(5), trip.gathered().get(ItemKey.item(1511))); // gross gathered preserved
        assertTrue(trip.suppliesUsed().isEmpty());
        assertEquals(Integer.valueOf(2), trip.consumedLoot().get(ItemKey.item(1511)));
        assertEquals(3, trip.netProfit(oneGp));
    }

    @Test
    public void keptViewsExcludeConsumedLootButLeaveGrossIntact() {
        // Loot 4 sharks, eat 3: gross picked stays 4, but "kept" is the 1 you still have.
        TripLedger ledger = new TripLedger();
        ledger.updateCarried(carried());
        ledger.recordKill("x", carried(ItemKey.item(385), 4));
        ledger.updateCarried(carried(ItemKey.item(385), 4));
        ledger.updateCarried(carried(ItemKey.item(385), 1));
        Trip trip = ledger.build("t1", 0, 60_000, false);
        assertEquals(Integer.valueOf(4), trip.pickedUp().get(ItemKey.item(385)));     // gross intact
        assertEquals(Integer.valueOf(1), trip.pickedUpKept().get(ItemKey.item(385))); // kept
        assertEquals(1, trip.pickedUpKeptValue(oneGp));
    }

    @Test
    public void keptGatheredExcludesConsumedGatheredItems() {
        // Cut 5 logs, burn 2: gathered-kept is 3.
        TripLedger ledger = new TripLedger();
        ledger.updateCarried(carried());
        ledger.updateCarried(carried(ItemKey.item(1511), 5));
        ledger.updateCarried(carried(ItemKey.item(1511), 3));
        Trip trip = ledger.build("t1", 0, 60_000, false);
        assertEquals(Integer.valueOf(5), trip.gathered().get(ItemKey.item(1511)));     // gross intact
        assertEquals(Integer.valueOf(3), trip.gatheredKept().get(ItemKey.item(1511))); // kept
        assertEquals(3, trip.gatheredKeptValue(oneGp));
    }

    @Test
    public void consumingMoreThanAcquiredChargesOnlyTheExcessAsSupply() {
        // Brought 2 sharks, loot 2 more, eat 3: 2 cancel against loot, 1 is a real supply.
        TripLedger ledger = new TripLedger();
        ledger.updateCarried(carried(ItemKey.item(385), 2));               // brought 2
        ledger.recordKill("x", carried(ItemKey.item(385), 2));             // dropped 2
        ledger.updateCarried(carried(ItemKey.item(385), 4));               // pick up the 2 -> total 4
        ledger.updateCarried(carried(ItemKey.item(385), 1));               // eat 3
        Trip trip = ledger.build("t1", 0, 60_000, false);
        assertEquals(Integer.valueOf(2), trip.pickedUp().get(ItemKey.item(385)));
        assertEquals(Integer.valueOf(2), trip.consumedLoot().get(ItemKey.item(385)));
        assertEquals(Integer.valueOf(1), trip.suppliesUsed().get(ItemKey.item(385)));
        assertEquals(-1, trip.netProfit(oneGp));                           // picked 2 - consumed 2 - supply 1
    }

    @Test
    public void partlyLootedPartlyBroughtDropSplitsCorrectly() {
        TripLedger ledger = new TripLedger();
        ledger.updateCarried(carried(ItemKey.item(560), 2));               // brought 2
        ledger.recordKill("x", carried(ItemKey.item(560), 3));             // dropped 3
        ledger.updateCarried(carried(ItemKey.item(560), 5));               // pick up the 3 -> total 5
        java.util.Set<ItemKey> dropped = new java.util.HashSet<>();
        dropped.add(ItemKey.item(560));
        ledger.updateCarried(carried(), dropped);                          // drop all 5
        Trip trip = ledger.build("t1", 0, 60_000, false);
        assertEquals(Integer.valueOf(3), trip.missed().get(ItemKey.item(560)));
        assertEquals(Integer.valueOf(2), trip.suppliesUsed().get(ItemKey.item(560)));
        assertNull(trip.pickedUp().get(ItemKey.item(560)));
    }

    @Test
    public void droppingABroughtItemThenPickingItBackUpNetsToZero() {
        TripLedger ledger = new TripLedger();
        ledger.updateCarried(carried(ItemKey.item(1265), 1));              // brought a pickaxe
        java.util.Set<ItemKey> dropped = new java.util.HashSet<>();
        dropped.add(ItemKey.item(1265));
        ledger.updateCarried(carried(), dropped);                          // drop it -> supply 1
        ledger.updateCarried(carried(ItemKey.item(1265), 1));              // pick it back up
        Trip trip = ledger.build("t1", 0, 60_000, false);
        assertTrue(trip.suppliesUsed().isEmpty());                         // supply reversed
        assertTrue(trip.pickedUp().isEmpty());                             // not loot either
    }

    @Test
    public void brokenWhenDroppedBroughtItemIsNotRepickedStaysASupply() {
        TripLedger ledger = new TripLedger();
        ledger.updateCarried(carried(ItemKey.item(1265), 1));              // brought a pickaxe
        java.util.Set<ItemKey> dropped = new java.util.HashSet<>();
        dropped.add(ItemKey.item(1265));
        ledger.updateCarried(carried(), dropped);                          // drop and leave it
        Trip trip = ledger.build("t1", 0, 60_000, false);
        assertEquals(Integer.valueOf(1), trip.suppliesUsed().get(ItemKey.item(1265)));
    }

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
}
