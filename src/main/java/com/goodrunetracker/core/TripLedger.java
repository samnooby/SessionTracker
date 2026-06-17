package com.goodrunetracker.core;

import com.goodrunetracker.core.item.ItemKey;
import java.util.Collections;
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
    // Brought items that were dropped and charged as supplies, but are still on the ground
    // and recoverable: picking one back up reverses that supply charge.
    private final Map<ItemKey, Integer> droppedBrought = new HashMap<>();
    private final Map<ItemKey, Integer> gathered = new HashMap<>();
    // Acquired-this-trip items (looted or gathered) that were later consumed: free, so not supplies.
    private final Map<ItemKey, Integer> consumedLoot = new HashMap<>();
    private final Map<String, Long> xp = new HashMap<>();

    // The first item gathered this trip, in encounter order (for auto-naming the session).
    private ItemKey firstGathered = null;

    private Map<ItemKey, Integer> carried = null;

    public void recordKill(String npcName, Map<ItemKey, Integer> drops) {
        kills.merge(npcName, 1, Integer::sum);
        for (Map.Entry<ItemKey, Integer> e : drops.entrySet()) {
            int qty = e.getValue();
            if (qty <= 0) {
                continue;
            }
            dropped.merge(e.getKey(), qty, Integer::sum);
            groundPool.merge(e.getKey(), qty, Integer::sum);
        }
    }

    public void updateCarried(Map<ItemKey, Integer> settledCarried) {
        updateCarried(settledCarried, Collections.emptySet());
    }

    public void updateCarried(Map<ItemKey, Integer> settledCarried, Set<ItemKey> droppedThisTick) {
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
                int lost = -delta;
                if (droppedThisTick.contains(key)) {
                    reverseDrop(key, lost);
                } else {
                    consume(key, lost);
                }
            }
        }
        carried = new HashMap<>(settledCarried);
    }

    private void reconcilePickup(ItemKey key, int gained) {
        int remaining = gained;
        // First, reverse any brought item we dropped earlier this trip: picking it back up
        // undoes the supply we charged when it was dropped.
        int broughtBack = Math.min(remaining, droppedBrought.getOrDefault(key, 0));
        if (broughtBack > 0) {
            decrement(suppliesUsed, key, broughtBack);
            decrement(droppedBrought, key, broughtBack);
            remaining -= broughtBack;
        }
        // Then, reconcile against kill loot still on the ground.
        int onGround = groundPool.getOrDefault(key, 0);
        int fromGround = Math.min(remaining, onGround);
        if (fromGround > 0) {
            pickedUp.merge(key, fromGround, Integer::sum);
            decrement(groundPool, key, fromGround);
            remaining -= fromGround;
        }
        // Any further gain is a non-loot inventory gain (e.g. a gathered resource).
        if (remaining > 0) {
            gathered.merge(key, remaining, Integer::sum);
            if (firstGathered == null) {
                firstGathered = key;
            }
        }
    }

    private void consume(ItemKey key, int lost) {
        // An item consumed (eaten, buried, burnt, cast...) is a supply only to the extent it
        // wasn't acquired this trip. Cancel it against this trip's still-unconsumed loot and
        // gathered resources first; only the remainder was brought, so only that is a supply.
        int acquired = pickedUp.getOrDefault(key, 0) + gathered.getOrDefault(key, 0);
        int available = Math.max(0, acquired - consumedLoot.getOrDefault(key, 0));
        int free = Math.min(lost, available);
        if (free > 0) {
            consumedLoot.merge(key, free, Integer::sum);
        }
        int brought = lost - free;
        if (brought > 0) {
            suppliesUsed.merge(key, brought, Integer::sum);
        }
    }

    private void reverseDrop(ItemKey key, int lost) {
        int pickedUpQty = pickedUp.getOrDefault(key, 0);
        int reversed = Math.min(lost, pickedUpQty);
        if (reversed > 0) {
            decrement(pickedUp, key, reversed);
            // Back on the ground so a later re-pickup reconciles as loot again.
            groundPool.merge(key, reversed, Integer::sum);
        }
        int brought = lost - reversed;
        if (brought > 0) {
            suppliesUsed.merge(key, brought, Integer::sum);
            // Remember it's recoverable: picking it back up reverses this supply charge.
            droppedBrought.merge(key, brought, Integer::sum);
        }
    }

    private static void decrement(Map<ItemKey, Integer> map, ItemKey key, int amount) {
        int remaining = map.getOrDefault(key, 0) - amount;
        if (remaining <= 0) {
            map.remove(key);
        } else {
            map.put(key, remaining);
        }
    }

    /** The first item gathered this trip, or {@code null} if nothing has been gathered yet. */
    public ItemKey firstGathered() {
        return firstGathered;
    }

    public void recordXp(String skill, long delta) {
        if (delta > 0) {
            xp.merge(skill, delta, Long::sum);
        }
    }

    /**
     * Builds the immutable {@link Trip}. Non-destructive: the ledger may be queried again.
     * Any loot dropped before the first {@link #updateCarried(Map)} baseline cannot be
     * reconciled to a pickup, so it is reported entirely as missed.
     */
    public Trip build(String id, long startMillis, long endMillis, boolean died) {
        Map<ItemKey, Integer> missed = new HashMap<>();
        for (Map.Entry<ItemKey, Integer> e : dropped.entrySet()) {
            int remaining = e.getValue() - pickedUp.getOrDefault(e.getKey(), 0);
            if (remaining > 0) {
                missed.put(e.getKey(), remaining);
            }
        }
        return new Trip(id, startMillis, endMillis, died,
                kills, dropped, pickedUp, missed, suppliesUsed, gathered, consumedLoot, xp);
    }
}
