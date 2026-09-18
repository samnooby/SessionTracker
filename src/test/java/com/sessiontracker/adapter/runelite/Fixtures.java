package com.sessiontracker.adapter.runelite;

import com.sessiontracker.adapter.ItemKeyCodec;
import com.sessiontracker.adapter.StoredSession;
import com.sessiontracker.adapter.StoredTrip;
import com.sessiontracker.core.item.ItemKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

/** Builders for stored sessions and trips, so panel tests can seed a SessionStore directly. */
final class Fixtures {

    static final int COINS = 995;
    static final int SHARK = 385;
    static final int OAK_LOGS = 1521;

    static final long MINUTE = 60_000L;

    private Fixtures() {
    }

    static String itemName(int id) {
        switch (id) {
            case COINS:
                return "Coins";
            case SHARK:
                return "Shark";
            case OAK_LOGS:
                return "Oak logs";
            default:
                return "Item " + id;
        }
    }

    static String key(int itemId) {
        return ItemKeyCodec.encode(ItemKey.item(itemId));
    }

    static StoredSession session(String id, String category, String name, long startMillis,
                                 StoredTrip... trips) {
        StoredSession s = new StoredSession();
        s.id = id;
        s.accountHash = "42";
        s.category = category;
        s.name = name;
        s.startMillis = startMillis;
        s.trips = new ArrayList<>(Arrays.asList(trips));
        long end = startMillis;
        for (StoredTrip t : trips) {
            end = Math.max(end, t.endMillis);
        }
        s.endMillis = end;
        return s;
    }

    static TripBuilder trip(String id, long startMillis, long endMillis) {
        return new TripBuilder(id, startMillis, endMillis);
    }

    static final class TripBuilder {
        private final StoredTrip t = new StoredTrip();

        TripBuilder(String id, long startMillis, long endMillis) {
            t.id = id;
            t.startMillis = startMillis;
            t.endMillis = endMillis;
            t.kills = new HashMap<>();
            t.dropped = new HashMap<>();
            t.pickedUp = new HashMap<>();
            t.missed = new HashMap<>();
            t.suppliesUsed = new HashMap<>();
            t.gathered = new HashMap<>();
            t.consumedLoot = new HashMap<>();
            t.xpGained = new HashMap<>();
            t.unitPrices = new HashMap<>();
        }

        TripBuilder kills(String npc, int count) {
            t.kills.merge(npc, count, Integer::sum);
            return this;
        }

        /** Loot dropped by kills and picked up, priced per unit. */
        TripBuilder pickedUp(int itemId, int qty, long unitPrice) {
            t.dropped.merge(key(itemId), qty, Integer::sum);
            t.pickedUp.merge(key(itemId), qty, Integer::sum);
            t.unitPrices.put(key(itemId), unitPrice);
            return this;
        }

        /** Loot dropped by kills and left on the ground, priced per unit. */
        TripBuilder missed(int itemId, int qty, long unitPrice) {
            t.dropped.merge(key(itemId), qty, Integer::sum);
            t.missed.merge(key(itemId), qty, Integer::sum);
            t.unitPrices.put(key(itemId), unitPrice);
            return this;
        }

        TripBuilder gathered(int itemId, int qty, long unitPrice) {
            t.gathered.merge(key(itemId), qty, Integer::sum);
            t.unitPrices.put(key(itemId), unitPrice);
            return this;
        }

        TripBuilder supplies(int itemId, int qty, long unitPrice) {
            t.suppliesUsed.merge(key(itemId), qty, Integer::sum);
            t.unitPrices.put(key(itemId), unitPrice);
            return this;
        }

        TripBuilder xp(String skill, long amount) {
            t.xpGained.merge(skill, amount, Long::sum);
            return this;
        }

        TripBuilder died() {
            t.died = true;
            return this;
        }

        StoredTrip build() {
            return t;
        }
    }
}
