package com.goodrunetracker.core;

import com.goodrunetracker.core.item.ItemKey;
import com.goodrunetracker.core.item.ItemValuer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Immutable record of a single completed trip. Quantities only; gp is derived via an ItemValuer. */
public final class Trip {

    private final String id;
    private final long startMillis;
    private final long endMillis;
    private final boolean died;
    private final Map<String, Integer> kills;
    private final Map<ItemKey, Integer> dropped;
    private final Map<ItemKey, Integer> pickedUp;
    private final Map<ItemKey, Integer> missed;
    private final Map<ItemKey, Integer> suppliesUsed;
    private final Map<ItemKey, Integer> gathered;
    // Items acquired this trip (looted or gathered) that were later consumed. They cost
    // nothing, so they are not supplies; they net against this trip's income instead.
    private final Map<ItemKey, Integer> consumedLoot;
    private final Map<String, Long> xpGained;

    public Trip(String id, long startMillis, long endMillis, boolean died,
                Map<String, Integer> kills, Map<ItemKey, Integer> dropped,
                Map<ItemKey, Integer> pickedUp, Map<ItemKey, Integer> missed,
                Map<ItemKey, Integer> suppliesUsed, Map<String, Long> xpGained) {
        this(id, startMillis, endMillis, died, kills, dropped, pickedUp, missed,
                suppliesUsed, new HashMap<>(), xpGained);
    }

    public Trip(String id, long startMillis, long endMillis, boolean died,
                Map<String, Integer> kills, Map<ItemKey, Integer> dropped,
                Map<ItemKey, Integer> pickedUp, Map<ItemKey, Integer> missed,
                Map<ItemKey, Integer> suppliesUsed, Map<ItemKey, Integer> gathered,
                Map<String, Long> xpGained) {
        this(id, startMillis, endMillis, died, kills, dropped, pickedUp, missed,
                suppliesUsed, gathered, new HashMap<>(), xpGained);
    }

    public Trip(String id, long startMillis, long endMillis, boolean died,
                Map<String, Integer> kills, Map<ItemKey, Integer> dropped,
                Map<ItemKey, Integer> pickedUp, Map<ItemKey, Integer> missed,
                Map<ItemKey, Integer> suppliesUsed, Map<ItemKey, Integer> gathered,
                Map<ItemKey, Integer> consumedLoot, Map<String, Long> xpGained) {
        this.id = id;
        this.startMillis = startMillis;
        this.endMillis = endMillis;
        this.died = died;
        this.kills = new HashMap<>(kills);
        this.dropped = new HashMap<>(dropped);
        this.pickedUp = new HashMap<>(pickedUp);
        this.missed = new HashMap<>(missed);
        this.suppliesUsed = new HashMap<>(suppliesUsed);
        this.gathered = new HashMap<>(gathered);
        this.consumedLoot = new HashMap<>(consumedLoot);
        this.xpGained = new HashMap<>(xpGained);
    }

    public String id() {
        return id;
    }

    public long startMillis() {
        return startMillis;
    }

    public long endMillis() {
        return endMillis;
    }

    public long durationMillis() {
        return endMillis - startMillis;
    }

    public boolean died() {
        return died;
    }

    public Map<String, Integer> kills() {
        return Collections.unmodifiableMap(kills);
    }

    public Map<ItemKey, Integer> dropped() {
        return Collections.unmodifiableMap(dropped);
    }

    public Map<ItemKey, Integer> pickedUp() {
        return Collections.unmodifiableMap(pickedUp);
    }

    public Map<ItemKey, Integer> missed() {
        return Collections.unmodifiableMap(missed);
    }

    public Map<ItemKey, Integer> suppliesUsed() {
        return Collections.unmodifiableMap(suppliesUsed);
    }

    public Map<ItemKey, Integer> gathered() {
        return Collections.unmodifiableMap(gathered);
    }

    public Map<ItemKey, Integer> consumedLoot() {
        return Collections.unmodifiableMap(consumedLoot);
    }

    public Map<String, Long> xpGained() {
        return Collections.unmodifiableMap(xpGained);
    }

    public int totalKills() {
        int sum = 0;
        for (int c : kills.values()) {
            sum += c;
        }
        return sum;
    }

    public long totalXp() {
        long sum = 0;
        for (long v : xpGained.values()) {
            sum += v;
        }
        return sum;
    }

    public long pickedUpValue(ItemValuer valuer) {
        return value(pickedUp, valuer);
    }

    public long gatheredValue(ItemValuer valuer) {
        return value(gathered, valuer);
    }

    public long consumedLootValue(ItemValuer valuer) {
        return value(consumedLoot, valuer);
    }

    public long suppliesValue(ItemValuer valuer) {
        return value(suppliesUsed, valuer);
    }

    public long missedValue(ItemValuer valuer) {
        return value(missed, valuer);
    }

    public long netProfit(ItemValuer valuer) {
        return pickedUpValue(valuer) + gatheredValue(valuer)
                - consumedLootValue(valuer) - suppliesValue(valuer);
    }

    private static long value(Map<ItemKey, Integer> items, ItemValuer valuer) {
        long sum = 0;
        for (Map.Entry<ItemKey, Integer> e : items.entrySet()) {
            sum += valuer.value(e.getKey(), e.getValue());
        }
        return sum;
    }
}
