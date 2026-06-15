package com.goodrunetracker.adapter.runelite;

import com.goodrunetracker.adapter.Clock;
import java.util.UUID;

/** Real clock: wall-clock time and random UUIDs. */
public final class SystemClock implements Clock {

    @Override
    public long nowMillis() {
        return System.currentTimeMillis();
    }

    @Override
    public String newId() {
        return UUID.randomUUID().toString();
    }
}
