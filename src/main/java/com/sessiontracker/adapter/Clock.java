package com.sessiontracker.adapter;

/** Supplies the current time and fresh unique ids; faked in tests. */
public interface Clock {
    long nowMillis();

    String newId();
}
