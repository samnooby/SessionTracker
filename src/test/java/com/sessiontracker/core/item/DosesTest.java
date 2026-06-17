package com.sessiontracker.core.item;

import static org.junit.Assert.*;
import java.util.Optional;
import org.junit.Test;

public class DosesTest {

    @Test
    public void parsesPotionDoseAndFamily() {
        Optional<DoseForm> form = Doses.parse("Prayer potion(4)");
        assertTrue(form.isPresent());
        assertEquals("Prayer potion", form.get().family());
        assertEquals(4, form.get().dose());
    }

    @Test
    public void parsesSingleDigitDoses() {
        assertEquals(2, Doses.parse("Saradomin brew(2)").get().dose());
    }

    @Test
    public void nonDosedItemsReturnEmpty() {
        assertFalse(Doses.parse("Abyssal whip").isPresent());
        assertFalse(Doses.parse("Clue scroll (easy)").isPresent());
    }

    @Test
    public void nullNameReturnsEmpty() {
        assertFalse(Doses.parse(null).isPresent());
    }
}
