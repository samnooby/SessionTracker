package com.sessiontracker.adapter;

import java.util.List;

/** Serializable form of a session. */
public final class StoredSession {
    public String id;
    public String accountHash;
    public String category;
    public String name;
    public long startMillis;
    public long endMillis;
    public List<StoredTrip> trips;
}
