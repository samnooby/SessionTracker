package com.goodrunetracker.adapter;

import com.goodrunetracker.core.Session;
import com.goodrunetracker.core.Trip;
import com.goodrunetracker.core.item.ItemKey;
import com.goodrunetracker.core.item.ItemValuer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Swing-free, RuneLite-free read/edit API over stored sessions. Every query loads the
 * active account's sessions from {@link SessionStore}, values them with each trip's own
 * captured prices, and returns immutable view-model carriers the panel renders.
 */
public final class SessionHistory {

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
            java.util.function.Function<Trip, ItemValuer> fn = SessionMapper.valuerFor(s);
            out.add(new SessionSummary(s.id, s.name, s.category, s.trips.size(),
                    session.totalNetProfit(fn), session.gpPerHour(fn), session.totalXp(),
                    session.wallClockMillis(), s.startMillis));
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
                        lines(t.suppliesUsed(), v), t.netProfit(v), t.missedValue(v));
            }
        }
        return null;
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

        public SessionSummary(String sessionId, String name, String category, int tripCount,
                              long netProfit, long gpPerHour, long xpTotal, long wallClockMillis,
                              long startMillis) {
            this.sessionId = sessionId;
            this.name = name;
            this.category = category;
            this.tripCount = tripCount;
            this.netProfit = netProfit;
            this.gpPerHour = gpPerHour;
            this.xpTotal = xpTotal;
            this.wallClockMillis = wallClockMillis;
            this.startMillis = startMillis;
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

        public TripDetail(List<ItemLine> pickedUp, List<ItemLine> leftOnGround,
                          List<ItemLine> suppliesUsed, long netProfit, long missedValue) {
            this.pickedUp = pickedUp;
            this.leftOnGround = leftOnGround;
            this.suppliesUsed = suppliesUsed;
            this.netProfit = netProfit;
            this.missedValue = missedValue;
        }
    }
}
