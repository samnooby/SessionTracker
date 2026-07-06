package com.sessiontracker.adapter.runelite;

import com.sessiontracker.adapter.DurationFormat;
import com.sessiontracker.adapter.GpFormat;
import com.sessiontracker.adapter.SessionHistory;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridLayout;
import java.util.List;
import java.util.Locale;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.FontManager;

/** Per-category stats: GP/hr-sorted cards, drill-in to averages + exact supply breakdown. */
final class StatsTab extends JPanel {

    private static final String CARDS = "cards";
    private static final String DETAIL = "detail";

    private final ClientThread clientThread;
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel();
    private final JPanel cardsBody = new JPanel();
    private final JPanel detailBody = new JPanel();

    private SessionHistory history;

    StatsTab(ClientThread clientThread) {
        this.clientThread = clientThread;
        setBackground(Styles.PANEL);
        setLayout(new BorderLayout());
        root.setLayout(cards);
        root.setBackground(Styles.PANEL);
        cardsBody.setLayout(new BoxLayout(cardsBody, BoxLayout.Y_AXIS));
        cardsBody.setBackground(Styles.PANEL);
        cardsBody.setBorder(new EmptyBorder(8, 8, 8, 8));
        detailBody.setLayout(new BoxLayout(detailBody, BoxLayout.Y_AXIS));
        detailBody.setBackground(Styles.PANEL);
        detailBody.setBorder(new EmptyBorder(8, 8, 8, 8));
        root.add(wrapNorth(cardsBody), CARDS);
        root.add(wrapNorth(detailBody), DETAIL);
        add(root, BorderLayout.CENTER);
        cards.show(root, CARDS);
    }

    private static JPanel wrapNorth(JPanel inner) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Styles.PANEL);
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
            cardsBody.add(Styles.keyLabel("Log in to view stats"));
        } else {
            List<SessionHistory.CategoryStatsView> stats = history.categoryStats();
            if (stats.isEmpty()) {
                cardsBody.add(Styles.keyLabel("No sessions yet"));
            }
            for (SessionHistory.CategoryStatsView c : stats) {
                cardsBody.add(categoryCard(c));
                cardsBody.add(Box.createVerticalStrut(5));
            }
        }
        cardsBody.revalidate();
        cardsBody.repaint();
        cards.show(root, CARDS);
    }

    private JPanel categoryCard(SessionHistory.CategoryStatsView c) {
        JPanel card = Styles.card();

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Styles.CARD);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel name = new JLabel(c.category);
        name.setFont(FontManager.getRunescapeBoldFont());
        name.setForeground(Styles.TEXT);
        header.add(name, BorderLayout.CENTER);
        header.add(new JLabel(Styles.caret(Styles.CARET_RIGHT, Styles.SUBTEXT)), BorderLayout.EAST);
        Styles.capHeight(header);
        card.add(header);

        String sessionWord = c.sessionCount == 1 ? "session" : "sessions";
        String tripWord = c.tripCount == 1 ? "trip" : "trips";
        JLabel meta = Styles.keyLabel(c.sessionCount + " " + sessionWord + " · " + c.tripCount + " " + tripWord);
        meta.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(meta);
        card.add(Box.createVerticalStrut(6));

        card.add(tiles(c.gpPerHour, c.xpPerHour));

        Styles.clickable(card, () -> showDetail(c.category));
        return card;
    }

    /** A GP/hr (green, sign-aware) + XP/hr (blue) tile pair. */
    private static JPanel tiles(long gpPerHour, long xpPerHour) {
        JLabel gp = new JLabel(GpFormat.format(gpPerHour));
        JLabel xp = new JLabel(GpFormat.format(xpPerHour));
        JPanel row = new JPanel(new GridLayout(1, 2, 6, 0));
        row.setBackground(Styles.CARD);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(Styles.tile(gp, "GP/hr", gpPerHour < 0 ? Styles.NEG : Styles.GP));
        row.add(Styles.tile(xp, "XP/hr", Styles.XP));
        Styles.capHeight(row);
        return row;
    }

    private void showDetail(String category) {
        clientThread.invoke(() -> {
            SessionHistory.CategoryDetail d = history.categoryDetail(category);
            SwingUtilities.invokeLater(() -> renderDetail(category, d));
        });
    }

    private void renderDetail(String category, SessionHistory.CategoryDetail d) {
        detailBody.removeAll();

        JButton back = Styles.linkButton("Back", Styles.ORANGE);
        back.setIcon(Styles.caret(Styles.CARET_LEFT, Styles.ORANGE));
        back.setIconTextGap(5);
        back.setAlignmentX(Component.LEFT_ALIGNMENT);
        back.addActionListener(e -> cards.show(root, CARDS));
        detailBody.add(back);
        detailBody.add(Box.createVerticalStrut(6));

        JLabel name = new JLabel(category);
        name.setFont(FontManager.getRunescapeBoldFont());
        name.setForeground(Styles.TEXT);
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailBody.add(name);
        detailBody.add(Box.createVerticalStrut(4));
        detailBody.add(tiles(d.gpPerHour, d.xpPerHour));

        detailBody.add(Styles.sectionHeader("Per hour"));
        JPanel perHourCard = Styles.card();
        JPanel perHour = grid();
        perHour.add(Styles.keyLabel("Combat GP/hr"));
        perHour.add(signedValue(d.combatGpPerHour));
        perHour.add(Styles.keyLabel("Gather GP/hr"));
        perHour.add(signedValue(d.gatherGpPerHour));
        perHour.add(Styles.keyLabel("Supplies GP/hr"));
        JLabel supHr = Styles.valueLabel(Styles.NEG);
        supHr.setText(GpFormat.format(d.suppliesGpPerHour));
        perHour.add(supHr);
        Styles.capHeight(perHour);
        perHourCard.add(perHour);
        detailBody.add(perHourCard);

        detailBody.add(Styles.sectionHeader("Per-trip averages"));
        JPanel avgCard = Styles.card();
        JPanel avg = grid();
        avg.add(Styles.keyLabel("Net profit"));
        avg.add(signedValue(d.avgNetProfitPerTrip));
        avg.add(Styles.keyLabel("Missed"));
        JLabel missed = Styles.valueLabel(Styles.MISSED);
        missed.setText(GpFormat.format(d.avgMissedPerTrip));
        avg.add(missed);
        avg.add(Styles.keyLabel("Trip length"));
        JLabel len = Styles.valueLabel(Styles.TEXT);
        len.setText(DurationFormat.compact(d.avgTripDurationMillis));
        avg.add(len);
        avg.add(Styles.keyLabel("Kills"));
        JLabel kills = Styles.valueLabel(Styles.TEXT);
        kills.setText(String.format(Locale.US, "%.1f", d.avgKillsPerTrip));
        avg.add(kills);
        Styles.capHeight(avg);
        avgCard.add(avg);
        detailBody.add(avgCard);

        detailBody.add(Styles.sectionHeader("Per session"));
        JPanel sessionCard = Styles.card();
        JPanel sessionGrid = grid();
        sessionGrid.add(Styles.keyLabel("Avg session length"));
        JLabel sessionLen = Styles.valueLabel(Styles.TEXT);
        sessionLen.setText(DurationFormat.compact(d.avgSessionDurationMillis));
        sessionGrid.add(sessionLen);
        Styles.capHeight(sessionGrid);
        sessionCard.add(sessionGrid);
        detailBody.add(sessionCard);

        addItemTable("Avg picked up / trip", d.pickedAverages, d.avgPickedGpPerTrip, Styles.GP);
        addItemTable("Avg gathered / trip", d.gatheredAverages, d.avgGatheredGpPerTrip, Styles.GP);
        addItemTable("Avg used loot / trip", d.usedLootAverages, d.avgUsedLootGpPerTrip, Styles.TEXT);
        addItemTable("Avg supplies / trip", d.supplies, d.avgTotalSuppliesGpPerTrip, Styles.NEG);
        addItemTable("Avg left on ground / trip", d.missedAverages, d.avgMissedPerTrip, Styles.MISSED);
        addItemTable("Gross avg drops / trip", d.droppedAverages, d.avgDroppedGpPerTrip, Styles.TEXT);

        detailBody.add(Styles.sectionHeader("XP averages"));
        JPanel xpCard = Styles.card();
        if (d.xpAverages.isEmpty()) {
            JLabel none = Styles.keyLabel("None");
            none.setAlignmentX(Component.LEFT_ALIGNMENT);
            xpCard.add(none);
        } else {
            JPanel xpGrid = new JPanel(new GridLayout(0, 3, 6, 3));
            xpGrid.setBackground(Styles.CARD);
            xpGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
            xpGrid.add(Styles.keyLabel("Skill"));
            xpGrid.add(rightValue("/trip", Styles.SUBTEXT));
            xpGrid.add(rightValue("/hr", Styles.SUBTEXT));
            long totalAvgTrip = 0;
            for (SessionHistory.SkillXpAverage a : d.xpAverages) {
                totalAvgTrip += a.avgXpPerTrip;
                xpGrid.add(Styles.keyLabel(a.skill));
                xpGrid.add(rightValue(GpFormat.format(a.avgXpPerTrip), Styles.XP));
                xpGrid.add(rightValue(GpFormat.format(a.xpPerHour), Styles.XP));
            }
            xpGrid.add(boldLabel("Total", Styles.TEXT, SwingConstants.LEADING));
            xpGrid.add(boldLabel(GpFormat.format(totalAvgTrip), Styles.XP, SwingConstants.RIGHT));
            xpGrid.add(boldLabel(GpFormat.format(d.xpPerHour), Styles.XP, SwingConstants.RIGHT));
            Styles.capHeight(xpGrid);
            xpCard.add(xpGrid);
        }
        detailBody.add(xpCard);

        detailBody.add(Styles.sectionHeader("Kill averages"));
        JPanel killCard = Styles.card();
        if (d.killAverages.isEmpty()) {
            JLabel none = Styles.keyLabel("None");
            none.setAlignmentX(Component.LEFT_ALIGNMENT);
            killCard.add(none);
        } else {
            JPanel killGrid = new JPanel(new GridLayout(0, 3, 6, 3));
            killGrid.setBackground(Styles.CARD);
            killGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
            killGrid.add(Styles.keyLabel("NPC"));
            killGrid.add(rightValue("/trip", Styles.SUBTEXT));
            killGrid.add(rightValue("/hr", Styles.SUBTEXT));
            double totalTrip = 0;
            double totalHr = 0;
            for (SessionHistory.NpcKillAverage k : d.killAverages) {
                totalTrip += k.avgPerTrip;
                totalHr += k.perHour;
                killGrid.add(Styles.keyLabel(k.npc));
                killGrid.add(rightValue(String.format(Locale.US, "%.1f", k.avgPerTrip), Styles.TEXT));
                killGrid.add(rightValue(String.format(Locale.US, "%.1f", k.perHour), Styles.TEXT));
            }
            killGrid.add(boldLabel("Total", Styles.TEXT, SwingConstants.LEADING));
            killGrid.add(boldLabel(String.format(Locale.US, "%.1f", totalTrip), Styles.TEXT, SwingConstants.RIGHT));
            killGrid.add(boldLabel(String.format(Locale.US, "%.1f", totalHr), Styles.TEXT, SwingConstants.RIGHT));
            Styles.capHeight(killGrid);
            killCard.add(killGrid);
        }
        detailBody.add(killCard);

        detailBody.revalidate();
        detailBody.repaint();
        cards.show(root, DETAIL);
    }

    /**
     * A collapsible item-average section. The per-trip total sits (bold) in the clickable
     * header; the individual item rows stay hidden until the header is expanded, so the
     * detail view isn't bloated at first viewing.
     */
    private void addItemTable(String header, List<SessionHistory.ItemAverage> items,
                              long total, Color valueColor) {
        JPanel content = Styles.card();
        if (items.isEmpty()) {
            JLabel none = Styles.keyLabel("None");
            none.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(none);
        } else {
            JPanel g = grid();
            for (SessionHistory.ItemAverage a : items) {
                g.add(Styles.keyLabel(a.label + "  " + String.format(Locale.US, "%.1f", a.avgQtyPerTrip)));
                JLabel v = Styles.valueLabel(valueColor);
                v.setText(GpFormat.format(a.avgGpPerTrip));
                g.add(v);
            }
            Styles.capHeight(g);
            content.add(g);
        }
        content.setVisible(false);

        JLabel caretIcon = new JLabel(Styles.caret(Styles.CARET_RIGHT, Styles.SUBTEXT));
        JLabel title = new JLabel(header);
        title.setFont(FontManager.getRunescapeSmallFont());
        title.setForeground(Styles.TEXT);
        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.X_AXIS));
        left.setBackground(Styles.CARD);
        left.add(caretIcon);
        left.add(Box.createHorizontalStrut(6));
        left.add(title);

        JPanel headerRow = new JPanel(new BorderLayout(6, 0));
        headerRow.setBackground(Styles.CARD);
        headerRow.setBorder(new EmptyBorder(6, 9, 6, 9));
        headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerRow.add(left, BorderLayout.WEST);
        headerRow.add(boldLabel(GpFormat.format(total), valueColor, SwingConstants.RIGHT), BorderLayout.EAST);
        Styles.capHeight(headerRow);

        Styles.clickable(headerRow, () -> {
            boolean show = !content.isVisible();
            content.setVisible(show);
            caretIcon.setIcon(Styles.caret(show ? Styles.CARET_DOWN : Styles.CARET_RIGHT, Styles.SUBTEXT));
            detailBody.revalidate();
            detailBody.repaint();
        });

        detailBody.add(Box.createVerticalStrut(4));
        detailBody.add(headerRow);
        detailBody.add(content);
    }

    private static JPanel grid() {
        JPanel g = new JPanel(new GridLayout(0, 2, 0, 3));
        g.setBackground(Styles.CARD);
        g.setAlignmentX(Component.LEFT_ALIGNMENT);
        return g;
    }

    private static JLabel rightValue(String text, Color color) {
        JLabel l = Styles.valueLabel(color);
        l.setText(text);
        return l;
    }

    private static JLabel boldLabel(String text, Color color, int align) {
        JLabel l = new JLabel(text);
        l.setFont(FontManager.getRunescapeBoldFont());
        l.setForeground(color);
        l.setHorizontalAlignment(align);
        return l;
    }

    private static JLabel signedValue(long value) {
        JLabel l = Styles.valueLabel(value < 0 ? Styles.NEG : Styles.GP);
        l.setText(GpFormat.format(value));
        return l;
    }
}
