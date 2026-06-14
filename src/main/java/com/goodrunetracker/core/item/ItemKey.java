package com.goodrunetracker.core.item;

import java.util.Objects;

/** Identifies a tracked quantity: either a normal item (by id) or a potion family (by name). */
public final class ItemKey {

    private final int itemId;          // -1 for potion-family keys
    private final String potionFamily; // null for normal-item keys

    private ItemKey(int itemId, String potionFamily) {
        this.itemId = itemId;
        this.potionFamily = potionFamily;
    }

    public static ItemKey item(int itemId) {
        return new ItemKey(itemId, null);
    }

    public static ItemKey potion(String family) {
        return new ItemKey(-1, Objects.requireNonNull(family));
    }

    public boolean isPotion() {
        return potionFamily != null;
    }

    public int itemId() {
        return itemId;
    }

    public String potionFamily() {
        return potionFamily;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ItemKey)) {
            return false;
        }
        ItemKey other = (ItemKey) o;
        return itemId == other.itemId && Objects.equals(potionFamily, other.potionFamily);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId, potionFamily);
    }

    @Override
    public String toString() {
        return isPotion() ? "potion:" + potionFamily : "item:" + itemId;
    }
}
