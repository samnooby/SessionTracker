package com.sessiontracker.adapter;

import static org.junit.Assert.*;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import org.junit.Test;

public class RunePouchTest {

    // type 1 -> air rune (556), type 2 -> water rune (555), anything else -> unknown (0)
    private static final IntUnaryOperator RESOLVER = t -> t == 1 ? 556 : t == 2 ? 555 : 0;

    @Test
    public void mapsSlotsToRuneItemIdsAndSkipsUnknownTypes() {
        int[] types = {1, 2, 0, 9};      // slot3 empty type, slot4 unknown type
        int[] amounts = {100, 50, 0, 30}; // slot3 zero amount, slot4 unknown -> skipped
        Map<Integer, Integer> c = RunePouch.contents(types, amounts, RESOLVER);
        assertEquals(2, c.size());
        assertEquals(Integer.valueOf(100), c.get(556));
        assertEquals(Integer.valueOf(50), c.get(555));
    }

    @Test
    public void sumsTheSameRuneAcrossSlotsAndSkipsZeroAmounts() {
        int[] types = {1, 1, 1};
        int[] amounts = {30, 0, 20};     // slot2 zero amount skipped
        Map<Integer, Integer> c = RunePouch.contents(types, amounts, RESOLVER);
        assertEquals(1, c.size());
        assertEquals(Integer.valueOf(50), c.get(556)); // 30 + 20
    }
}
