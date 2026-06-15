package com.goodrunetracker.adapter;

import static org.junit.Assert.*;
import com.goodrunetracker.core.item.ItemKey;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class FrozenItemValuerTest {

    @Test
    public void valuesAtCapturedUnitPrices() {
        Map<ItemKey, Long> prices = new HashMap<>();
        prices.put(ItemKey.item(560), 200L);
        FrozenItemValuer valuer = new FrozenItemValuer(prices);
        assertEquals(2000, valuer.value(ItemKey.item(560), 10));
    }

    @Test
    public void unknownKeyValuesToZero() {
        FrozenItemValuer valuer = new FrozenItemValuer(new HashMap<>());
        assertEquals(0, valuer.value(ItemKey.item(999), 5));
    }
}
