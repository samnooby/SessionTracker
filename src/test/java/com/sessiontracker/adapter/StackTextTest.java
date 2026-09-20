package com.sessiontracker.adapter;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The number drawn in the corner of an item icon, in the game's own stack style. */
public class StackTextTest {

    @Test
    public void smallCountsShowAsPlainDigits() {
        assertEquals("25", StackText.count(25));
    }

    @Test
    public void countsBelowOneHundredThousandKeepEveryDigit() {
        assertEquals("99999", StackText.count(99_999));
    }

    @Test
    public void hundredThousandAndAboveShortensToThousands() {
        assertEquals("100K", StackText.count(100_000));
    }

    @Test
    public void tenMillionAndAboveShortensToMillions() {
        assertEquals("10M", StackText.count(10_000_000));
    }

    @Test
    public void averagesKeepOneDecimalPlace() {
        assertEquals("2.5", StackText.average(2.5));
    }

    @Test
    public void wholeAveragesStillShowTheDecimal() {
        assertEquals("2.0", StackText.average(2.0));
    }

    @Test
    public void averagesTooWideForADecimalDropIt() {
        assertEquals("1234", StackText.average(1_234.4));
    }

    @Test
    public void hugeAveragesShortenLikeAStack() {
        assertEquals("150K", StackText.average(150_000.0));
    }
}
