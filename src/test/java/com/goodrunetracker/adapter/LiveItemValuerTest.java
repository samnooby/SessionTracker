package com.goodrunetracker.adapter;

import static org.junit.Assert.*;
import com.goodrunetracker.core.item.ItemKey;
import org.junit.Test;

public class LiveItemValuerTest {

    private final ItemPriceSource pricesEqualId = itemId -> itemId;

    @Test
    public void pricesNormalItemsByGePriceTimesQuantity() {
        LiveItemValuer valuer = new LiveItemValuer(pricesEqualId, new PotionRegistry());
        assertEquals(300, valuer.value(ItemKey.item(100), 3));
    }

    @Test
    public void pricesPotionsPerDoseViaRegistry() {
        PotionRegistry registry = new PotionRegistry();
        registry.observe(1000, "Prayer potion(4)");
        LiveItemValuer valuer = new LiveItemValuer(pricesEqualId, registry);
        assertEquals(1000, valuer.value(ItemKey.potion("Prayer potion"), 4));
        assertEquals(250, valuer.unitValue(ItemKey.potion("Prayer potion")));
    }

    @Test
    public void unknownPotionFamilyValuesToZero() {
        LiveItemValuer valuer = new LiveItemValuer(pricesEqualId, new PotionRegistry());
        assertEquals(0, valuer.value(ItemKey.potion("Mystery brew"), 4));
    }

    @Test
    public void perDosePriceFloorsTowardZero() {
        PotionRegistry registry = new PotionRegistry();
        registry.observe(2434, "Prayer potion(4)"); // rep id 2434, dose 4
        ItemPriceSource prices = id -> id == 2434 ? 1001 : 0; // 1001 / 4 = 250 floored
        LiveItemValuer valuer = new LiveItemValuer(prices, registry);
        assertEquals(250, valuer.unitValue(ItemKey.potion("Prayer potion")));
        assertEquals(1000, valuer.value(ItemKey.potion("Prayer potion"), 4)); // 250*4, not 1001
    }
}
