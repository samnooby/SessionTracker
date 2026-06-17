package com.goodrunetracker.adapter.runelite;

import com.goodrunetracker.adapter.ChargedItems;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;

/**
 * Reads the charge counts of the "1 charge = 1 stored item" weapons and folds them into carried.
 * The initial registry is the Zulrah's-scale weapons (blowpipe / serpentine helm / toxic staff),
 * which all store Zulrah's scales, so their counts sum under one item id.
 */
public final class ChargedItemReader {

    // Zulrah's scales item id -- the consumable these weapons store (1 charge = 1 scale).
    private static final int ZULRAH_SCALE = 12934;

    private static final int[] CHARGE_VARBITS = {
        VarbitID.CHARGES_TOXIC_BLOWPIPE_QUANTITY,
        VarbitID.CHARGES_SERPENTINE_HELM_QUANTITY,
        VarbitID.CHARGES_TOXIC_STAFF_OF_THE_DEAD_QUANTITY,
    };
    private static final int[] ITEM_IDS = {
        ZULRAH_SCALE, ZULRAH_SCALE, ZULRAH_SCALE,
    };

    private final Client client;

    public ChargedItemReader(Client client) {
        this.client = client;
    }

    /** Current charged-item contents as itemId -&gt; quantity (empty if none charged). */
    public Map<Integer, Integer> contents() {
        return ChargedItems.contents(CHARGE_VARBITS, ITEM_IDS, client::getVarbitValue);
    }

    /** True if {@code varbitId} is one of the tracked charge varbits. */
    public static boolean isChargeVarbit(int varbitId) {
        for (int v : CHARGE_VARBITS) {
            if (v == varbitId) {
                return true;
            }
        }
        return false;
    }
}
