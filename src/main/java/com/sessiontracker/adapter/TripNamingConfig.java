package com.sessiontracker.adapter;

/**
 * Whether to auto-name a session's category after the first qualifying action. Read live so
 * config toggles take effect immediately. When both are enabled, whichever happens first
 * (kill or gather) names the session; when neither matches, the session stays uncategorized.
 */
public interface TripNamingConfig {
    boolean nameAfterFirstKill();

    boolean nameAfterFirstGather();
}
