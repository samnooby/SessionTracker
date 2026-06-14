package com.goodrunetracker.core.item;

import static org.junit.Assert.*;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntFunction;
import org.junit.Test;

public class CarriedNormalizerTest {

    private final IntFunction<String> names = id -> {
        switch (id) {
            case 2434: return "Prayer potion(4)";
            case 139:  return "Prayer potion(3)";
            case 995:  return "Coins";
            default:   return "Unknown";
        }
    };

    @Test
    public void nonPotionsKeepTheirItemId() {
        Map<Integer, Integer> raw = new HashMap<>();
        raw.put(995, 100);
        Map<ItemKey, Integer> result = CarriedNormalizer.normalize(raw, names);
        assertEquals(Integer.valueOf(100), result.get(ItemKey.item(995)));
    }

    @Test
    public void potionsCollapseToFamilyTotalDoses() {
        Map<Integer, Integer> raw = new HashMap<>();
        raw.put(2434, 2); // 2 x Prayer potion(4) = 8 doses
        raw.put(139, 1);  // 1 x Prayer potion(3) = 3 doses
        Map<ItemKey, Integer> result = CarriedNormalizer.normalize(raw, names);
        assertEquals(Integer.valueOf(11), result.get(ItemKey.potion("Prayer potion")));
        assertFalse(result.containsKey(ItemKey.item(2434)));
    }

    @Test
    public void zeroOrNegativeQuantitiesAreDropped() {
        Map<Integer, Integer> raw = new HashMap<>();
        raw.put(995, 0);
        assertTrue(CarriedNormalizer.normalize(raw, names).isEmpty());
    }
}
