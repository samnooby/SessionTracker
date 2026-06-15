package com.goodrunetracker.adapter;

import static org.junit.Assert.*;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class CarriedSnapshotsTest {

    private Map<Integer, Integer> map(int... idQtyPairs) {
        Map<Integer, Integer> m = new HashMap<>();
        for (int i = 0; i < idQtyPairs.length; i += 2) {
            m.put(idQtyPairs[i], idQtyPairs[i + 1]);
        }
        return m;
    }

    @Test
    public void sumsSharedIdsAcrossContainers() {
        Map<Integer, Integer> result = CarriedSnapshots.combine(map(884, 1000), map(884, 500));
        assertEquals(Integer.valueOf(1500), result.get(884));
    }

    @Test
    public void mergesDistinctIds() {
        Map<Integer, Integer> result = CarriedSnapshots.combine(map(995, 100), map(4151, 1));
        assertEquals(Integer.valueOf(100), result.get(995));
        assertEquals(Integer.valueOf(1), result.get(4151));
    }

    @Test
    public void dropsNonPositiveQuantities() {
        Map<Integer, Integer> result = CarriedSnapshots.combine(map(995, 0, 560, 5), map(1, -3));
        assertFalse(result.containsKey(995));
        assertFalse(result.containsKey(1));
        assertEquals(Integer.valueOf(5), result.get(560));
    }
}
