package com.sessiontracker.adapter;

import static org.junit.Assert.*;
import org.junit.Test;

public class DurationFormatTest {

    @Test
    public void zeroAndNegativeRenderAsZeroMinutes() {
        assertEquals("0m", DurationFormat.compact(0));
        assertEquals("0m", DurationFormat.compact(-5_000));
    }

    @Test
    public void subSecondRendersAsZeroSeconds() {
        assertEquals("0s", DurationFormat.compact(999));
    }

    @Test
    public void subMinuteRendersSeconds() {
        assertEquals("45s", DurationFormat.compact(45_000));
        assertEquals("59s", DurationFormat.compact(59_999));
    }

    @Test
    public void subHourRendersWholeMinutes() {
        assertEquals("1m", DurationFormat.compact(60_000));
        assertEquals("12m", DurationFormat.compact(12 * 60_000L));
    }

    @Test
    public void hourOrMoreRendersHoursAndMinutes() {
        assertEquals("1h 0m", DurationFormat.compact(3_600_000L));
        assertEquals("1h 23m", DurationFormat.compact(4_980_000L));
    }
}
