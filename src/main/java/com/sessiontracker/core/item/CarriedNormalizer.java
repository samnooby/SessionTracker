package com.sessiontracker.core.item;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntFunction;

/**
 * Converts a raw combined inventory+equipment snapshot (itemId -> quantity)
 * into normalized {@link ItemKey} quantities, where potion ids collapse into a
 * single per-family key valued in total doses.
 */
public final class CarriedNormalizer {

    private CarriedNormalizer() {
    }

    public static Map<ItemKey, Integer> normalize(Map<Integer, Integer> rawCombined,
                                                   IntFunction<String> nameLookup) {
        Map<ItemKey, Integer> out = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : rawCombined.entrySet()) {
            int id = entry.getKey();
            int qty = entry.getValue();
            if (qty <= 0) {
                continue;
            }
            Optional<DoseForm> form = Doses.parse(nameLookup.apply(id));
            if (form.isPresent()) {
                out.merge(ItemKey.potion(form.get().family()), form.get().dose() * qty, Integer::sum);
            } else {
                out.merge(ItemKey.item(id), qty, Integer::sum);
            }
        }
        return out;
    }
}
