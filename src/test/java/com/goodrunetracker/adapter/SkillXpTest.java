package com.goodrunetracker.adapter;

import static org.junit.Assert.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class SkillXpTest {

    @Test
    public void sortedFromReturnsAlphabeticalListBySkill() {
        Map<String, Long> xp = new HashMap<>();
        xp.put("Ranged", 300L);
        xp.put("Attack", 200L);
        xp.put("Hitpoints", 100L);

        List<SkillXp> list = SkillXp.sortedFrom(xp);

        assertEquals(3, list.size());
        assertEquals("Attack", list.get(0).skill);
        assertEquals(200L, list.get(0).xp);
        assertEquals("Hitpoints", list.get(1).skill);
        assertEquals("Ranged", list.get(2).skill);
    }

    @Test
    public void sortedFromEmptyMapIsEmptyList() {
        assertTrue(SkillXp.sortedFrom(new HashMap<>()).isEmpty());
    }
}
