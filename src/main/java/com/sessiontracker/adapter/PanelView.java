package com.sessiontracker.adapter;

/** The narrow view the tracking service drives; implemented by the Swing panel in Phase 2b. */
public interface PanelView {
    void refresh();

    void showDeathPrompt();
}
