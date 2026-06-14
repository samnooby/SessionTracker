package com.goodrunetracker.core;

import com.goodrunetracker.core.item.ItemValuer;
import java.util.List;

/** Aggregated averages across all sessions sharing a category. */
public final class CategoryStats {

    private static final long MILLIS_PER_HOUR = 3_600_000L;

    private final String category;
    private final int sessionCount;
    private final int tripCount;
    private final long gpPerHour;
    private final long xpPerHour;
    private final long avgTripDurationMillis;
    private final long avgNetProfitPerTrip;
    private final long avgMissedPerTrip;
    private final double avgKillsPerTrip;

    private CategoryStats(String category, int sessionCount, int tripCount, long gpPerHour,
                          long xpPerHour, long avgTripDurationMillis, long avgNetProfitPerTrip,
                          long avgMissedPerTrip, double avgKillsPerTrip) {
        this.category = category;
        this.sessionCount = sessionCount;
        this.tripCount = tripCount;
        this.gpPerHour = gpPerHour;
        this.xpPerHour = xpPerHour;
        this.avgTripDurationMillis = avgTripDurationMillis;
        this.avgNetProfitPerTrip = avgNetProfitPerTrip;
        this.avgMissedPerTrip = avgMissedPerTrip;
        this.avgKillsPerTrip = avgKillsPerTrip;
    }

    public static CategoryStats from(String category, List<Session> sessions, ItemValuer valuer) {
        int tripCount = 0;
        long totalWallClock = 0;
        long totalNet = 0;
        long totalXp = 0;
        long totalTripDuration = 0;
        long totalMissed = 0;
        int totalKills = 0;

        for (Session s : sessions) {
            totalWallClock += s.wallClockMillis();
            totalXp += s.totalXp();
            for (Trip t : s.trips()) {
                tripCount++;
                totalNet += t.netProfit(valuer);
                totalTripDuration += t.durationMillis();
                totalMissed += t.missedValue(valuer);
                totalKills += t.totalKills();
            }
        }

        long gpPerHour = totalWallClock <= 0 ? 0 : totalNet * MILLIS_PER_HOUR / totalWallClock;
        long xpPerHour = totalWallClock <= 0 ? 0 : totalXp * MILLIS_PER_HOUR / totalWallClock;
        long avgDuration = tripCount == 0 ? 0 : totalTripDuration / tripCount;
        long avgNet = tripCount == 0 ? 0 : totalNet / tripCount;
        long avgMissed = tripCount == 0 ? 0 : totalMissed / tripCount;
        double avgKills = tripCount == 0 ? 0 : (double) totalKills / tripCount;

        return new CategoryStats(category, sessions.size(), tripCount, gpPerHour, xpPerHour,
                avgDuration, avgNet, avgMissed, avgKills);
    }

    public String category() {
        return category;
    }

    public int sessionCount() {
        return sessionCount;
    }

    public int tripCount() {
        return tripCount;
    }

    public long gpPerHour() {
        return gpPerHour;
    }

    public long xpPerHour() {
        return xpPerHour;
    }

    public long avgTripDurationMillis() {
        return avgTripDurationMillis;
    }

    public long avgNetProfitPerTrip() {
        return avgNetProfitPerTrip;
    }

    public long avgMissedPerTrip() {
        return avgMissedPerTrip;
    }

    public double avgKillsPerTrip() {
        return avgKillsPerTrip;
    }
}
