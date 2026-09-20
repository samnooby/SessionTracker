package com.sessiontracker.adapter;

import java.util.Locale;

/**
 * The quantity drawn in the corner of an item icon, in the game's own stack style: plain digits
 * below 100K, then thousands, then millions. Per-trip averages keep one decimal place, since
 * "2.5 sharks" is the point of an average, falling back to the stack style when a decimal would
 * be too wide to read on a 36px icon.
 */
public final class StackText {

    private static final long K = 100_000L;
    private static final long M = 10_000_000L;

    /** The widest average that still fits as "999.9"; above this the stack style reads better. */
    private static final double DECIMAL_LIMIT = 1_000.0;

    private StackText() {
    }

    public static String count(long quantity) {
        if (quantity >= M) {
            return (quantity / 1_000_000L) + "M";
        }
        if (quantity >= K) {
            return (quantity / 1_000L) + "K";
        }
        return Long.toString(quantity);
    }

    public static String average(double perTrip) {
        if (perTrip >= DECIMAL_LIMIT) {
            return count(Math.round(perTrip));
        }
        return String.format(Locale.US, "%.1f", perTrip);
    }
}
