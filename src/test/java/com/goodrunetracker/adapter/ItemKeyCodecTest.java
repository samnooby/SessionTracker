package com.goodrunetracker.adapter;

import static org.junit.Assert.*;
import com.goodrunetracker.core.item.ItemKey;
import org.junit.Test;

public class ItemKeyCodecTest {

    @Test
    public void roundTripsItemKeys() {
        ItemKey key = ItemKey.item(560);
        assertEquals("item:560", ItemKeyCodec.encode(key));
        assertEquals(key, ItemKeyCodec.decode("item:560"));
    }

    @Test
    public void roundTripsPotionKeys() {
        ItemKey key = ItemKey.potion("Prayer potion");
        assertEquals("potion:Prayer potion", ItemKeyCodec.encode(key));
        assertEquals(key, ItemKeyCodec.decode("potion:Prayer potion"));
    }

    @Test
    public void preservesColonsInPotionFamily() {
        ItemKey key = ItemKey.potion("Weird: brew");
        assertEquals(key, ItemKeyCodec.decode(ItemKeyCodec.encode(key)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownToken() {
        ItemKeyCodec.decode("mystery:1");
    }
}
