package com.goodrunetracker.adapter;

import static org.junit.Assert.*;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import org.junit.Test;

public class ChargedItemsTest {

    // varbit 10 -> 1000, 11 -> 50, 12 -> 0, anything else -> -5
    private static final IntUnaryOperator VALUE = id -> id == 10 ? 1000 : id == 11 ? 50 : id == 12 ? 0 : -5;

    @Test
    public void mapsEachEntryToItsItemAndSumsSharedItems() {
        int[] varbits = {10, 11};
        int[] items = {12934, 12934}; // both store Zulrah's scales -> sum
        Map<Integer, Integer> c = ChargedItems.contents(varbits, items, VALUE);
        assertEquals(1, c.size());
        assertEquals(Integer.valueOf(1050), c.get(12934)); // 1000 + 50
    }

    @Test
    public void skipsZeroAndNegativeValues() {
        int[] varbits = {12, 99, 10}; // 12 -> 0, 99 -> -5, 10 -> 1000
        int[] items = {555, 556, 12934};
        Map<Integer, Integer> c = ChargedItems.contents(varbits, items, VALUE);
        assertEquals(1, c.size());
        assertEquals(Integer.valueOf(1000), c.get(12934));
    }
}
