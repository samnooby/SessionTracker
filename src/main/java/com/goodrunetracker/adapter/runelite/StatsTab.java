package com.goodrunetracker.adapter.runelite;

import com.goodrunetracker.adapter.SessionHistory;
import javax.swing.JLabel;
import javax.swing.JPanel;

final class StatsTab extends JPanel {
    StatsTab() {
        add(new JLabel("Stats"));
    }
    void setHistory(SessionHistory history) { }
    void reload() { }
}
