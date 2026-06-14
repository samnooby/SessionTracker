package com.goodrunetracker.core.item;

import java.util.Objects;

/** The parsed dose/charge information from an item name, e.g. {"Prayer potion", 4}. */
public final class DoseForm {

    private final String family;
    private final int dose;

    public DoseForm(String family, int dose) {
        this.family = family;
        this.dose = dose;
    }

    public String family() {
        return family;
    }

    public int dose() {
        return dose;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DoseForm)) {
            return false;
        }
        DoseForm other = (DoseForm) o;
        return dose == other.dose && Objects.equals(family, other.family);
    }

    @Override
    public int hashCode() {
        return Objects.hash(family, dose);
    }
}
