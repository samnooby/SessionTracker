package com.sessiontracker.adapter;

import static org.junit.Assert.*;
import org.junit.Test;

public class PotionFormatTest {

    @Test
    public void fractionalDosesShowTwoDecimals() {
        assertEquals("9.25 potions (of 4)", PotionFormat.potions(37, 4));
    }

    @Test
    public void wholePotionsDropDecimals() {
        assertEquals("2 potions (of 4)", PotionFormat.potions(8, 4));
    }

    @Test
    public void trailingZerosAreTrimmed() {
        assertEquals("0.5 potions (of 4)", PotionFormat.potions(2, 4));
        assertEquals("2.5 potions (of 4)", PotionFormat.potions(10, 4));
    }

    @Test
    public void honoursNonFourDenominators() {
        assertEquals("3 potions (of 3)", PotionFormat.potions(9, 3));
    }

    @Test
    public void averageDoubleDosesAreSupported() {
        assertEquals("0.75 potions (of 4)", PotionFormat.potions(3.0, 4));
    }

    @Test
    public void nonPositiveDenominatorIsTreatedAsOne() {
        assertEquals("5 potions (of 1)", PotionFormat.potions(5, 0));
    }
}
