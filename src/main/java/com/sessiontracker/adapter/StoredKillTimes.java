package com.sessiontracker.adapter;

import com.sessiontracker.core.KillTimes;

/** Serializable form of {@link KillTimes}. */
public final class StoredKillTimes {
    public int count;
    public long totalMillis;
    public long fastestMillis;

    static StoredKillTimes from(KillTimes times) {
        StoredKillTimes stored = new StoredKillTimes();
        stored.count = times.count();
        stored.totalMillis = times.totalMillis();
        stored.fastestMillis = times.fastestMillis();
        return stored;
    }

    KillTimes toKillTimes() {
        return new KillTimes(count, totalMillis, fastestMillis);
    }
}
