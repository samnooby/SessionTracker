package com.sessiontracker.adapter.runelite;

import com.sessiontracker.adapter.ChargedItems;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

/**
 * Reads the plank sack's per-plank-type counts (one varbit each) and folds them into carried, so
 * filling the sack is net zero and building from it is a genuine plank decrease.
 */
public final class PlankSackReader {

    private static final int[] VARBITS = {
        VarbitID.PLANK_SACK_PLAIN,
        VarbitID.PLANK_SACK_OAK,
        VarbitID.PLANK_SACK_TEAK,
        VarbitID.PLANK_SACK_MAHOGANY,
        VarbitID.PLANK_SACK_CAMPHOR,
        VarbitID.PLANK_SACK_IRONWOOD,
        VarbitID.PLANK_SACK_ROSEWOOD,
    };
    private static final int[] ITEM_IDS = {
        ItemID.WOODPLANK,
        ItemID.PLANK_OAK,
        ItemID.PLANK_TEAK,
        ItemID.PLANK_MAHOGANY,
        ItemID.PLANK_CAMPHOR,
        ItemID.PLANK_IRONWOOD,
        ItemID.PLANK_ROSEWOOD,
    };

    private final Client client;

    public PlankSackReader(Client client) {
        this.client = client;
    }

    /** Current plank sack contents as plankItemId -&gt; quantity (empty if no sack or an empty one). */
    public Map<Integer, Integer> contents() {
        return ChargedItems.contents(VARBITS, ITEM_IDS, client::getVarbitValue);
    }

    /** True if {@code varbitId} is one of the plank sack count varbits. */
    public static boolean isPlankSackVarbit(int varbitId) {
        for (int v : VARBITS) {
            if (v == varbitId) {
                return true;
            }
        }
        return false;
    }
}
