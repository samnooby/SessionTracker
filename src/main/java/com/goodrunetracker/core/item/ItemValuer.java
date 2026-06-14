package com.goodrunetracker.core.item;

/** Returns the gp value of a quantity of an {@link ItemKey}. Implemented outside the core. */
public interface ItemValuer {
    long value(ItemKey key, int quantity);
}
