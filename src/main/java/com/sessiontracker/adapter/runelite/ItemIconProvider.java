package com.sessiontracker.adapter.runelite;

import javax.swing.JLabel;

/**
 * Draws an item icon, with its quantity in the corner the way the game's inventory does, into a
 * Swing label. When {@link #enabled()} is false the tabs lay their item rows out as text instead,
 * so callers must ask before building the view rather than relying on {@link #apply} to no-op.
 */
interface ItemIconProvider {

    /** Whether item icons should be drawn at all; false means fall back to text rows. */
    boolean enabled();

    /**
     * Loads {@code itemId}'s sprite and draws {@code quantityText} over its top-left corner.
     * Loading is asynchronous: the label gets its icon once the image arrives.
     */
    void apply(JLabel label, int itemId, String quantityText);
}
