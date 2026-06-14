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
}
