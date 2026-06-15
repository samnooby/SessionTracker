package com.goodrunetracker.adapter.runelite;

import com.goodrunetracker.adapter.GpFormat;
import com.goodrunetracker.adapter.SessionSnapshot;
import com.goodrunetracker.adapter.TrackingService;
import com.goodrunetracker.adapter.TripSnapshot;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.Optional;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.callback.ClientThread;

/** The live view: status + controls, current-trip card, session-so-far roll-up, death prompt. */
final class NowTab extends JPanel {

    private final ClientThread clientThread;
    private TrackingService service;
    private boolean loggedIn;

    private final JLabel status = new JLabel("Log in to start tracking");
    private final JButton startStop = new JButton("Start tracking");
    private final JButton endTrip = new JButton("End trip");
    private final JButton discardTrip = new JButton("Discard trip");

    private final JLabel kills = new JLabel("-");
    private final JLabel picked = new JLabel("-");
    private final JLabel ground = new JLabel("-");
    private final JLabel supplies = new JLabel("-");
    private final JLabel tripGpHr = new JLabel("-");

    private final JLabel sessTrips = new JLabel("-");
    private final JLabel sessNet = new JLabel("-");
    private final JLabel sessXp = new JLabel("-");
    private final JLabel sessGpHr = new JLabel("-");

    private final JPanel deathPrompt = new JPanel();
    private final JButton keepDeath = new JButton("Keep");
    private final JButton discardDeath = new JButton("Discard");

    NowTab(ClientThread clientThread) {
        this.clientThread = clientThread;
        setLayout(new BorderLayout());
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        body.add(status);
        body.add(startStop);
        body.add(endTrip);
        body.add(discardTrip);

        body.add(new JLabel("Current trip"));
        JPanel trip = new JPanel(new GridLayout(0, 2));
        trip.add(new JLabel("Kills")); trip.add(kills);
        trip.add(new JLabel("Loot picked")); trip.add(picked);
        trip.add(new JLabel("On ground")); trip.add(ground);
        trip.add(new JLabel("Supplies")); trip.add(supplies);
        trip.add(new JLabel("GP/hr")); trip.add(tripGpHr);
        body.add(trip);

        body.add(new JLabel("Session so far"));
        JPanel sess = new JPanel(new GridLayout(0, 2));
        sess.add(new JLabel("Trips")); sess.add(sessTrips);
        sess.add(new JLabel("Net profit")); sess.add(sessNet);
        sess.add(new JLabel("Total XP")); sess.add(sessXp);
        sess.add(new JLabel("GP/hr")); sess.add(sessGpHr);
        body.add(sess);

        deathPrompt.setLayout(new GridLayout(0, 1));
        deathPrompt.add(new JLabel("You died this trip — keep or discard it?"));
        JPanel deathButtons = new JPanel();
        deathButtons.add(keepDeath);
        deathButtons.add(discardDeath);
        deathPrompt.add(deathButtons);
        deathPrompt.setVisible(false);
        body.add(deathPrompt);

        add(body, BorderLayout.NORTH);

        startStop.addActionListener(e -> onClient(() -> {
            if (service == null) return;
            if (service.isTracking()) service.endSession(); else service.startSession();
        }));
        endTrip.addActionListener(e -> onClient(() -> {
            if (service != null && service.isTracking()) service.onBankOpened();
        }));
        discardTrip.addActionListener(e -> onClient(() -> {
            if (service != null && service.isTracking()) service.discardTrip();
        }));
        keepDeath.addActionListener(e -> { deathPrompt.setVisible(false); renderControls();
            onClient(() -> { if (service != null) service.resolveDeath(true); }); });
        discardDeath.addActionListener(e -> { deathPrompt.setVisible(false); renderControls();
            onClient(() -> { if (service != null) service.resolveDeath(false); }); });

        renderControls();
    }

    private void onClient(Runnable r) {
        clientThread.invoke(r);
    }

    void setService(TrackingService service, boolean loggedIn) {
        this.service = service;
        this.loggedIn = loggedIn;
        render();
    }

    void showDeathPrompt() {
        deathPrompt.setVisible(true);
        renderControls();
    }

    void render() {
        renderControls();
        renderStats();
    }

    private void renderControls() {
        boolean tracking = service != null && service.isTracking();
        startStop.setEnabled(loggedIn && service != null);
        startStop.setText(tracking ? "Stop tracking" : "Start tracking");
        endTrip.setEnabled(tracking);
        discardTrip.setEnabled(tracking);
        status.setText(!loggedIn ? "Log in to start tracking" : (tracking ? "Tracking…" : "Ready"));
        if (service == null || !loggedIn) {
            deathPrompt.setVisible(false);
        }
    }

    private void renderStats() {
        Optional<TripSnapshot> snap = service == null ? Optional.empty() : service.currentSnapshot();
        if (snap.isPresent()) {
            TripSnapshot s = snap.get();
            kills.setText(Integer.toString(s.kills));
            picked.setText(GpFormat.format(s.pickedGp));
            ground.setText(GpFormat.format(s.groundGp));
            supplies.setText(GpFormat.format(s.suppliesGp));
            tripGpHr.setText(GpFormat.format(s.gpPerHour));
        } else {
            for (JLabel l : new JLabel[]{kills, picked, ground, supplies, tripGpHr}) l.setText("-");
        }
        Optional<SessionSnapshot> sess = service == null ? Optional.empty() : service.currentSessionSnapshot();
        if (sess.isPresent()) {
            SessionSnapshot s = sess.get();
            sessTrips.setText(Integer.toString(s.tripCount));
            sessNet.setText(GpFormat.format(s.netProfit));
            sessXp.setText(GpFormat.format(s.totalXp));
            sessGpHr.setText(GpFormat.format(s.gpPerHour));
        } else {
            for (JLabel l : new JLabel[]{sessTrips, sessNet, sessXp, sessGpHr}) l.setText("-");
        }
    }
}
