package com.goodrunetracker.adapter;

import java.util.HashMap;
import java.util.Map;

/** Combines the inventory and equipment id->quantity snapshots into one carried map. */
public final class CarriedSnapshots {

    private CarriedSnapshots() {
    }

    public static Map<Integer, Integer> combine(Map<Integer, Integer> inventory,
                                                Map<Integer, Integer> equipment) {
        Map<Integer, Integer> out = new HashMap<>();
        addPositive(out, inventory);
        addPositive(out, equipment);
        return out;
    }

    private static void addPositive(Map<Integer, Integer> out, Map<Integer, Integer> src) {
        for (Map.Entry<Integer, Integer> e : src.entrySet()) {
            if (e.getValue() != null && e.getValue() > 0) {
                out.merge(e.getKey(), e.getValue(), Integer::sum);
            }
        }
    }
}
