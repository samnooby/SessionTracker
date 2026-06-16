package com.goodrunetracker.adapter;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/**
 * Pure assembly of rune-pouch slot data into a runeItemId -&gt; quantity map. RuneLite-free:
 * the per-slot type value is resolved to a rune item id via the injected {@code typeToItemId}
 * (a resolver returning &le; 0 means "unknown type", and that slot is skipped).
 */
public final class RunePouch {

    private RunePouch() {
    }

    public static Map<Integer, Integer> contents(int[] types, int[] amounts,
                                                 IntUnaryOperator typeToItemId) {
        Map<Integer, Integer> out = new HashMap<>();
        int slots = Math.min(types.length, amounts.length);
        for (int i = 0; i < slots; i++) {
            int amount = amounts[i];
            if (amount <= 0) {
                continue;
            }
            int itemId = typeToItemId.applyAsInt(types[i]);
            if (itemId <= 0) {
                continue;
            }
            out.merge(itemId, amount, Integer::sum);
        }
        return out;
    }
}
