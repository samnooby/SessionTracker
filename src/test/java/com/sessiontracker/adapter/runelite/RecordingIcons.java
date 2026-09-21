package com.sessiontracker.adapter.runelite;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.JLabel;

/**
 * An {@link ItemIconProvider} that records what it was asked to draw instead of loading sprites,
 * so panel tests can assert on the item and the quantity without a client.
 */
final class RecordingIcons implements ItemIconProvider {

    private final boolean enabled;
    private final Map<Integer, String> drawn = new LinkedHashMap<>();

    RecordingIcons(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public void apply(JLabel label, int itemId, String quantityText) {
        drawn.put(itemId, quantityText);
    }

    /** The quantity drawn on {@code itemId}'s icon, or null if no icon was drawn for it. */
    String quantityOn(int itemId) {
        return drawn.get(itemId);
    }
}
