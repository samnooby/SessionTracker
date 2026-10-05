package com.sessiontracker.core;

/** How long timed kills of one NPC took: how many, their summed duration, and the fastest. */
public final class KillTimes {

    private final int count;
    private final long totalMillis;
    private final long fastestMillis;

    public KillTimes(int count, long totalMillis, long fastestMillis) {
        this.count = count;
        this.totalMillis = totalMillis;
        this.fastestMillis = fastestMillis;
    }

    /** A single timed kill. */
    public static KillTimes of(long millis) {
        return new KillTimes(1, millis, millis);
    }

    public KillTimes plus(KillTimes other) {
        return new KillTimes(count + other.count, totalMillis + other.totalMillis,
                Math.min(fastestMillis, other.fastestMillis));
    }

    public int count() {
        return count;
    }

    public long totalMillis() {
        return totalMillis;
    }

    public long fastestMillis() {
        return fastestMillis;
    }

    public long averageMillis() {
        return count == 0 ? 0 : totalMillis / count;
    }
}
