package com.goodrunetracker.adapter;

import com.goodrunetracker.core.item.ItemKey;
import com.goodrunetracker.core.item.ItemValuer;
import java.util.HashMap;
import java.util.Map;

/** Values items at unit prices captured when a trip ended (stable history). */
public final class FrozenItemValuer implements ItemValuer {

    private final Map<ItemKey, Long> unitPrices;

    public FrozenItemValuer(Map<ItemKey, Long> unitPrices) {
        this.unitPrices = new HashMap<>(unitPrices);
    }

    @Override
    public long value(ItemKey key, int quantity) {
        return unitPrices.getOrDefault(key, 0L) * quantity;
    }
}
