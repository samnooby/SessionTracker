package com.sessiontracker.adapter;

import com.sessiontracker.core.item.ItemKey;
import com.sessiontracker.core.item.ItemValuer;

/** Values items at current GE prices; potions priced per dose via the registry. */
public final class LiveItemValuer implements ItemValuer {

    private final ItemPriceSource prices;
    private final PotionRegistry potions;

    public LiveItemValuer(ItemPriceSource prices, PotionRegistry potions) {
        this.prices = prices;
        this.potions = potions;
    }

    @Override
    public long value(ItemKey key, int quantity) {
        if (!key.isPotion()) {
            return (long) prices.price(key.itemId()) * quantity;
        }
        return potions.representativeFor(key.potionFamily())
                .map(rep -> (long) (prices.price(rep.itemId()) / rep.dose()) * quantity)
                .orElse(0L);
    }

    /** Value of one unit (or one dose, for potions) — used to capture prices at trip end. */
    public long unitValue(ItemKey key) {
        return value(key, 1);
    }
}
