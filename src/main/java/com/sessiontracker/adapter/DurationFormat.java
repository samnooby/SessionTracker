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

    /**
     * A kill time, precise enough to compare fights: tenths of a second under a minute
     * ("4.8s", since game ticks are 0.6s), otherwise minutes and seconds ("1:45").
     */
    public static String killTime(long ms) {
        if (ms < 0) {
            ms = 0;
        }
        if (ms < 60_000) {
            long tenths = ms / 100;
            return (tenths / 10) + "." + (tenths % 10) + "s";
        }
        long totalSec = ms / 1000;
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long sec = totalSec % 60;
        String mmss = (h > 0 && m < 10 ? "0" : "") + m + ":" + (sec < 10 ? "0" : "") + sec;
        return h > 0 ? h + ":" + mmss : mmss;
    }
}
