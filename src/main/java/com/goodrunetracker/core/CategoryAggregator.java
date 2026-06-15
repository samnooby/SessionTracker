package com.goodrunetracker.core;

import com.goodrunetracker.core.item.ItemValuer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Groups sessions by category and computes {@link CategoryStats} for each. */
public final class CategoryAggregator {

    private CategoryAggregator() {
    }

    public static Map<String, CategoryStats> aggregate(List<Session> sessions, ItemValuer valuer) {
        return aggregate(sessions, t -> valuer);
    }

    public static Map<String, CategoryStats> aggregate(
            List<Session> sessions, Function<Trip, ItemValuer> valuerFn) {
        Map<String, List<Session>> byCategory = new LinkedHashMap<>();
        for (Session s : sessions) {
            byCategory.computeIfAbsent(s.category(), k -> new ArrayList<>()).add(s);
        }
        Map<String, CategoryStats> result = new HashMap<>();
        for (Map.Entry<String, List<Session>> e : byCategory.entrySet()) {
            result.put(e.getKey(), CategoryStats.from(e.getKey(), e.getValue(), valuerFn));
        }
        return result;
    }
}
