package com.goodrunetracker.core;

import com.goodrunetracker.core.item.ItemValuer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** An ordered collection of trips for one activity, plus its editable labels. */
public final class Session {

    private static final long MILLIS_PER_HOUR = 3_600_000L;

    private final String id;
    private final String accountHash;
    private String category;
    private String name;
    private final List<Trip> trips;

    public Session(String id, String accountHash, String category, String name, List<Trip> trips) {
        this.id = id;
        this.accountHash = accountHash;
        this.category = category;
        this.name = name;
        this.trips = new ArrayList<>(trips);
    }

    public String id() {
        return id;
    }

    public String accountHash() {
        return accountHash;
    }

    public String category() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Trip> trips() {
        return Collections.unmodifiableList(trips);
    }

    public long wallClockMillis() {
        if (trips.isEmpty()) {
            return 0;
        }
        long first = Long.MAX_VALUE;
        long last = Long.MIN_VALUE;
        for (Trip t : trips) {
            first = Math.min(first, t.startMillis());
            last = Math.max(last, t.endMillis());
        }
        return last - first;
    }

    public long totalNetProfit(ItemValuer valuer) {
        long sum = 0;
        for (Trip t : trips) {
            sum += t.netProfit(valuer);
        }
        return sum;
    }

    public long totalXp() {
        long sum = 0;
        for (Trip t : trips) {
            sum += t.totalXp();
        }
        return sum;
    }

    public long gpPerHour(ItemValuer valuer) {
        long ms = wallClockMillis();
        return ms <= 0 ? 0 : totalNetProfit(valuer) * MILLIS_PER_HOUR / ms;
    }

    public long xpPerHour() {
        long ms = wallClockMillis();
        return ms <= 0 ? 0 : totalXp() * MILLIS_PER_HOUR / ms;
    }
}
