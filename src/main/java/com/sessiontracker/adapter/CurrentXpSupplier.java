package com.sessiontracker.adapter;

import java.util.Map;

/**
 * Returns each skill's current total XP, keyed by {@code Skill.getName()} (matching the
 * names {@code StatChanged} delivers). Used to prime per-skill baselines when a session
 * starts so the first XP gain of every skill that session is counted, not silently lost.
 */
public interface CurrentXpSupplier {
    Map<String, Long> currentXp();
}
