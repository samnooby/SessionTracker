package com.sessiontracker.adapter.runelite;

import javax.swing.JLabel;

/** Loads an item icon into a Swing label. Implementations may no-op when icons are disabled. */
@FunctionalInterface
interface ItemIconProvider {
    void apply(JLabel label, int itemId);
}
