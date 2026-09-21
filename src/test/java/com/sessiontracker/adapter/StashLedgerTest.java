package com.sessiontracker.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/** What the plugin believes is inside containers the client will not let it read. */
public class StashLedgerTest {

    private static final String COAL_BAG = "coal bag";
    private static final String GEM_BAG = "gem bag";
    private static final int COAL = 453;
    private static final int SAPPHIRE = 1623;

    private static Map<Integer, Integer> inventory(Object... pairs) {
        Map<Integer, Integer> m = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((Integer) pairs[i], (Integer) pairs[i + 1]);
        }
        return m;
    }

    @Test
    public void creditsAGatherThatNeverReachedTheInventory() {
        StashLedger ledger = new StashLedger();
        ledger.gathered(COAL_BAG, COAL, 1);

        ledger.settle(inventory(), inventory());

        assertEquals(Integer.valueOf(1), ledger.contents().get(COAL));
    }

    @Test
    public void creditsNothingWhenTheItemLandedInTheInventoryInstead() {
        StashLedger ledger = new StashLedger();
        ledger.gathered(COAL_BAG, COAL, 1);

        // A full bag leaves the coal in the inventory, though the game still prints the message.
        ledger.settle(inventory(), inventory(COAL, 1));

        assertTrue(ledger.contents().isEmpty());
    }

    @Test
    public void creditsOnlyWhatTheInventoryDidNotReceive() {
        StashLedger ledger = new StashLedger();
        ledger.gathered(COAL_BAG, COAL, 3);

        ledger.settle(inventory(COAL, 10), inventory(COAL, 11));

        assertEquals(Integer.valueOf(2), ledger.contents().get(COAL));
    }

    @Test
    public void settlingConsumesTheGathersSoTheyAreNotCountedTwice() {
        StashLedger ledger = new StashLedger();
        ledger.gathered(COAL_BAG, COAL, 1);
        ledger.settle(inventory(), inventory());

        ledger.settle(inventory(), inventory());

        assertEquals(Integer.valueOf(1), ledger.contents().get(COAL));
    }

    @Test
    public void contentsCoverEveryBagAtOnce() {
        StashLedger ledger = new StashLedger();
        ledger.gathered(COAL_BAG, COAL, 2);
        ledger.gathered(GEM_BAG, SAPPHIRE, 1);
        ledger.settle(inventory(), inventory());

        assertEquals(Integer.valueOf(2), ledger.contents().get(COAL));
        assertEquals(Integer.valueOf(1), ledger.contents().get(SAPPHIRE));
    }

    @Test
    public void clearingOneBagLeavesTheOthersAlone() {
        StashLedger ledger = new StashLedger();
        ledger.gathered(COAL_BAG, COAL, 2);
        ledger.gathered(GEM_BAG, SAPPHIRE, 1);
        ledger.settle(inventory(), inventory());

        ledger.clear(COAL_BAG);

        assertTrue(ledger.contents().get(COAL) == null);
        assertEquals(Integer.valueOf(1), ledger.contents().get(SAPPHIRE));
    }

    @Test
    public void clearingEverythingForgetsThePendingGathersToo() {
        StashLedger ledger = new StashLedger();
        ledger.gathered(COAL_BAG, COAL, 2);

        ledger.clearAll();
        ledger.settle(inventory(), inventory());

        assertTrue(ledger.contents().isEmpty());
    }
}
