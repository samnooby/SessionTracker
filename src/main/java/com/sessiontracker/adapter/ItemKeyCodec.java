package com.sessiontracker.adapter;

import com.sessiontracker.core.item.ItemKey;

/** Serializes {@link ItemKey} to/from a compact string token for JSON map keys. */
public final class ItemKeyCodec {

    private static final String ITEM = "item:";
    private static final String POTION = "potion:";

    private ItemKeyCodec() {
    }

    public static String encode(ItemKey key) {
        return key.isPotion() ? POTION + key.potionFamily() : ITEM + key.itemId();
    }

    public static ItemKey decode(String token) {
        if (token.startsWith(ITEM)) {
            return ItemKey.item(Integer.parseInt(token.substring(ITEM.length())));
        }
        if (token.startsWith(POTION)) {
            return ItemKey.potion(token.substring(POTION.length()));
        }
        throw new IllegalArgumentException("Unrecognized ItemKey token: " + token);
    }
}
