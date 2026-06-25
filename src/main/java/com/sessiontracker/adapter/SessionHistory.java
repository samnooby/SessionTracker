package com.sessiontracker.adapter;

import com.sessiontracker.core.CategoryStats;
import com.sessiontracker.core.Session;
import com.sessiontracker.core.Trip;
import com.sessiontracker.core.item.ItemKey;
import com.sessiontracker.core.item.ItemValuer;
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
    private final PotionRegistry potions;

    public SessionHistory(SessionStore store, String accountHash, IntFunction<String> names) {
        this(store, accountHash, names, new PotionRegistry());
    }

    public SessionHistory(SessionStore store, String accountHash, IntFunction<String> names,
                          PotionRegistry potions) {
        this.store = store;
        this.accountHash = accountHash;
        this.names = names;
        this.potions = potions;
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
            long totalKills = 0;
            for (Trip t : session.trips()) {
                totalKills += t.totalKills();
            }
            double avgKills = tripCount == 0 ? 0 : (double) totalKills / tripCount;
            out.add(new SessionSummary(s.id, s.name, s.category, tripCount,
                    net, session.gpPerHour(fn), xpTotal, session.wallClockMillis(), s.startMillis,
                    session.xpPerHour(), avgNet, avgXp, avgKills));
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
                return new TripDetail(lines(t.pickedUpKept(), v), lines(t.missed(), v),
                        lines(t.suppliesUsed(), v), t.netProfit(v), t.missedValue(v),
                        SkillXp.sortedFrom(t.xpGained()), NpcKills.sortedByCountDesc(t.kills()),
                        lines(t.gatheredKept(), v), t.gatheredValue(v), lines(t.consumedLoot(), v));
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
        ItemAverages supplyAvg = itemAverages(sessions, fn, tripCount, Trip::suppliesUsed);
        // Picked-up and gathered show what was KEPT; items consumed this trip move to used loot.
        ItemAverages pickedAvg = itemAverages(sessions, fn, tripCount, Trip::pickedUpKept);
        ItemAverages missedAvg = itemAverages(sessions, fn, tripCount, Trip::missed);
        ItemAverages droppedAvg = itemAverages(sessions, fn, tripCount, Trip::dropped);
        ItemAverages gatheredAvg = itemAverages(sessions, fn, tripCount, Trip::gatheredKept);
        ItemAverages usedLootAvg = itemAverages(sessions, fn, tripCount, Trip::consumedLoot);

        Map<String, Long> skillTotalXp = new TreeMap<>(); // TreeMap -> alphabetical
        long totalWallClock = 0;
        long totalCombatGp = 0;
        long totalGatherGp = 0;
        long totalSuppliesGp = 0;
        for (Session s : sessions) {
            totalWallClock += s.wallClockMillis();
            for (Trip t : s.trips()) {
                ItemValuer v = fn.apply(t);
                totalCombatGp += t.pickedUpKeptValue(v);
                totalGatherGp += t.gatheredKeptValue(v);
                totalSuppliesGp += t.suppliesValue(v);
                for (Map.Entry<String, Long> e : t.xpGained().entrySet()) {
                    skillTotalXp.merge(e.getKey(), e.getValue(), Long::sum);
                }
            }
        }
        long combatGpPerHour = totalWallClock <= 0 ? 0 : totalCombatGp * MILLIS_PER_HOUR / totalWallClock;
        long gatherGpPerHour = totalWallClock <= 0 ? 0 : totalGatherGp * MILLIS_PER_HOUR / totalWallClock;
        long suppliesGpPerHour = totalWallClock <= 0 ? 0 : totalSuppliesGp * MILLIS_PER_HOUR / totalWallClock;
        List<SkillXpAverage> xpAverages = new ArrayList<>();
        for (Map.Entry<String, Long> e : skillTotalXp.entrySet()) {
            long avgTrip = tripCount == 0 ? 0 : e.getValue() / tripCount;
            long perHour = totalWallClock <= 0 ? 0 : e.getValue() * MILLIS_PER_HOUR / totalWallClock;
            xpAverages.add(new SkillXpAverage(e.getKey(), avgTrip, perHour));
        }

        Map<String, Integer> npcTotalKills = new HashMap<>();
        for (Session s : sessions) {
            for (Trip t : s.trips()) {
                for (Map.Entry<String, Integer> e : t.kills().entrySet()) {
                    npcTotalKills.merge(e.getKey(), e.getValue(), Integer::sum);
                }
            }
        }
        List<NpcKillAverage> killAverages = new ArrayList<>();
        for (Map.Entry<String, Integer> e : npcTotalKills.entrySet()) {
            double avgTrip = tripCount == 0 ? 0 : (double) e.getValue() / tripCount;
            double perHour = totalWallClock <= 0 ? 0
                    : (double) e.getValue() * MILLIS_PER_HOUR / totalWallClock;
            killAverages.add(new NpcKillAverage(e.getKey(), avgTrip, perHour));
        }
        killAverages.sort(Comparator.comparingDouble((NpcKillAverage k) -> k.avgPerTrip).reversed()
                .thenComparing(k -> k.npc));

        return new CategoryDetail(cs.gpPerHour(), cs.xpPerHour(), cs.avgNetProfitPerTrip(),
                cs.avgMissedPerTrip(), cs.avgTripDurationMillis(), cs.avgKillsPerTrip(),
                supplyAvg.items, supplyAvg.avgTotalGpPerTrip, xpAverages, killAverages,
                pickedAvg.items, pickedAvg.avgTotalGpPerTrip,
                missedAvg.items, droppedAvg.items, droppedAvg.avgTotalGpPerTrip,
                gatheredAvg.items, gatheredAvg.avgTotalGpPerTrip, combatGpPerHour, gatherGpPerHour,
                suppliesGpPerHour, usedLootAvg.items, usedLootAvg.avgTotalGpPerTrip);
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

    public void deleteSession(String sessionId) {
        store.delete(accountHash, sessionId);
    }

    public void deleteTrip(String sessionId, String tripId) {
        StoredSession s = find(sessionId);
        if (s == null) {
            return;
        }
        s.trips.removeIf(t -> t.id.equals(tripId));
        if (s.trips.isEmpty()) {
            store.delete(accountHash, sessionId);
        } else {
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
            ItemMeta meta = describe(e.getKey());
            out.add(new ItemLine(meta.label, e.getValue(),
                    valuer.value(e.getKey(), e.getValue()),
                    meta.iconItemId, meta.isPotion, meta.dosesPerPotion));
        }
        out.sort(Comparator.comparingLong((ItemLine l) -> l.gpValue).reversed());
        return out;
    }

    private static final class ItemAverages {
        final List<ItemAverage> items;
        final long avgTotalGpPerTrip;

        ItemAverages(List<ItemAverage> items, long avgTotalGpPerTrip) {
            this.items = items;
            this.avgTotalGpPerTrip = avgTotalGpPerTrip;
        }
    }

    private ItemAverages itemAverages(List<Session> sessions, Function<Trip, ItemValuer> fn,
                                      int tripCount, Function<Trip, Map<ItemKey, Integer>> extractor) {
        Map<ItemKey, Long> totalQty = new HashMap<>();
        Map<ItemKey, Long> totalGp = new HashMap<>();
        for (Session s : sessions) {
            for (Trip t : s.trips()) {
                ItemValuer v = fn.apply(t);
                for (Map.Entry<ItemKey, Integer> e : extractor.apply(t).entrySet()) {
                    totalQty.merge(e.getKey(), e.getValue().longValue(), Long::sum);
                    totalGp.merge(e.getKey(), v.value(e.getKey(), e.getValue()), Long::sum);
                }
            }
        }
        List<ItemAverage> items = new ArrayList<>();
        long sumGp = 0;
        for (Map.Entry<ItemKey, Long> e : totalGp.entrySet()) {
            sumGp += e.getValue();
            ItemMeta meta = describe(e.getKey());
            double avgQty = tripCount == 0 ? 0.0 : (double) totalQty.get(e.getKey()) / tripCount;
            long avgGp = tripCount == 0 ? 0 : e.getValue() / tripCount;
            items.add(new ItemAverage(meta.label, avgQty, avgGp, meta.isPotion, meta.dosesPerPotion));
        }
        items.sort(Comparator.comparingLong((ItemAverage a) -> a.avgGpPerTrip).reversed());
        long avgTotal = tripCount == 0 ? 0 : sumGp / tripCount;
        return new ItemAverages(items, avgTotal);
    }

    private ItemMeta describe(ItemKey key) {
        if (!key.isPotion()) {
            int id = key.itemId();
            return new ItemMeta(names.apply(id), id, false, 1);
        }
        String family = key.potionFamily();
        return potions.representativeFor(family)
                .map(rep -> new ItemMeta(family, rep.itemId(), true, rep.dose()))
                .orElseGet(() -> new ItemMeta(family, null, true, 4));
    }

    private static final class ItemMeta {
        final String label;
        final Integer iconItemId;
        final boolean isPotion;
        final int dosesPerPotion;

        ItemMeta(String label, Integer iconItemId, boolean isPotion, int dosesPerPotion) {
            this.label = label;
            this.iconItemId = iconItemId;
            this.isPotion = isPotion;
            this.dosesPerPotion = dosesPerPotion;
        }
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
        public final double avgKillsPerTrip;

        public SessionSummary(String sessionId, String name, String category, int tripCount,
                              long netProfit, long gpPerHour, long xpTotal, long wallClockMillis,
                              long startMillis, long xpPerHour, long avgNetProfitPerTrip,
                              long avgXpPerTrip, double avgKillsPerTrip) {
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
            this.avgKillsPerTrip = avgKillsPerTrip;
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
        public final Integer iconItemId;
        public final boolean isPotion;
        public final int dosesPerPotion;

        public ItemLine(String label, int quantity, long gpValue, Integer iconItemId,
                        boolean isPotion, int dosesPerPotion) {
            this.label = label;
            this.quantity = quantity;
            this.gpValue = gpValue;
            this.iconItemId = iconItemId;
            this.isPotion = isPotion;
            this.dosesPerPotion = dosesPerPotion;
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
        public final List<ItemLine> gathered;
        public final long gatheredValue;
        public final List<ItemLine> usedLoot;

        public TripDetail(List<ItemLine> pickedUp, List<ItemLine> leftOnGround,
                          List<ItemLine> suppliesUsed, long netProfit, long missedValue,
                          List<SkillXp> xpGained, List<NpcKills> killsByNpc,
                          List<ItemLine> gathered, long gatheredValue, List<ItemLine> usedLoot) {
            this.pickedUp = pickedUp;
            this.leftOnGround = leftOnGround;
            this.suppliesUsed = suppliesUsed;
            this.netProfit = netProfit;
            this.missedValue = missedValue;
            this.xpGained = xpGained;
            this.killsByNpc = killsByNpc;
            this.gathered = gathered;
            this.gatheredValue = gatheredValue;
            this.usedLoot = usedLoot;
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

    public static final class ItemAverage {
        public final String label;
        public final double avgQtyPerTrip;
        public final long avgGpPerTrip;
        public final boolean isPotion;
        public final int dosesPerPotion;

        public ItemAverage(String label, double avgQtyPerTrip, long avgGpPerTrip,
                           boolean isPotion, int dosesPerPotion) {
            this.label = label;
            this.avgQtyPerTrip = avgQtyPerTrip;
            this.avgGpPerTrip = avgGpPerTrip;
            this.isPotion = isPotion;
            this.dosesPerPotion = dosesPerPotion;
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

    public static final class NpcKillAverage {
        public final String npc;
        public final double avgPerTrip;
        public final double perHour;

        public NpcKillAverage(String npc, double avgPerTrip, double perHour) {
            this.npc = npc;
            this.avgPerTrip = avgPerTrip;
            this.perHour = perHour;
        }
    }

    public static final class CategoryDetail {
        public final long gpPerHour;
        public final long xpPerHour;
        public final long avgNetProfitPerTrip;
        public final long avgMissedPerTrip;
        public final long avgTripDurationMillis;
        public final double avgKillsPerTrip;
        public final List<ItemAverage> supplies;
        public final long avgTotalSuppliesGpPerTrip;
        public final List<SkillXpAverage> xpAverages;
        public final List<NpcKillAverage> killAverages;
        public final List<ItemAverage> pickedAverages;
        public final long avgPickedGpPerTrip;
        public final List<ItemAverage> missedAverages;
        public final List<ItemAverage> droppedAverages;
        public final long avgDroppedGpPerTrip;
        public final List<ItemAverage> gatheredAverages;
        public final long avgGatheredGpPerTrip;
        public final long combatGpPerHour;
        public final long gatherGpPerHour;
        public final long suppliesGpPerHour;
        public final List<ItemAverage> usedLootAverages;
        public final long avgUsedLootGpPerTrip;

        public CategoryDetail(long gpPerHour, long xpPerHour, long avgNetProfitPerTrip,
                              long avgMissedPerTrip, long avgTripDurationMillis, double avgKillsPerTrip,
                              List<ItemAverage> supplies, long avgTotalSuppliesGpPerTrip,
                              List<SkillXpAverage> xpAverages, List<NpcKillAverage> killAverages,
                              List<ItemAverage> pickedAverages, long avgPickedGpPerTrip,
                              List<ItemAverage> missedAverages, List<ItemAverage> droppedAverages,
                              long avgDroppedGpPerTrip, List<ItemAverage> gatheredAverages,
                              long avgGatheredGpPerTrip, long combatGpPerHour, long gatherGpPerHour,
                              long suppliesGpPerHour, List<ItemAverage> usedLootAverages,
                              long avgUsedLootGpPerTrip) {
            this.gpPerHour = gpPerHour;
            this.xpPerHour = xpPerHour;
            this.avgNetProfitPerTrip = avgNetProfitPerTrip;
            this.avgMissedPerTrip = avgMissedPerTrip;
            this.avgTripDurationMillis = avgTripDurationMillis;
            this.avgKillsPerTrip = avgKillsPerTrip;
            this.supplies = supplies;
            this.avgTotalSuppliesGpPerTrip = avgTotalSuppliesGpPerTrip;
            this.xpAverages = xpAverages;
            this.killAverages = killAverages;
            this.pickedAverages = pickedAverages;
            this.avgPickedGpPerTrip = avgPickedGpPerTrip;
            this.missedAverages = missedAverages;
            this.droppedAverages = droppedAverages;
            this.avgDroppedGpPerTrip = avgDroppedGpPerTrip;
            this.gatheredAverages = gatheredAverages;
            this.avgGatheredGpPerTrip = avgGatheredGpPerTrip;
            this.combatGpPerHour = combatGpPerHour;
            this.gatherGpPerHour = gatherGpPerHour;
            this.suppliesGpPerHour = suppliesGpPerHour;
            this.usedLootAverages = usedLootAverages;
            this.avgUsedLootGpPerTrip = avgUsedLootGpPerTrip;
        }
    }
}
