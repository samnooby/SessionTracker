package com.sessiontracker.core;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.OptionalLong;

/**
 * Times fights against individual NPCs, and how long the player spent fighting at all.
 *
 * <p>A fight starts with the player's first hitsplat on an NPC (keyed by its scene index, so two
 * of the same monster are timed apart) and ends when that NPC dies. A fight that goes quiet for
 * {@link #ABANDON_AFTER_MILLIS}, or whose NPC despawns alive, is dropped without a kill.
 *
 * <p>Combat uptime is the union of every fight's span, so fighting several NPCs at once counts
 * that time once. A dropped fight still counts up to the player's last hit on it.
 */
public final class FightTracker {

    /** A fight with no hit from the player for this long is treated as walked away from. */
    public static final long ABANDON_AFTER_MILLIS = 30_000L;

    private static final class Fight {
        final long startMillis;
        long lastHitMillis;

        Fight(long startMillis) {
            this.startMillis = startMillis;
            this.lastHitMillis = startMillis;
        }
    }

    private final Map<Integer, Fight> fights = new HashMap<>();
    // Start of the current stretch with at least one fight running, and the latest end seen
    // among fights closed during it (a dropped fight ends at its last hit, which may be earlier
    // than another fight's end).
    private long busySinceMillis;
    private long busyUntilMillis;
    private long closedUptimeMillis;

    /** The player's hitsplat landed on the NPC at {@code npcIndex}. */
    public void hit(int npcIndex, long now) {
        Fight fight = fights.get(npcIndex);
        if (fight == null) {
            if (fights.isEmpty()) {
                busySinceMillis = now;
                busyUntilMillis = now;
            }
            fights.put(npcIndex, new Fight(now));
        } else {
            fight.lastHitMillis = now;
        }
    }

    /** The NPC at {@code npcIndex} died: its time to kill, if the player was fighting it. */
    public OptionalLong death(int npcIndex, long now) {
        Fight fight = fights.get(npcIndex);
        if (fight == null) {
            return OptionalLong.empty();
        }
        close(npcIndex, now);
        return OptionalLong.of(now - fight.startMillis);
    }

    /** The NPC at {@code npcIndex} left the scene alive: drop its fight without a kill. */
    public void despawn(int npcIndex) {
        Fight fight = fights.get(npcIndex);
        if (fight != null) {
            close(npcIndex, fight.lastHitMillis);
        }
    }

    /** Drop every fight the player has not hit for {@link #ABANDON_AFTER_MILLIS}. */
    public void expire(long now) {
        Iterator<Map.Entry<Integer, Fight>> it = fights.entrySet().iterator();
        while (it.hasNext()) {
            Fight fight = it.next().getValue();
            if (now - fight.lastHitMillis >= ABANDON_AFTER_MILLIS) {
                it.remove();
                endedAt(fight.lastHitMillis);
            }
        }
    }

    /**
     * Combat time accumulated since the last call, counting a stretch still running up to
     * {@code now}. Used to hand each trip the combat time that fell inside it.
     */
    public long takeUptime(long now) {
        long uptime = closedUptimeMillis;
        closedUptimeMillis = 0;
        if (!fights.isEmpty()) {
            uptime += Math.max(0, now - busySinceMillis);
            busySinceMillis = now;
            busyUntilMillis = now;
        }
        return uptime;
    }

    /** Forget every fight and any untaken uptime. */
    public void reset() {
        fights.clear();
        closedUptimeMillis = 0;
    }

    private void close(int npcIndex, long endMillis) {
        fights.remove(npcIndex);
        endedAt(endMillis);
    }

    private void endedAt(long endMillis) {
        busyUntilMillis = Math.max(busyUntilMillis, endMillis);
        if (fights.isEmpty()) {
            closedUptimeMillis += Math.max(0, busyUntilMillis - busySinceMillis);
        }
    }
}
