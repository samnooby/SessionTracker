package com.sessiontracker.adapter;

/** Formats a duration in millis as a short human string (e.g. "45s", "12m", "1h 23m"). */
public final class DurationFormat {

    private DurationFormat() {
    }

    public static String compact(long ms) {
        if (ms <= 0) {
            return "0m";
        }
        long totalSec = ms / 1000;
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        if (h > 0) {
            return h + "h " + m + "m";
        }
        if (m > 0) {
            return m + "m";
        }
        return totalSec + "s";
    }
}
