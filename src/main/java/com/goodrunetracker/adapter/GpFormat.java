package com.goodrunetracker.adapter;

import java.util.Locale;

/** Formats gp amounts as short human strings (e.g. 1_460_000 -> "1.46M"). */
public final class GpFormat {

    private GpFormat() {
    }

    public static String format(long gp) {
        long abs = Math.abs(gp);
        if (abs >= 1_000_000_000L) {
            return String.format(Locale.US, "%.2fB", gp / 1_000_000_000.0);
        }
        if (abs >= 1_000_000L) {
            return String.format(Locale.US, "%.2fM", gp / 1_000_000.0);
        }
        if (abs >= 1_000L) {
            return String.format(Locale.US, "%.1fK", gp / 1_000.0);
        }
        return Long.toString(gp);
    }
}
