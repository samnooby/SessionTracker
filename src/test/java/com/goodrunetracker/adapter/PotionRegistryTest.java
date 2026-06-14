package com.goodrunetracker.adapter;

import static org.junit.Assert.*;
import java.util.Optional;
import org.junit.Test;

public class PotionRegistryTest {

    @Test
    public void learnsRepresentativeForAPotionFamily() {
        PotionRegistry registry = new PotionRegistry();
        registry.observe(139, "Prayer potion(3)");
        Optional<PotionRegistry.Rep> rep = registry.representativeFor("Prayer potion");
        assertTrue(rep.isPresent());
        assertEquals(139, rep.get().itemId());
        assertEquals(3, rep.get().dose());
    }

    @Test
    public void prefersTheHighestDoseFormSeen() {
        PotionRegistry registry = new PotionRegistry();
        registry.observe(139, "Prayer potion(3)");
        registry.observe(2434, "Prayer potion(4)");
        registry.observe(141, "Prayer potion(2)");
        PotionRegistry.Rep rep = registry.representativeFor("Prayer potion").get();
        assertEquals(2434, rep.itemId());
        assertEquals(4, rep.dose());
    }

    @Test
    public void ignoresNonPotionItems() {
        PotionRegistry registry = new PotionRegistry();
        registry.observe(4151, "Abyssal whip");
        assertFalse(registry.representativeFor("Abyssal whip").isPresent());
    }
}
