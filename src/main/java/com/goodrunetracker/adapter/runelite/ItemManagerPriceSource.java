package com.goodrunetracker.adapter.runelite;

import com.goodrunetracker.adapter.ItemPriceSource;
import net.runelite.client.game.ItemManager;

/** Live GE prices via RuneLite's ItemManager. */
public final class ItemManagerPriceSource implements ItemPriceSource {

    private final ItemManager itemManager;

    public ItemManagerPriceSource(ItemManager itemManager) {
        this.itemManager = itemManager;
    }

    @Override
    public int price(int itemId) {
        return itemManager.getItemPrice(itemId);
    }
}
