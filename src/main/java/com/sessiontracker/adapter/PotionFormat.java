package com.sessiontracker.adapter;

import java.util.Locale;

/** Formats a single-dose count as a potion count, e.g. 37 doses of 4 -> "9.25 potions (of 4)". */
public final class PotionFormat {

    private PotionFormat() {
    }

    public static String potions(double doses, int dosesPerPotion) {
        int denom = dosesPerPotion <= 0 ? 1 : dosesPerPotion;
        double potions = doses / denom;
        return formatQty(potions) + " potions (of " + denom + ")";
    }

    private static String formatQty(double value) {
        if (value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        String s = String.format(Locale.US, "%.2f", value);
        s = s.replaceAll("0+$", "");
        s = s.replaceAll("\\.$", "");
        return s;
    }
}
