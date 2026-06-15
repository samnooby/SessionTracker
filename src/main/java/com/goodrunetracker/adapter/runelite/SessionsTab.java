package com.goodrunetracker.adapter.runelite;

import com.goodrunetracker.adapter.SessionHistory;
import com.goodrunetracker.adapter.TrackingService;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.callback.ClientThread;

final class SessionsTab extends JPanel {
    SessionsTab(ClientThread clientThread) {
        add(new JLabel("Sessions"));
    }
    void setContext(TrackingService service, SessionHistory history) { }
    void reload() { }
}
