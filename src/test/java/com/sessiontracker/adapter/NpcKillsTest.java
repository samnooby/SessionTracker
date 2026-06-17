package com.sessiontracker.adapter;

import static org.junit.Assert.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class NpcKillsTest {

    @Test
    public void sortedByCountDescOrdersByCountThenName() {
        Map<String, Integer> kills = new HashMap<>();
        kills.put("Bird", 10);
        kills.put("Goblin", 20);
        kills.put("Cow", 10); // ties with Bird at 10 -> alphabetical: Bird before Cow

        List<NpcKills> list = NpcKills.sortedByCountDesc(kills);

        assertEquals(3, list.size());
        assertEquals("Goblin", list.get(0).npc);
        assertEquals(20, list.get(0).count);
        assertEquals("Bird", list.get(1).npc);
        assertEquals(10, list.get(1).count);
        assertEquals("Cow", list.get(2).npc);
    }

    @Test
    public void sortedByCountDescEmptyMapIsEmptyList() {
        assertTrue(NpcKills.sortedByCountDesc(new HashMap<>()).isEmpty());
    }
}
