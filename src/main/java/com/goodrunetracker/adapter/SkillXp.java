package com.goodrunetracker.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Per-skill XP gained on a trip: a plain, RuneLite-free carrier ({@code skill} is a display name). */
public final class SkillXp {

    public final String skill;
    public final long xp;

    public SkillXp(String skill, long xp) {
        this.skill = skill;
        this.xp = xp;
    }

    /** The given per-skill XP map as a list ordered alphabetically by skill name. */
    public static List<SkillXp> sortedFrom(Map<String, Long> xpGained) {
        List<SkillXp> out = new ArrayList<>();
        for (Map.Entry<String, Long> e : new TreeMap<>(xpGained).entrySet()) {
            out.add(new SkillXp(e.getKey(), e.getValue()));
        }
        return out;
    }
}
