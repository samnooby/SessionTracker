package com.goodrunetracker.adapter.runelite;

import com.goodrunetracker.adapter.GpFormat;
import com.goodrunetracker.adapter.SessionSnapshot;
import com.goodrunetracker.adapter.TrackingService;
import com.goodrunetracker.adapter.TripSnapshot;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.util.Map;
import java.util.Optional;
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.callback.ClientThread;

/** The live view: status + controls, current-trip card, session-so-far roll-up, death prompt. */
final class NowTab extends JPanel {

    private static final long MILLIS_PER_HOUR = 3_600_000L;

    private final ClientThread clientThread;
    private final Map<String, Icon> skillIcons;
    private TrackingService service;
    private boolean loggedIn;

    private final JLabel statusDot = new JLabel("●");
    private final JLabel status = new JLabel("Log in to start tracking");
    private final JLabel elapsed = new JLabel("");

    private final JButton startStop = Styles.button("Start tracking", Styles.ORANGE, Styles.PANEL);
    private final JButton endTrip = Styles.button("End trip", Styles.CARD, Styles.TEXT);
    private final JButton discardTrip = Styles.button("Discard", Styles.CARD, Styles.NEG);

    private final JLabel tripGpHr = new JLabel("-");
    private final JLabel tripXpHr = new JLabel("-");
    private final JLabel kills = Styles.valueLabel(Styles.TEXT);
    private final JLabel picked = Styles.valueLabel(Styles.GP);
    private final JLabel ground = Styles.valueLabel(Styles.MISSED);
    private final JLabel supplies = Styles.valueLabel(Styles.NEG);

    private final JLabel sessTrips = Styles.valueLabel(Styles.TEXT);
    private final JLabel sessNet = Styles.valueLabel(Styles.GP);
    private final JLabel sessXp = Styles.valueLabel(Styles.XP);
    private final JLabel sessGpHr = Styles.valueLabel(Styles.GP);

    private final JPanel deathPrompt = Styles.card();
    private final JButton keepDeath = Styles.button("Keep", Styles.CARD, Styles.TEXT);
    private final JButton discardDeath = Styles.button("Discard", Styles.CARD, Styles.NEG);

    NowTab(ClientThread clientThread, Map<String, Icon> skillIcons) {
        this.clientThread = clientThread;
        this.skillIcons = skillIcons;
        setBackground(Styles.PANEL);
        setLayout(new BorderLayout());

        JPanel body = new JPanel();
        body.setBackground(Styles.PANEL);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(8, 8, 8, 8));

        body.add(statusRow());
        body.add(Box.createVerticalStrut(8));
        body.add(controls());

        body.add(Styles.sectionHeader("Current trip"));
        body.add(currentCard());

        body.add(Styles.sectionHeader("Session so far"));
        body.add(sessionCard());

        body.add(Box.createVerticalStrut(8));
        body.add(buildDeathPrompt());

        add(body, BorderLayout.NORTH);

        startStop.addActionListener(e -> onClient(() -> {
            if (service == null) {
                return;
            }
            if (service.isTracking()) {
                service.endSession();
            } else {
                service.startSession();
            }
        }));
        endTrip.addActionListener(e -> onClient(() -> {
            if (service != null && service.isTracking()) {
                service.onBankOpened();
            }
        }));
        discardTrip.addActionListener(e -> onClient(() -> {
            if (service != null && service.isTracking()) {
                service.discardTrip();
            }
        }));
        keepDeath.addActionListener(e -> {
            deathPrompt.setVisible(false);
            renderControls();
            onClient(() -> {
                if (service != null) {
                    service.resolveDeath(true);
                }
            });
        });
        discardDeath.addActionListener(e -> {
            deathPrompt.setVisible(false);
            renderControls();
            onClient(() -> {
                if (service != null) {
                    service.resolveDeath(false);
                }
            });
        });

        renderControls();
    }

    private JPanel statusRow() {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(Styles.PANEL);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.X_AXIS));
        left.setBackground(Styles.PANEL);
        statusDot.setForeground(Styles.SUBTEXT);
        status.setForeground(Styles.TEXT);
        left.add(statusDot);
        left.add(Box.createHorizontalStrut(6));
        left.add(status);
        elapsed.setForeground(Styles.SUBTEXT);
        row.add(left, BorderLayout.WEST);
        row.add(elapsed, BorderLayout.EAST);
        Styles.capHeight(row);
        return row;
    }

    private JPanel controls() {
        JPanel wrap = new JPanel();
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
        wrap.setBackground(Styles.PANEL);
        wrap.setAlignmentX(Component.LEFT_ALIGNMENT);

        startStop.setAlignmentX(Component.LEFT_ALIGNMENT);
        Styles.capHeight(startStop);
        wrap.add(startStop);
        wrap.add(Box.createVerticalStrut(5));

        JPanel pair = new JPanel(new GridLayout(1, 2, 5, 0));
        pair.setBackground(Styles.PANEL);
        pair.setAlignmentX(Component.LEFT_ALIGNMENT);
        pair.add(endTrip);
        pair.add(discardTrip);
        Styles.capHeight(pair);
        wrap.add(pair);

        Styles.capHeight(wrap);
        return wrap;
    }

    private JPanel currentCard() {
        JPanel card = Styles.card();

        JPanel tiles = new JPanel(new GridLayout(1, 2, 6, 0));
        tiles.setBackground(Styles.CARD);
        tiles.setAlignmentX(Component.LEFT_ALIGNMENT);
        tiles.add(Styles.tile(tripGpHr, "GP/hr", Styles.GP));
        tiles.add(Styles.tile(tripXpHr, "XP/hr", Styles.XP));
        Styles.capHeight(tiles);
        card.add(tiles);
        card.add(Box.createVerticalStrut(6));

        JPanel grid = new JPanel(new GridLayout(0, 2, 0, 3));
        grid.setBackground(Styles.CARD);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        grid.add(Styles.keyLabel("Kills"));
        grid.add(kills);
        grid.add(Styles.keyLabel("Loot picked"));
        grid.add(picked);
        grid.add(Styles.keyLabel("On ground"));
        grid.add(ground);
        grid.add(Styles.keyLabel("Supplies"));
        grid.add(supplies);
        Styles.capHeight(grid);
        card.add(grid);

        return card;
    }

    private JPanel sessionCard() {
        JPanel card = Styles.card();
        JPanel grid = new JPanel(new GridLayout(0, 2, 0, 3));
        grid.setBackground(Styles.CARD);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        grid.add(Styles.keyLabel("Trips"));
        grid.add(sessTrips);
        grid.add(Styles.keyLabel("Net profit"));
        grid.add(sessNet);
        grid.add(Styles.keyLabel("Total XP"));
        grid.add(sessXp);
        grid.add(Styles.keyLabel("GP/hr"));
        grid.add(sessGpHr);
        Styles.capHeight(grid);
        card.add(grid);
        return card;
    }

    private JPanel buildDeathPrompt() {
        JLabel msg = new JLabel("<html>You died this trip — keep or discard it?</html>");
        msg.setForeground(Styles.MISSED);
        msg.setAlignmentX(Component.LEFT_ALIGNMENT);
        deathPrompt.add(msg);
        deathPrompt.add(Box.createVerticalStrut(6));
        JPanel pair = new JPanel(new GridLayout(1, 2, 5, 0));
        pair.setBackground(Styles.CARD);
        pair.setAlignmentX(Component.LEFT_ALIGNMENT);
        keepDeath.setForeground(Styles.GP);
        pair.add(keepDeath);
        pair.add(discardDeath);
        Styles.capHeight(pair);
        deathPrompt.add(pair);
        deathPrompt.setVisible(false);
        return deathPrompt;
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
        if (!loggedIn) {
            status.setText("Log in to start tracking");
            statusDot.setForeground(Styles.SUBTEXT);
        } else if (tracking) {
            status.setText("Tracking");
            statusDot.setForeground(Styles.GP);
        } else {
            status.setText("Ready");
            statusDot.setForeground(Styles.MISSED);
        }
        if (service == null || !loggedIn) {
            deathPrompt.setVisible(false);
        }
    }

    private void renderStats() {
        Optional<TripSnapshot> snap = service == null ? Optional.empty() : service.currentSnapshot();
        if (snap.isPresent()) {
            TripSnapshot s = snap.get();
            setSigned(tripGpHr, s.gpPerHour);
            long xpHr = s.durationMillis > 0 ? s.totalXp * MILLIS_PER_HOUR / s.durationMillis : 0;
            tripXpHr.setText(GpFormat.format(xpHr));
            kills.setText(Integer.toString(s.kills));
            picked.setText(GpFormat.format(s.pickedGp));
            ground.setText(GpFormat.format(s.groundGp));
            supplies.setText(GpFormat.format(s.suppliesGp));
            elapsed.setText(formatElapsed(s.durationMillis));
        } else {
            tripGpHr.setText("-");
            tripGpHr.setForeground(Styles.GP);
            tripXpHr.setText("-");
            kills.setText("-");
            picked.setText("-");
            ground.setText("-");
            supplies.setText("-");
            elapsed.setText("");
        }
        Optional<SessionSnapshot> sess = service == null ? Optional.empty() : service.currentSessionSnapshot();
        if (sess.isPresent()) {
            SessionSnapshot s = sess.get();
            sessTrips.setText(Integer.toString(s.tripCount));
            setSigned(sessNet, s.netProfit);
            sessXp.setText(GpFormat.format(s.totalXp));
            setSigned(sessGpHr, s.gpPerHour);
        } else {
            sessTrips.setText("-");
            sessNet.setText("-");
            sessNet.setForeground(Styles.GP);
            sessXp.setText("-");
            sessGpHr.setText("-");
            sessGpHr.setForeground(Styles.GP);
        }
    }

    private static void setSigned(JLabel label, long value) {
        label.setText(GpFormat.format(value));
        label.setForeground(value < 0 ? Styles.NEG : Styles.GP);
    }

    private static String formatElapsed(long ms) {
        long totalSec = ms / 1000;
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        return h > 0
                ? String.format("%d:%02d:%02d", h, m, s)
                : String.format("%d:%02d", m, s);
    }
}
