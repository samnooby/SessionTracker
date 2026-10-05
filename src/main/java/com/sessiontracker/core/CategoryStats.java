package com.sessiontracker.core;

import com.sessiontracker.core.item.ItemKey;
import com.sessiontracker.core.item.ItemValuer;
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
    private final long avgSessionDurationMillis;
    private final long avgNetProfitPerTrip;
    private final long avgMissedPerTrip;
    private final double avgKillsPerTrip;
    private final Map<ItemKey, Double> avgSuppliesPerTrip;
    private final double killsPerHour;
    private final Map<String, KillTimes> killTimes;
    private final boolean hasCombatTime;
    private final double combatUptime;

    private CategoryStats(String category, int sessionCount, int tripCount, long gpPerHour,
                          long xpPerHour, long avgTripDurationMillis, long avgSessionDurationMillis,
                          long avgNetProfitPerTrip, long avgMissedPerTrip, double avgKillsPerTrip,
                          Map<ItemKey, Double> avgSuppliesPerTrip, double killsPerHour,
                          Map<String, KillTimes> killTimes, boolean hasCombatTime,
                          double combatUptime) {
        this.category = category;
        this.sessionCount = sessionCount;
        this.tripCount = tripCount;
        this.gpPerHour = gpPerHour;
        this.xpPerHour = xpPerHour;
        this.avgTripDurationMillis = avgTripDurationMillis;
        this.avgSessionDurationMillis = avgSessionDurationMillis;
        this.avgNetProfitPerTrip = avgNetProfitPerTrip;
        this.avgMissedPerTrip = avgMissedPerTrip;
        this.avgKillsPerTrip = avgKillsPerTrip;
        this.avgSuppliesPerTrip = avgSuppliesPerTrip;
        this.killsPerHour = killsPerHour;
        this.killTimes = killTimes;
        this.hasCombatTime = hasCombatTime;
        this.combatUptime = combatUptime;
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
        Map<String, KillTimes> killTimes = new HashMap<>();
        // Uptime only over sessions that recorded fight time: older sessions (and pure skilling
        // ones) have none, and would otherwise read as time spent not fighting.
        long timedWallClock = 0;
        long totalCombat = 0;

        for (Session s : sessions) {
            totalWallClock += s.wallClockMillis();
            totalXp += s.totalXp();
            long sessionCombat = 0;
            for (Trip t : s.trips()) {
                sessionCombat += t.combatMillis();
                t.killTimes().forEach((npc, times) -> killTimes.merge(npc, times, KillTimes::plus));
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
            if (sessionCombat > 0) {
                totalCombat += sessionCombat;
                timedWallClock += s.wallClockMillis();
            }
        }

        long gpPerHour = totalWallClock <= 0 ? 0 : totalNet * MILLIS_PER_HOUR / totalWallClock;
        long xpPerHour = totalWallClock <= 0 ? 0 : totalXp * MILLIS_PER_HOUR / totalWallClock;
        long avgDuration = tripCount == 0 ? 0 : totalTripDuration / tripCount;
        long avgSessionDuration = sessions.isEmpty() ? 0 : totalWallClock / sessions.size();
        long avgNet = tripCount == 0 ? 0 : totalNet / tripCount;
        long avgMissed = tripCount == 0 ? 0 : totalMissed / tripCount;
        double avgKills = tripCount == 0 ? 0 : (double) totalKills / tripCount;
        double killsPerHour = totalWallClock <= 0 ? 0
                : (double) totalKills * MILLIS_PER_HOUR / totalWallClock;
        boolean hasCombatTime = timedWallClock > 0;
        double combatUptime = hasCombatTime
                ? Math.min(1.0, (double) totalCombat / timedWallClock) : 0;

        Map<ItemKey, Double> avgSupplies = new HashMap<>();
        if (tripCount > 0) {
            for (Map.Entry<ItemKey, Long> e : totalSupplies.entrySet()) {
                avgSupplies.put(e.getKey(), (double) e.getValue() / tripCount);
            }
        }

        return new CategoryStats(category, sessions.size(), tripCount, gpPerHour, xpPerHour,
                avgDuration, avgSessionDuration, avgNet, avgMissed, avgKills, avgSupplies,
                killsPerHour, killTimes, hasCombatTime, combatUptime);
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

    public long avgSessionDurationMillis() {
        return avgSessionDurationMillis;
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

    /** Kills per hour of session wall clock, banking and travel included. */
    public double killsPerHour() {
        return killsPerHour;
    }

    /** Timed kills per NPC, summed across every trip in the category. */
    public Map<String, KillTimes> killTimes() {
        return Collections.unmodifiableMap(killTimes);
    }

    /** True if any session in the category recorded time spent fighting. */
    public boolean hasCombatTime() {
        return hasCombatTime;
    }

    /** Share of wall clock spent fighting (0..1), over sessions that recorded fight time. */
    public double combatUptime() {
        return combatUptime;
    }
}
