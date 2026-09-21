package com.sessiontracker.adapter;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the plugin believes is inside the storage containers the client will not let it read
 * (herb sack, gem bag, coal bag, fish barrel, log basket and friends).
 *
 * <p>Contents are inferred from the message the game prints as each item is swallowed, so a gain
 * is recorded in the trip it happened in rather than whenever the container is next emptied. The
 * belief is folded into the carried snapshot like any real container, which is what makes a later
 * deposit net to zero.
 *
 * <p>A gather is only believed once the tick has settled: the game prints the same message when a
 * full container leaves the item in your inventory, so anything the inventory actually received is
 * subtracted before the rest is credited. When the belief cannot be trusted any more — a manual
 * Fill or Empty, a bank visit, a fresh login — the caller clears it, and the inventory change that
 * follows is rebaselined rather than booked.
 */
public final class StashLedger {

    /** Gathers seen this tick, not yet reconciled against what the inventory received. */
    private final Map<String, Map<Integer, Integer>> pending = new LinkedHashMap<>();

    /** Per container, what we believe it holds. */
    private final Map<String, Map<Integer, Integer>> held = new LinkedHashMap<>();

    /** Records that {@code bag} appears to have swallowed {@code qty} of {@code itemId}. */
    public void gathered(String bag, int itemId, int qty) {
        if (qty <= 0) {
            return;
        }
        pending.computeIfAbsent(bag, b -> new HashMap<>()).merge(itemId, qty, Integer::sum);
    }

    /**
     * Believes this tick's gathers, minus anything the inventory actually received, which the
     * container must have been too full to take.
     */
    public void settle(Map<Integer, Integer> inventoryBefore, Map<Integer, Integer> inventoryAfter) {
        if (pending.isEmpty()) {
            return;
        }
        Map<Integer, Integer> spareRoom = new HashMap<>();
        for (Map.Entry<String, Map<Integer, Integer>> bag : pending.entrySet()) {
            for (Map.Entry<Integer, Integer> gather : bag.getValue().entrySet()) {
                int itemId = gather.getKey();
                int landedInInventory = spareRoom.containsKey(itemId)
                        ? spareRoom.get(itemId)
                        : increase(inventoryBefore, inventoryAfter, itemId);
                int credited = Math.max(0, gather.getValue() - landedInInventory);
                spareRoom.put(itemId, Math.max(0, landedInInventory - gather.getValue()));
                if (credited > 0) {
                    held.computeIfAbsent(bag.getKey(), b -> new HashMap<>())
                            .merge(itemId, credited, Integer::sum);
                }
            }
        }
        pending.clear();
    }

    private static int increase(Map<Integer, Integer> before, Map<Integer, Integer> after, int itemId) {
        int was = before.getOrDefault(itemId, 0);
        int now = after.getOrDefault(itemId, 0);
        return Math.max(0, now - was);
    }

    /** Forgets one container, for when a manual transfer makes the belief untrustworthy. */
    public void clear(String bag) {
        pending.remove(bag);
        held.remove(bag);
    }

    public void clearAll() {
        pending.clear();
        held.clear();
    }

    /** Everything believed to be stashed, merged across containers, for the carried snapshot. */
    public Map<Integer, Integer> contents() {
        Map<Integer, Integer> out = new HashMap<>();
        for (Map<Integer, Integer> bag : held.values()) {
            for (Map.Entry<Integer, Integer> e : bag.entrySet()) {
                out.merge(e.getKey(), e.getValue(), Integer::sum);
            }
        }
        return out;
    }
}
