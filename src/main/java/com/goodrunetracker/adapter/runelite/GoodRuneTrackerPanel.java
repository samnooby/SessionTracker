package com.goodrunetracker.adapter.runelite;

import com.goodrunetracker.adapter.GpFormat;
import com.goodrunetracker.adapter.PanelView;
import com.goodrunetracker.adapter.TrackingService;
import com.goodrunetracker.adapter.TripSnapshot;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.Optional;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.PluginPanel;

/** Minimal control panel: start/stop tracking, end/discard trip, live readout, death prompt. */
public final class GoodRuneTrackerPanel extends PluginPanel implements PanelView {

    private TrackingService service;
    private boolean loggedIn;

    private final JButton startStop = new JButton("Start tracking");
    private final JButton endTrip = new JButton("End trip");
    private final JButton discardTrip = new JButton("Discard trip");
    private final JPanel deathPrompt = new JPanel();
    private final JButton keepDeath = new JButton("Keep");
    private final JButton discardDeath = new JButton("Discard");

    private final JLabel status = new JLabel("Log in to start tracking");
    private final JLabel kills = new JLabel("-");
    private final JLabel picked = new JLabel("-");
    private final JLabel ground = new JLabel("-");
    private final JLabel supplies = new JLabel("-");
    private final JLabel xp = new JLabel("-");
    private final JLabel gpPerHour = new JLabel("-");

    public GoodRuneTrackerPanel() {
        setLayout(new BorderLayout());
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        body.add(status);
        body.add(startStop);
        body.add(endTrip);
        body.add(discardTrip);

        JPanel stats = new JPanel(new GridLayout(0, 2));
        stats.add(new JLabel("Kills"));
        stats.add(kills);
        stats.add(new JLabel("Loot picked"));
        stats.add(picked);
        stats.add(new JLabel("On ground"));
        stats.add(ground);
        stats.add(new JLabel("Supplies"));
        stats.add(supplies);
        stats.add(new JLabel("XP"));
        stats.add(xp);
        stats.add(new JLabel("GP/hr"));
        stats.add(gpPerHour);
        body.add(stats);

        deathPrompt.setLayout(new GridLayout(0, 1));
        deathPrompt.add(new JLabel("You died this trip — keep or discard it?"));
        JPanel deathButtons = new JPanel();
        deathButtons.add(keepDeath);
        deathButtons.add(discardDeath);
        deathPrompt.add(deathButtons);
        deathPrompt.setVisible(false);
        body.add(deathPrompt);

        add(body, BorderLayout.NORTH);

        startStop.addActionListener(e -> onStartStop());
        wireButtons();
        renderControls();
    }

    private void wireButtons() {
        endTrip.addActionListener(e -> {
            if (service != null && service.isTracking()) {
                service.onBankOpened();
            }
        });
        discardTrip.addActionListener(e -> {
            if (service != null && service.isTracking()) {
                service.discardTrip();
            }
        });
        keepDeath.addActionListener(e -> {
            if (service != null) {
                service.resolveDeath(true);
            }
            deathPrompt.setVisible(false);
            renderControls();
        });
        discardDeath.addActionListener(e -> {
            if (service != null) {
                service.resolveDeath(false);
            }
            deathPrompt.setVisible(false);
            renderControls();
        });
    }

    private void onStartStop() {
        if (service == null) {
            return;
        }
        if (service.isTracking()) {
            service.endSession();
        } else {
            service.startSession();
        }
        renderControls();
    }

    /** Called by the plugin on login/logout. */
    public void setService(TrackingService service, boolean loggedIn) {
        this.service = service;
        this.loggedIn = loggedIn;
        SwingUtilities.invokeLater(this::renderAll);
    }

    @Override
    public void refresh() {
        SwingUtilities.invokeLater(this::renderAll);
    }

    @Override
    public void showDeathPrompt() {
        SwingUtilities.invokeLater(() -> {
            deathPrompt.setVisible(true);
            renderControls();
        });
    }

    private void renderAll() {
        renderControls();
        renderStats();
    }

    private void renderControls() {
        boolean tracking = service != null && service.isTracking();
        startStop.setEnabled(loggedIn && service != null);
        startStop.setText(tracking ? "Stop tracking" : "Start tracking");
        endTrip.setEnabled(tracking);
        discardTrip.setEnabled(tracking);
        if (!loggedIn) {
            status.setText("Log in to start tracking");
        } else {
            status.setText(tracking ? "Tracking…" : "Ready");
        }
    }

    private void renderStats() {
        Optional<TripSnapshot> snap = service == null ? Optional.empty() : service.currentSnapshot();
        if (!snap.isPresent()) {
            kills.setText("-");
            picked.setText("-");
            ground.setText("-");
            supplies.setText("-");
            xp.setText("-");
            gpPerHour.setText("-");
            return;
        }
        TripSnapshot s = snap.get();
        kills.setText(Integer.toString(s.kills));
        picked.setText(GpFormat.format(s.pickedGp));
        ground.setText(GpFormat.format(s.groundGp));
        supplies.setText(GpFormat.format(s.suppliesGp));
        xp.setText(GpFormat.format(s.totalXp));
        gpPerHour.setText(GpFormat.format(s.gpPerHour));
    }
}
