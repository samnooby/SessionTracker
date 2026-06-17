package com.goodrunetracker.adapter;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/**
 * Pure assembly of charged-item charge counts into an itemId -&gt; quantity map. RuneLite-free:
 * each registry entry pairs a charge varbit id with the item id it stores (1 charge = 1 item);
 * {@code varbitToValue} supplies the current charge count. Entries sharing an item id are summed;
 * a value &le; 0 is skipped.
 */
public final class ChargedItems {

    private ChargedItems() {
    }

    public static Map<Integer, Integer> contents(int[] varbitIds, int[] itemIds,
                                                 IntUnaryOperator varbitToValue) {
        Map<Integer, Integer> out = new HashMap<>();
        int n = Math.min(varbitIds.length, itemIds.length);
        for (int i = 0; i < n; i++) {
            int value = varbitToValue.applyAsInt(varbitIds[i]);
            if (value > 0) {
                out.merge(itemIds[i], value, Integer::sum);
            }
        }
        return out;
    }
}
