package com.goodrunetracker.core;

import com.goodrunetracker.core.item.ItemKey;
import com.goodrunetracker.core.item.ItemValuer;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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
    private final Map<ItemKey, Double> avgSuppliesPerTrip;

    private CategoryStats(String category, int sessionCount, int tripCount, long gpPerHour,
                          long xpPerHour, long avgTripDurationMillis, long avgNetProfitPerTrip,
                          long avgMissedPerTrip, double avgKillsPerTrip,
                          Map<ItemKey, Double> avgSuppliesPerTrip) {
        this.category = category;
        this.sessionCount = sessionCount;
        this.tripCount = tripCount;
        this.gpPerHour = gpPerHour;
        this.xpPerHour = xpPerHour;
        this.avgTripDurationMillis = avgTripDurationMillis;
        this.avgNetProfitPerTrip = avgNetProfitPerTrip;
        this.avgMissedPerTrip = avgMissedPerTrip;
        this.avgKillsPerTrip = avgKillsPerTrip;
        this.avgSuppliesPerTrip = avgSuppliesPerTrip;
    }

    public static CategoryStats from(String category, List<Session> sessions, ItemValuer valuer) {
        return from(category, sessions, t -> valuer);
    }

    public static CategoryStats from(String category, List<Session> sessions,
                                     Function<Trip, ItemValuer> valuerFn) {
        int tripCount = 0;
        long totalWallClock = 0;
        long totalNet = 0;
        long totalXp = 0;
        long totalTripDuration = 0;
        long totalMissed = 0;
        int totalKills = 0;
        Map<ItemKey, Long> totalSupplies = new HashMap<>();

        for (Session s : sessions) {
            totalWallClock += s.wallClockMillis();
            totalXp += s.totalXp();
            for (Trip t : s.trips()) {
                ItemValuer valuer = valuerFn.apply(t);
                tripCount++;
                totalNet += t.netProfit(valuer);
                totalTripDuration += t.durationMillis();
                totalMissed += t.missedValue(valuer);
                totalKills += t.totalKills();
                for (Map.Entry<ItemKey, Integer> e : t.suppliesUsed().entrySet()) {
                    totalSupplies.merge(e.getKey(), e.getValue().longValue(), Long::sum);
                }
            }
        }

        long gpPerHour = totalWallClock <= 0 ? 0 : totalNet * MILLIS_PER_HOUR / totalWallClock;
        long xpPerHour = totalWallClock <= 0 ? 0 : totalXp * MILLIS_PER_HOUR / totalWallClock;
        long avgDuration = tripCount == 0 ? 0 : totalTripDuration / tripCount;
        long avgNet = tripCount == 0 ? 0 : totalNet / tripCount;
        long avgMissed = tripCount == 0 ? 0 : totalMissed / tripCount;
        double avgKills = tripCount == 0 ? 0 : (double) totalKills / tripCount;

        Map<ItemKey, Double> avgSupplies = new HashMap<>();
        if (tripCount > 0) {
            for (Map.Entry<ItemKey, Long> e : totalSupplies.entrySet()) {
                avgSupplies.put(e.getKey(), (double) e.getValue() / tripCount);
            }
        }

        return new CategoryStats(category, sessions.size(), tripCount, gpPerHour, xpPerHour,
                avgDuration, avgNet, avgMissed, avgKills, avgSupplies);
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

    public Map<ItemKey, Double> avgSuppliesPerTrip() {
        return Collections.unmodifiableMap(avgSuppliesPerTrip);
    }
}
