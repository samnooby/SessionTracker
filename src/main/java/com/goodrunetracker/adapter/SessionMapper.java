package com.goodrunetracker.adapter;

import com.goodrunetracker.core.Session;
import com.goodrunetracker.core.Trip;
import com.goodrunetracker.core.item.ItemKey;
import com.goodrunetracker.core.item.ItemValuer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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

    public static Session toSession(StoredSession stored) {
        List<Trip> trips = new ArrayList<>();
        for (StoredTrip t : stored.trips) {
            trips.add(toTrip(t));
        }
        return new Session(stored.id, stored.accountHash, stored.category, stored.name, trips);
    }

    /**
     * A per-trip valuer: each trip is valued with a {@link FrozenItemValuer} over the unit
     * prices captured when that trip ended. Trips not present in this session value to 0.
     */
    public static Function<Trip, ItemValuer> valuerFor(StoredSession stored) {
        Map<String, ItemValuer> byTripId = new HashMap<>();
        for (StoredTrip t : stored.trips) {
            byTripId.put(t.id, new FrozenItemValuer(unitPrices(t)));
        }
        ItemValuer zero = (key, qty) -> 0L;
        return trip -> byTripId.getOrDefault(trip.id(), zero);
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
