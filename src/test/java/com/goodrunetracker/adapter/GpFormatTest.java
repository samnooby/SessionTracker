package com.goodrunetracker.adapter;

import static org.junit.Assert.*;
import org.junit.Test;

public class GpFormatTest {

    @Test
    public void formatsPlainBelowAThousand() {
        assertEquals("950", GpFormat.format(950));
        assertEquals("0", GpFormat.format(0));
    }

    @Test
    public void formatsThousandsWithOneDecimal() {
        assertEquals("1.5K", GpFormat.format(1_500));
        assertEquals("412.0K", GpFormat.format(412_000));
    }

    @Test
    public void formatsMillionsWithTwoDecimals() {
        assertEquals("1.46M", GpFormat.format(1_460_000));
    }

    @Test
    public void formatsBillionsWithTwoDecimals() {
        assertEquals("3.10B", GpFormat.format(3_100_000_000L));
    }

    @Test
    public void formatsNegatives() {
        assertEquals("-64.0K", GpFormat.format(-64_000));
    }
}
