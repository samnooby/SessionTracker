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
    /** Time between this session ending and being resumed, excluded from its wall clock. */
    public long pausedMillis;
    public List<StoredTrip> trips;
}
