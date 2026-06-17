package com.sessiontracker.adapter;

/** Live gp price of a single raw item id. Implemented in Phase 2b via ItemManager. */
public interface ItemPriceSource {
    int price(int itemId);
}
