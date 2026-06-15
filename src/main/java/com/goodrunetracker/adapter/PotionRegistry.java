package com.goodrunetracker.adapter;

import com.goodrunetracker.core.item.Doses;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Learns a representative dose-form item id for each potion family as raw item
 * snapshots are observed. The core collapses potions to a family name and loses
 * the id; this restores an id so the family can be priced per dose.
 */
public final class PotionRegistry {

    /** A representative dose form: which item id, and how many doses it carries. */
    public static final class Rep {
        private final int itemId;
        private final int dose;

        Rep(int itemId, int dose) {
            this.itemId = itemId;
            this.dose = dose;
        }

        public int itemId() {
            return itemId;
        }

        public int dose() {
            return dose;
        }
    }

    private final Map<String, Rep> reps = new HashMap<>();

    public void observe(int itemId, String name) {
        Doses.parse(name).ifPresent(form -> {
            Rep current = reps.get(form.family());
            if (current == null || form.dose() > current.dose) {
                reps.put(form.family(), new Rep(itemId, form.dose()));
            }
        });
    }

    public Optional<Rep> representativeFor(String family) {
        return Optional.ofNullable(reps.get(family));
    }
}
