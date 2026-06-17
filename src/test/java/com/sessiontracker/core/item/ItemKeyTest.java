package com.sessiontracker.core.item;

import static org.junit.Assert.*;
import org.junit.Test;

public class ItemKeyTest {

    @Test
    public void itemKeysWithSameIdAreEqual() {
        assertEquals(ItemKey.item(995), ItemKey.item(995));
        assertEquals(ItemKey.item(995).hashCode(), ItemKey.item(995).hashCode());
    }

    @Test
    public void itemAndPotionKeysAreNeverEqual() {
        assertNotEquals(ItemKey.item(995), ItemKey.potion("Prayer potion"));
    }

    @Test
    public void exposesItsKind() {
        assertTrue(ItemKey.potion("Prayer potion").isPotion());
        assertFalse(ItemKey.item(995).isPotion());
        assertEquals(995, ItemKey.item(995).itemId());
        assertEquals("Prayer potion", ItemKey.potion("Prayer potion").potionFamily());
    }

    @Test
    public void potionKeysWithSameFamilyAreEqual() {
        assertEquals(ItemKey.potion("Prayer potion"), ItemKey.potion("Prayer potion"));
        assertEquals(ItemKey.potion("Prayer potion").hashCode(),
                ItemKey.potion("Prayer potion").hashCode());
    }

    @Test(expected = NullPointerException.class)
    public void potionRejectsNullFamily() {
        ItemKey.potion(null);
    }

    @Test(expected = IllegalStateException.class)
    public void itemIdOnPotionKeyThrows() {
        ItemKey.potion("Prayer potion").itemId();
    }
}
