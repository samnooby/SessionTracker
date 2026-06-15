package com.goodrunetracker.adapter;

/** Immutable live roll-up of the active session (persisted trips + the in-progress trip). */
public final class SessionSnapshot {
    public final int tripCount;
    public final long netProfit;
    public final long totalXp;
    public final long gpPerHour;

    public SessionSnapshot(int tripCount, long netProfit, long totalXp, long gpPerHour) {
        this.tripCount = tripCount;
        this.netProfit = netProfit;
        this.totalXp = totalXp;
        this.gpPerHour = gpPerHour;
    }
}
