package com.goodrunetracker.adapter;

/** Immutable live readout of the in-progress trip, for the panel. */
public final class TripSnapshot {
    public final int tripNumber;
    public final long durationMillis;
    public final int kills;
    public final long pickedGp;
    public final long groundGp;
    public final long suppliesGp;
    public final long totalXp;
    public final long gpPerHour;

    public TripSnapshot(int tripNumber, long durationMillis, int kills, long pickedGp,
                        long groundGp, long suppliesGp, long totalXp, long gpPerHour) {
        this.tripNumber = tripNumber;
        this.durationMillis = durationMillis;
        this.kills = kills;
        this.pickedGp = pickedGp;
        this.groundGp = groundGp;
        this.suppliesGp = suppliesGp;
        this.totalXp = totalXp;
        this.gpPerHour = gpPerHour;
    }
}
