package com.goodrunetracker.adapter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Per-NPC kill count on a trip: a plain, RuneLite-free carrier ({@code npc} is the NPC name). */
public final class NpcKills {

    public final String npc;
    public final int count;

    public NpcKills(String npc, int count) {
        this.npc = npc;
        this.count = count;
    }

    /** The given per-NPC kill map as a list ordered by count descending, ties broken by name ascending. */
    public static List<NpcKills> sortedByCountDesc(Map<String, Integer> kills) {
        List<NpcKills> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : kills.entrySet()) {
            out.add(new NpcKills(e.getKey(), e.getValue()));
        }
        out.sort(Comparator.comparingInt((NpcKills k) -> k.count).reversed()
                .thenComparing(k -> k.npc));
        return out;
    }
}
