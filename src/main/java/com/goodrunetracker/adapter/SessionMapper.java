package com.goodrunetracker.adapter;

import com.goodrunetracker.core.Trip;
import com.goodrunetracker.core.item.ItemKey;
import java.util.HashMap;
import java.util.Map;

/** Converts a core {@link Trip} (plus captured unit prices) to/from its stored form. */
public final class SessionMapper {

    private SessionMapper() {
    }

    public static StoredTrip toStored(Trip trip, Map<ItemKey, Long> unitPrices) {
        StoredTrip stored = new StoredTrip();
        stored.id = trip.id();
        stored.startMillis = trip.startMillis();
        stored.endMillis = trip.endMillis();
        stored.died = trip.died();
        stored.kills = new HashMap<>(trip.kills());
        stored.dropped = encode(trip.dropped());
        stored.pickedUp = encode(trip.pickedUp());
        stored.missed = encode(trip.missed());
        stored.suppliesUsed = encode(trip.suppliesUsed());
        stored.xpGained = new HashMap<>(trip.xpGained());
        stored.unitPrices = encodeLong(unitPrices);
        return stored;
    }

    public static Trip toTrip(StoredTrip stored) {
        return new Trip(stored.id, stored.startMillis, stored.endMillis, stored.died,
                new HashMap<>(stored.kills),
                decode(stored.dropped), decode(stored.pickedUp),
                decode(stored.missed), decode(stored.suppliesUsed),
                new HashMap<>(stored.xpGained));
    }

    public static Map<ItemKey, Long> unitPrices(StoredTrip stored) {
        Map<ItemKey, Long> out = new HashMap<>();
        stored.unitPrices.forEach((token, price) -> out.put(ItemKeyCodec.decode(token), price));
        return out;
    }

    private static Map<String, Integer> encode(Map<ItemKey, Integer> map) {
        Map<String, Integer> out = new HashMap<>();
        map.forEach((key, qty) -> out.put(ItemKeyCodec.encode(key), qty));
        return out;
    }

    private static Map<String, Long> encodeLong(Map<ItemKey, Long> map) {
        Map<String, Long> out = new HashMap<>();
        map.forEach((key, value) -> out.put(ItemKeyCodec.encode(key), value));
        return out;
    }

    private static Map<ItemKey, Integer> decode(Map<String, Integer> map) {
        Map<ItemKey, Integer> out = new HashMap<>();
        map.forEach((token, qty) -> out.put(ItemKeyCodec.decode(token), qty));
        return out;
    }
}
