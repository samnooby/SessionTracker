package com.goodrunetracker.adapter.runelite;

import com.goodrunetracker.adapter.GpFormat;
import com.goodrunetracker.adapter.SessionHistory;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.GridLayout;
import java.util.List;
import java.util.Locale;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.callback.ClientThread;

/** Per-category stats: GP/hr-sorted cards, drill-in to averages + exact supply breakdown. */
final class StatsTab extends JPanel {

    private static final String CARDS = "cards";
    private static final String DETAIL = "detail";
    private static final long MILLIS_PER_MINUTE = 60_000L;

    private final ClientThread clientThread;
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel();
    private final JPanel cardsBody = new JPanel();
    private final JPanel detailBody = new JPanel();

    private SessionHistory history;

    StatsTab(ClientThread clientThread) {
        this.clientThread = clientThread;
        setLayout(new BorderLayout());
        root.setLayout(cards);
        cardsBody.setLayout(new BoxLayout(cardsBody, BoxLayout.Y_AXIS));
        detailBody.setLayout(new BoxLayout(detailBody, BoxLayout.Y_AXIS));
        root.add(wrapNorth(cardsBody), CARDS);
        root.add(wrapNorth(detailBody), DETAIL);
        add(root, BorderLayout.CENTER);
        cards.show(root, CARDS);
    }

    private static JPanel wrapNorth(JPanel inner) {
        JPanel p = new JPanel(new BorderLayout());
        p.add(inner, BorderLayout.NORTH);
        return p;
    }

    void setHistory(SessionHistory history) {
        this.history = history;
        reload();
    }

    void reload() {
        cardsBody.removeAll();
        if (history == null) {
            cardsBody.add(new JLabel("Log in to view stats"));
        } else {
            List<SessionHistory.CategoryStatsView> stats = history.categoryStats();
            if (stats.isEmpty()) {
                cardsBody.add(new JLabel("No sessions yet"));
            }
            for (SessionHistory.CategoryStatsView c : stats) {
                cardsBody.add(categoryCard(c));
            }
        }
        cardsBody.revalidate();
        cardsBody.repaint();
        cards.show(root, CARDS);
    }

    private JButton categoryCard(SessionHistory.CategoryStatsView c) {
        JButton b = new JButton("<html><b>" + c.category + "</b> ›<br>"
                + GpFormat.format(c.gpPerHour) + " gp/hr · " + GpFormat.format(c.xpPerHour)
                + " xp/hr<br>" + c.sessionCount + " sessions · " + c.tripCount + " trips</html>");
        b.addActionListener(e -> showDetail(c.category));
        return b;
    }

    private void showDetail(String category) {
        clientThread.invoke(() -> {
            SessionHistory.CategoryDetail d = history.categoryDetail(category);
            SwingUtilities.invokeLater(() -> renderDetail(category, d));
        });
    }

    private void renderDetail(String category, SessionHistory.CategoryDetail d) {
        detailBody.removeAll();
        JButton back = new JButton("‹ Back");
        back.addActionListener(e -> cards.show(root, CARDS));
        detailBody.add(back);
        detailBody.add(new JLabel("<html><b>" + category + "</b></html>"));
        detailBody.add(new JLabel(GpFormat.format(d.gpPerHour) + " gp/hr · "
                + GpFormat.format(d.xpPerHour) + " xp/hr"));

        detailBody.add(new JLabel("Per-trip averages"));
        JPanel avg = new JPanel(new GridLayout(0, 2));
        avg.add(new JLabel("Net profit")); avg.add(new JLabel(GpFormat.format(d.avgNetProfitPerTrip)));
        avg.add(new JLabel("Missed")); avg.add(new JLabel(GpFormat.format(d.avgMissedPerTrip)));
        avg.add(new JLabel("Trip length")); avg.add(new JLabel(
                (d.avgTripDurationMillis / MILLIS_PER_MINUTE) + "m"));
        avg.add(new JLabel("Kills")); avg.add(new JLabel(
                String.format(Locale.US, "%.1f", d.avgKillsPerTrip)));
        detailBody.add(avg);

        detailBody.add(new JLabel("Avg supplies / trip"));
        JPanel sup = new JPanel(new GridLayout(0, 2));
        for (SessionHistory.SupplyAverage s : d.supplies) {
            sup.add(new JLabel(s.label + "  " + String.format(Locale.US, "%.1f", s.avgQtyPerTrip)));
            sup.add(new JLabel(GpFormat.format(s.avgGpPerTrip)));
        }
        sup.add(new JLabel("<html><b>Total</b></html>"));
        sup.add(new JLabel("<html><b>" + GpFormat.format(d.avgTotalSuppliesGpPerTrip) + "</b></html>"));
        detailBody.add(sup);

        detailBody.revalidate();
        detailBody.repaint();
        cards.show(root, DETAIL);
    }
}
