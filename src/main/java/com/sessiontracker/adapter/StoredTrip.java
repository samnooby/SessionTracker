package com.sessiontracker.adapter;

import java.util.Map;

/** Serializable form of a trip: quantities (string-keyed) plus captured unit prices. */
public final class StoredTrip {
    public String id;
    public long startMillis;
    public long endMillis;
    public boolean died;
    public Map<String, Integer> kills;
    public Map<String, Integer> dropped;
    public Map<String, Integer> pickedUp;
    public Map<String, Integer> missed;
    public Map<String, Integer> suppliesUsed;
    public Map<String, Integer> gathered;
    public Map<String, Integer> consumedLoot;
    public Map<String, Long> xpGained;
    public Map<String, Long> unitPrices;
}
