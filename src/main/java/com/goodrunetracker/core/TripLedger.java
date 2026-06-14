package com.goodrunetracker.core;

import com.goodrunetracker.core.item.ItemKey;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Event-ordered tracking ledger for a single in-progress trip.
 *
 * <p>Contract: {@link #updateCarried(Map)} must be called once per settled game
 * tick with the combined, dose-normalized inventory+equipment quantities. A
 * positive per-tick delta is reconciled against the still-on-the-ground pool
 * (kill loot picked up, else an untracked generic gain); a negative delta is
 * recorded as supplies used.
 */
public final class TripLedger {

    private final Map<String, Integer> kills = new HashMap<>();
    private final Map<ItemKey, Integer> dropped = new HashMap<>();
    private final Map<ItemKey, Integer> groundPool = new HashMap<>();
    private final Map<ItemKey, Integer> pickedUp = new HashMap<>();
    private final Map<ItemKey, Integer> suppliesUsed = new HashMap<>();
    private final Map<String, Long> xp = new HashMap<>();

    private Map<ItemKey, Integer> carried = null;

    public void recordKill(String npcName, Map<ItemKey, Integer> drops) {
        kills.merge(npcName, 1, Integer::sum);
        for (Map.Entry<ItemKey, Integer> e : drops.entrySet()) {
            dropped.merge(e.getKey(), e.getValue(), Integer::sum);
            groundPool.merge(e.getKey(), e.getValue(), Integer::sum);
        }
    }

    public void updateCarried(Map<ItemKey, Integer> settledCarried) {
        if (carried == null) {
            carried = new HashMap<>(settledCarried);
            return;
        }
        Set<ItemKey> keys = new HashSet<>();
        keys.addAll(carried.keySet());
        keys.addAll(settledCarried.keySet());
        for (ItemKey key : keys) {
            int delta = settledCarried.getOrDefault(key, 0) - carried.getOrDefault(key, 0);
            if (delta > 0) {
                reconcilePickup(key, delta);
            } else if (delta < 0) {
                suppliesUsed.merge(key, -delta, Integer::sum);
            }
        }
        carried = new HashMap<>(settledCarried);
    }

    private void reconcilePickup(ItemKey key, int gained) {
        int onGround = groundPool.getOrDefault(key, 0);
        int fromGround = Math.min(gained, onGround);
        if (fromGround <= 0) {
            return; // untracked generic gain
        }
        pickedUp.merge(key, fromGround, Integer::sum);
        int remaining = onGround - fromGround;
        if (remaining == 0) {
            groundPool.remove(key);
        } else {
            groundPool.put(key, remaining);
        }
    }

    public void recordXp(String skill, long delta) {
        if (delta > 0) {
            xp.merge(skill, delta, Long::sum);
        }
    }

    public Trip build(String id, long startMillis, long endMillis, boolean died) {
        Map<ItemKey, Integer> missed = new HashMap<>();
        for (Map.Entry<ItemKey, Integer> e : dropped.entrySet()) {
            int remaining = e.getValue() - pickedUp.getOrDefault(e.getKey(), 0);
            if (remaining > 0) {
                missed.put(e.getKey(), remaining);
            }
        }
        return new Trip(id, startMillis, endMillis, died,
                kills, dropped, pickedUp, missed, suppliesUsed, xp);
    }
}
