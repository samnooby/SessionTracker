package com.sessiontracker.adapter.runelite;

import net.runelite.api.gameval.ItemID;

/**
 * Storage containers whose contents the client cannot read: herb sack, gem bag, coal bag, fish
 * barrel, fish sack barrel and log basket (plus the readable ones, for which this is a harmless
 * belt-and-braces). Their contents never appear in any item container or varbit, so the only
 * signal is the player's own <b>Fill</b> / <b>Empty</b> action on the item. The plugin uses that
 * click to treat the inventory change that follows as a move rather than a consumption or a gain.
 */
public final class StashContainers {

    private static final int[] ITEM_IDS = {
        ItemID.SLAYER_HERB_SACK, ItemID.SLAYER_HERB_SACK_OPEN,
        ItemID.SLAYER_HERB_SACK_SILK, ItemID.SLAYER_HERB_SACK_SILK_OPEN,
        ItemID.GEM_BAG, ItemID.GEM_BAG_OPEN,
        ItemID.COAL_BAG, ItemID.COAL_BAG_OPEN,
        ItemID.FISH_BARREL_CLOSED, ItemID.FISH_BARREL_OPEN,
        ItemID.FISH_SACK_BARREL_CLOSED, ItemID.FISH_SACK_BARREL_OPEN,
        ItemID.LOG_BASKET_CLOSED, ItemID.LOG_BASKET_OPEN,
        ItemID.SEED_BOX, ItemID.SEED_BOX_OPEN,
        ItemID.PLANK_SACK,
    };

    private StashContainers() {
    }

    /** True if clicking {@code option} on item {@code itemId} moves items into or out of a stash. */
    public static boolean isTransfer(int itemId, String option) {
        if (!"Fill".equals(option) && !"Empty".equals(option)) {
            return false;
        }
        for (int id : ITEM_IDS) {
            if (id == itemId) {
                return true;
            }
        }
        return false;
    }
}
