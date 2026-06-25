package com.sessiontracker.adapter.runelite;

import com.sessiontracker.adapter.PanelView;
import com.sessiontracker.adapter.SessionHistory;
import com.sessiontracker.adapter.TrackingService;
import java.awt.BorderLayout;
import java.util.Map;
import javax.swing.Icon;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.PluginPanel;

/** Tabbed panel: Now / Sessions / Stats. Implements PanelView so the service can push refreshes. */
public final class SessionTrackerPanel extends PluginPanel implements PanelView {

    private final NowTab nowTab;
    private final SessionsTab sessionsTab;
    private final StatsTab statsTab;
    private final JTabbedPane tabs = new JTabbedPane();

    public SessionTrackerPanel(ClientThread clientThread, Map<String, Icon> skillIcons,
                               ItemIconProvider itemIcons) {
        this.nowTab = new NowTab(clientThread, skillIcons);
        this.sessionsTab = new SessionsTab(clientThread, skillIcons, itemIcons);
        this.statsTab = new StatsTab(clientThread);
        setLayout(new BorderLayout());
        tabs.addTab("Now", nowTab);
        tabs.addTab("Sessions", sessionsTab);
        tabs.addTab("Stats", statsTab);
        tabs.addChangeListener(e -> onTabSelected());
        add(tabs, BorderLayout.CENTER);
    }

    private void onTabSelected() {
        java.awt.Component c = tabs.getSelectedComponent();
        if (c == sessionsTab) {
            sessionsTab.reload();
        } else if (c == statsTab) {
            statsTab.reload();
        }
    }

    /** Called by the plugin on login/logout. */
    public void setService(TrackingService service, boolean loggedIn, SessionHistory history) {
        SwingUtilities.invokeLater(() -> {
            nowTab.setService(service, loggedIn);
            sessionsTab.setContext(service, history);
            statsTab.setHistory(history);
        });
    }

    @Override
    public void refresh() {
        SwingUtilities.invokeLater(nowTab::render);
    }

    @Override
    public void showDeathPrompt() {
        SwingUtilities.invokeLater(nowTab::showDeathPrompt);
    }
}
