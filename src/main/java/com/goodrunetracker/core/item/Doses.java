package com.goodrunetracker.core.item;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses dose/charge information from item names like "Prayer potion(4)".
 * Matches a trailing "(<digits>)" suffix, which in OSRS denotes potion doses
 * (and, accepted for v1, charged jewellery like "Games necklace(8)").
 */
public final class Doses {

    private static final Pattern DOSE = Pattern.compile("^(.*)\\((\\d+)\\)$");

    private Doses() {
    }

    public static Optional<DoseForm> parse(String name) {
        if (name == null) {
            return Optional.empty();
        }
        Matcher matcher = DOSE.matcher(name.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String family = matcher.group(1).trim();
        int dose = Integer.parseInt(matcher.group(2));
        if (family.isEmpty() || dose <= 0) {
            return Optional.empty();
        }
        return Optional.of(new DoseForm(family, dose));
    }
}
