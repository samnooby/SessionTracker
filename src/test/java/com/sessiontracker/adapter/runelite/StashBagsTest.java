package com.sessiontracker.adapter.runelite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.OptionalInt;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/** Each opaque container, and the message the game prints as it swallows something. */
public class StashBagsTest {

    private static OptionalInt credit(int openItemId, String message) {
        return StashBags.byOpenItemId(openItemId)
                .map(bag -> bag.match(message))
                .orElse(OptionalInt.empty());
    }

    @Test
    public void coalBagTakesMinedCoal() {
        assertEquals(OptionalInt.of(ItemID.COAL),
                credit(ItemID.COAL_BAG_OPEN, "You manage to mine some coal."));
    }

    @Test
    public void gemBagTakesAMinedGem() {
        assertEquals(OptionalInt.of(ItemID.UNCUT_SAPPHIRE),
                credit(ItemID.GEM_BAG_OPEN, "You just found a Sapphire!"));
    }

    @Test
    public void gemSackTakesTheGemsOnlyItHolds() {
        assertEquals(OptionalInt.of(ItemID.UNCUT_JADE),
                credit(ItemID.GEM_SACK_OPEN, "You just mined a piece of Jade!"));
    }

    @Test
    public void herbSackTakesAHerbPickedUp() {
        assertEquals(OptionalInt.of(ItemID.UNIDENTIFIED_RANARR),
                credit(ItemID.SLAYER_HERB_SACK_OPEN,
                        "You put the Grimy ranarr herb into your herb sack."));
    }

    @Test
    public void fishBarrelTakesACaughtFish() {
        assertEquals(OptionalInt.of(ItemID.RAW_SHARK),
                credit(ItemID.FISH_BARREL_OPEN, "You catch a shark."));
    }

    @Test
    public void logBasketTakesChoppedLogsAndKeepsTheTreeApart() {
        assertEquals(OptionalInt.of(ItemID.YEW_LOGS),
                credit(ItemID.LOG_BASKET_OPEN, "You get some yew logs."));
        assertEquals(OptionalInt.of(ItemID.LOGS),
                credit(ItemID.LOG_BASKET_OPEN, "You get some logs."));
    }

    @Test
    public void aClosedBagIsNotOpenSoNothingIsCredited() {
        assertFalse(StashBags.byOpenItemId(ItemID.COAL_BAG).isPresent());
    }

    @Test
    public void aMessageFromAnotherSkillIsIgnored() {
        assertFalse(credit(ItemID.COAL_BAG_OPEN, "You get some yew logs.").isPresent());
    }

    @Test
    public void anyFormOfABagIsRecognisedSoAManualTransferCanForgetIt() {
        assertTrue(StashBags.byAnyItemId(ItemID.COAL_BAG).isPresent());
        assertTrue(StashBags.byAnyItemId(ItemID.COAL_BAG_OPEN).isPresent());
        assertEquals(StashBags.byAnyItemId(ItemID.COAL_BAG).get().name(),
                StashBags.byAnyItemId(ItemID.COAL_BAG_OPEN).get().name());
    }
}
