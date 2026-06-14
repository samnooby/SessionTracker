package com.goodrunetracker.core;

import com.goodrunetracker.core.item.ItemValuer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Groups sessions by category and computes {@link CategoryStats} for each. */
public final class CategoryAggregator {

    private CategoryAggregator() {
    }

    public static Map<String, CategoryStats> aggregate(List<Session> sessions, ItemValuer valuer) {
        Map<String, List<Session>> byCategory = new LinkedHashMap<>();
        for (Session s : sessions) {
            byCategory.computeIfAbsent(s.category(), k -> new ArrayList<>()).add(s);
        }
        Map<String, CategoryStats> result = new HashMap<>();
        for (Map.Entry<String, List<Session>> e : byCategory.entrySet()) {
            result.put(e.getKey(), CategoryStats.from(e.getKey(), e.getValue(), valuer));
        }
        return result;
    }
}
