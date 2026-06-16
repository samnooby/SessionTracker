package com.goodrunetracker.adapter;

import com.goodrunetracker.core.CategoryStats;
import com.goodrunetracker.core.Session;
import com.goodrunetracker.core.Trip;
import com.goodrunetracker.core.item.ItemKey;
import com.goodrunetracker.core.item.ItemValuer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * Swing-free, RuneLite-free read/edit API over stored sessions. Every query loads the
 * active account's sessions from {@link SessionStore}, values them with each trip's own
 * captured prices, and returns immutable view-model carriers the panel renders.
 */
public final class SessionHistory {

    private static final long MILLIS_PER_HOUR = 3_600_000L;

    private final SessionStore store;
    private final String accountHash;
    private final IntFunction<String> names;

    public SessionHistory(SessionStore store, String accountHash, IntFunction<String> names) {
        this.store = store;
        this.accountHash = accountHash;
        this.names = names;
    }

    public List<SessionSummary> sessionsNewestFirst() {
        List<StoredSession> stored = store.load(accountHash);
        List<SessionSummary> out = new ArrayList<>();
        for (StoredSession s : stored) {
            Session session = SessionMapper.toSession(s);
            Function<Trip, ItemValuer> fn = SessionMapper.valuerFor(s);
            int tripCount = s.trips.size();
            long net = session.totalNetProfit(fn);
            long xpTotal = session.totalXp();
            long avgNet = tripCount == 0 ? 0 : net / tripCount;
            long avgXp = tripCount == 0 ? 0 : xpTotal / tripCount;
            out.add(new SessionSummary(s.id, s.name, s.category, tripCount,
                    net, session.gpPerHour(fn), xpTotal, session.wallClockMillis(), s.startMillis,
                    session.xpPerHour(), avgNet, avgXp));
        }
        out.sort(Comparator.comparingLong((SessionSummary s) -> s.startMillis).reversed());
        return out;
    }

    public List<TripSummary> tripsFor(String sessionId) {
        StoredSession s = find(sessionId);
        List<TripSummary> out = new ArrayList<>();
        if (s == null) {
            return out;
        }
        for (StoredTrip st : s.trips) {
            Trip t = SessionMapper.toTrip(st);
            ItemValuer v = new FrozenItemValuer(SessionMapper.unitPrices(st));
            out.add(new TripSummary(t.id(), t.totalKills(), t.netProfit(v), t.durationMillis(),
                    t.totalXp(), t.startMillis(), t.died()));
        }
        return out;
    }

    public TripDetail tripDetail(String sessionId, String tripId) {
        StoredSession s = find(sessionId);
        if (s == null) {
            return null;
        }
        for (StoredTrip st : s.trips) {
            if (st.id.equals(tripId)) {
                Trip t = SessionMapper.toTrip(st);
                ItemValuer v = new FrozenItemValuer(SessionMapper.unitPrices(st));
                return new TripDetail(lines(t.pickedUp(), v), lines(t.missed(), v),
                        lines(t.suppliesUsed(), v), t.netProfit(v), t.missedValue(v),
                        SkillXp.sortedFrom(t.xpGained()), NpcKills.sortedByCountDesc(t.kills()));
            }
        }
        return null;
    }

    public List<CategoryStatsView> categoryStats() {
        Map<String, List<Session>> byCategory = sessionsByCategory();
        List<CategoryStatsView> out = new ArrayList<>();
        for (Map.Entry<String, List<Session>> e : byCategory.entrySet()) {
            CategoryStats cs = CategoryStats.from(e.getKey(), e.getValue(), perTripValuer());
            out.add(new CategoryStatsView(cs.category(), cs.sessionCount(), cs.tripCount(),
                    cs.gpPerHour(), cs.xpPerHour()));
        }
        out.sort(Comparator.comparingLong((CategoryStatsView c) -> c.gpPerHour).reversed());
        return out;
    }

    public CategoryDetail categoryDetail(String category) {
        Map<String, List<Session>> byCategory = sessionsByCategory();
        List<Session> sessions = byCategory.getOrDefault(category, Collections.emptyList());
        Function<Trip, ItemValuer> fn = perTripValuer();
        CategoryStats cs = CategoryStats.from(category, sessions, fn);
        int tripCount = cs.tripCount();
        java.util.Map<ItemKey, Double> avgQtyByKey = cs.avgSuppliesPerTrip();

        Map<ItemKey, Long> totalGp = new HashMap<>();
        for (Session s : sessions) {
            for (Trip t : s.trips()) {
                ItemValuer v = fn.apply(t);
                for (Map.Entry<ItemKey, Integer> e : t.suppliesUsed().entrySet()) {
                    totalGp.merge(e.getKey(), v.value(e.getKey(), e.getValue()), Long::sum);
                }
            }
        }
        List<SupplyAverage> supplies = new ArrayList<>();
        long totalSuppliesGp = 0;
        for (Map.Entry<ItemKey, Long> e : totalGp.entrySet()) {
            totalSuppliesGp += e.getValue();
            double avgQty = avgQtyByKey.getOrDefault(e.getKey(), 0.0);
            long avgGp = tripCount == 0 ? 0 : e.getValue() / tripCount;
            supplies.add(new SupplyAverage(label(e.getKey()), avgQty, avgGp));
        }
        supplies.sort(Comparator.comparingLong((SupplyAverage s) -> s.avgGpPerTrip).reversed());
        long avgTotalSupplies = tripCount == 0 ? 0 : totalSuppliesGp / tripCount;

        Map<String, Long> skillTotalXp = new TreeMap<>(); // TreeMap -> alphabetical
        long totalWallClock = 0;
        for (Session s : sessions) {
            totalWallClock += s.wallClockMillis();
            for (Trip t : s.trips()) {
                for (Map.Entry<String, Long> e : t.xpGained().entrySet()) {
                    skillTotalXp.merge(e.getKey(), e.getValue(), Long::sum);
                }
            }
        }
        List<SkillXpAverage> xpAverages = new ArrayList<>();
        for (Map.Entry<String, Long> e : skillTotalXp.entrySet()) {
            long avgTrip = tripCount == 0 ? 0 : e.getValue() / tripCount;
            long perHour = totalWallClock <= 0 ? 0 : e.getValue() * MILLIS_PER_HOUR / totalWallClock;
            xpAverages.add(new SkillXpAverage(e.getKey(), avgTrip, perHour));
        }

        return new CategoryDetail(cs.gpPerHour(), cs.xpPerHour(), cs.avgNetProfitPerTrip(),
                cs.avgMissedPerTrip(), cs.avgTripDurationMillis(), cs.avgKillsPerTrip(),
                supplies, avgTotalSupplies, xpAverages);
    }

    public void rename(String sessionId, String newName) {
        StoredSession s = find(sessionId);
        if (s != null) {
            s.name = newName;
            store.save(s);
        }
    }

    public void recategorize(String sessionId, String newCategory) {
        StoredSession s = find(sessionId);
        if (s != null) {
            s.category = newCategory;
            store.save(s);
        }
    }

    public List<String> categories() {
        java.util.TreeSet<String> distinct = new java.util.TreeSet<>();
        for (StoredSession s : store.load(accountHash)) {
            if (s.category != null) {
                distinct.add(s.category);
            }
        }
        return new ArrayList<>(distinct);
    }

    private Map<String, List<Session>> sessionsByCategory() {
        Map<String, List<Session>> byCategory = new LinkedHashMap<>();
        for (StoredSession s : store.load(accountHash)) {
            byCategory.computeIfAbsent(s.category, k -> new ArrayList<>()).add(SessionMapper.toSession(s));
        }
        return byCategory;
    }

    private Function<Trip, ItemValuer> perTripValuer() {
        return SessionMapper.valuerFor(store.load(accountHash));
    }

    private StoredSession find(String sessionId) {
        for (StoredSession s : store.load(accountHash)) {
            if (s.id.equals(sessionId)) {
                return s;
            }
        }
        return null;
    }

    private List<ItemLine> lines(Map<ItemKey, Integer> items, ItemValuer valuer) {
        List<ItemLine> out = new ArrayList<>();
        for (Map.Entry<ItemKey, Integer> e : items.entrySet()) {
            out.add(new ItemLine(label(e.getKey()), e.getValue(),
                    valuer.value(e.getKey(), e.getValue())));
        }
        out.sort(Comparator.comparingLong((ItemLine l) -> l.gpValue).reversed());
        return out;
    }

    private String label(ItemKey key) {
        return key.isPotion() ? key.potionFamily() : names.apply(key.itemId());
    }

    // ----- view-model carriers -----

    public static final class SessionSummary {
        public final String sessionId;
        public final String name;
        public final String category;
        public final int tripCount;
        public final long netProfit;
        public final long gpPerHour;
        public final long xpTotal;
        public final long wallClockMillis;
        public final long startMillis;
        public final long xpPerHour;
        public final long avgNetProfitPerTrip;
        public final long avgXpPerTrip;

        public SessionSummary(String sessionId, String name, String category, int tripCount,
                              long netProfit, long gpPerHour, long xpTotal, long wallClockMillis,
                              long startMillis, long xpPerHour, long avgNetProfitPerTrip,
                              long avgXpPerTrip) {
            this.sessionId = sessionId;
            this.name = name;
            this.category = category;
            this.tripCount = tripCount;
            this.netProfit = netProfit;
            this.gpPerHour = gpPerHour;
            this.xpTotal = xpTotal;
            this.wallClockMillis = wallClockMillis;
            this.startMillis = startMillis;
            this.xpPerHour = xpPerHour;
            this.avgNetProfitPerTrip = avgNetProfitPerTrip;
            this.avgXpPerTrip = avgXpPerTrip;
        }
    }

    public static final class TripSummary {
        public final String tripId;
        public final int kills;
        public final long netProfit;
        public final long durationMillis;
        public final long xpTotal;
        public final long startMillis;
        public final boolean died;

        public TripSummary(String tripId, int kills, long netProfit, long durationMillis,
                           long xpTotal, long startMillis, boolean died) {
            this.tripId = tripId;
            this.kills = kills;
            this.netProfit = netProfit;
            this.durationMillis = durationMillis;
            this.xpTotal = xpTotal;
            this.startMillis = startMillis;
            this.died = died;
        }
    }

    public static final class ItemLine {
        public final String label;
        public final int quantity;
        public final long gpValue;

        public ItemLine(String label, int quantity, long gpValue) {
            this.label = label;
            this.quantity = quantity;
            this.gpValue = gpValue;
        }
    }

    public static final class TripDetail {
        public final List<ItemLine> pickedUp;
        public final List<ItemLine> leftOnGround;
        public final List<ItemLine> suppliesUsed;
        public final long netProfit;
        public final long missedValue;
        public final List<SkillXp> xpGained;
        public final List<NpcKills> killsByNpc;

        public TripDetail(List<ItemLine> pickedUp, List<ItemLine> leftOnGround,
                          List<ItemLine> suppliesUsed, long netProfit, long missedValue,
                          List<SkillXp> xpGained, List<NpcKills> killsByNpc) {
            this.pickedUp = pickedUp;
            this.leftOnGround = leftOnGround;
            this.suppliesUsed = suppliesUsed;
            this.netProfit = netProfit;
            this.missedValue = missedValue;
            this.xpGained = xpGained;
            this.killsByNpc = killsByNpc;
        }
    }

    public static final class CategoryStatsView {
        public final String category;
        public final int sessionCount;
        public final int tripCount;
        public final long gpPerHour;
        public final long xpPerHour;

        public CategoryStatsView(String category, int sessionCount, int tripCount,
                                 long gpPerHour, long xpPerHour) {
            this.category = category;
            this.sessionCount = sessionCount;
            this.tripCount = tripCount;
            this.gpPerHour = gpPerHour;
            this.xpPerHour = xpPerHour;
        }
    }

    public static final class SupplyAverage {
        public final String label;
        public final double avgQtyPerTrip;
        public final long avgGpPerTrip;

        public SupplyAverage(String label, double avgQtyPerTrip, long avgGpPerTrip) {
            this.label = label;
            this.avgQtyPerTrip = avgQtyPerTrip;
            this.avgGpPerTrip = avgGpPerTrip;
        }
    }

    public static final class SkillXpAverage {
        public final String skill;
        public final long avgXpPerTrip;
        public final long xpPerHour;

        public SkillXpAverage(String skill, long avgXpPerTrip, long xpPerHour) {
            this.skill = skill;
            this.avgXpPerTrip = avgXpPerTrip;
            this.xpPerHour = xpPerHour;
        }
    }

    public static final class CategoryDetail {
        public final long gpPerHour;
        public final long xpPerHour;
        public final long avgNetProfitPerTrip;
        public final long avgMissedPerTrip;
        public final long avgTripDurationMillis;
        public final double avgKillsPerTrip;
        public final List<SupplyAverage> supplies;
        public final long avgTotalSuppliesGpPerTrip;
        public final List<SkillXpAverage> xpAverages;

        public CategoryDetail(long gpPerHour, long xpPerHour, long avgNetProfitPerTrip,
                              long avgMissedPerTrip, long avgTripDurationMillis, double avgKillsPerTrip,
                              List<SupplyAverage> supplies, long avgTotalSuppliesGpPerTrip,
                              List<SkillXpAverage> xpAverages) {
            this.gpPerHour = gpPerHour;
            this.xpPerHour = xpPerHour;
            this.avgNetProfitPerTrip = avgNetProfitPerTrip;
            this.avgMissedPerTrip = avgMissedPerTrip;
            this.avgTripDurationMillis = avgTripDurationMillis;
            this.avgKillsPerTrip = avgKillsPerTrip;
            this.supplies = supplies;
            this.avgTotalSuppliesGpPerTrip = avgTotalSuppliesGpPerTrip;
            this.xpAverages = xpAverages;
        }
    }
}
