package com.sessiontracker.adapter.runelite;

import com.sessiontracker.adapter.GpFormat;
import com.sessiontracker.adapter.NpcKills;
import com.sessiontracker.adapter.SessionHistory;
import com.sessiontracker.adapter.SkillXp;
import com.sessiontracker.adapter.TrackingService;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.List;
import java.util.Map;
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.FontManager;

/** Sessions browser: accordion list (session -> trips) with drill-in trip detail and inline edit. */
final class SessionsTab extends JPanel {

    private static final String LIST = "list";
    private static final String DETAIL = "detail";

    private final ClientThread clientThread;
    private final Map<String, Icon> skillIcons;
    private final ItemIconProvider itemIcons;
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel();
    private final JPanel listBody = new JPanel();
    private final JPanel detailBody = new JPanel();

    private TrackingService service;
    private SessionHistory history;
    private String expandedSessionId;
    private String editingSessionId;

    SessionsTab(ClientThread clientThread, Map<String, Icon> skillIcons, ItemIconProvider itemIcons) {
        this.clientThread = clientThread;
        this.skillIcons = skillIcons;
        this.itemIcons = itemIcons;
        setBackground(Styles.PANEL);
        setLayout(new BorderLayout());
        root.setLayout(cards);
        root.setBackground(Styles.PANEL);
        listBody.setLayout(new BoxLayout(listBody, BoxLayout.Y_AXIS));
        listBody.setBackground(Styles.PANEL);
        listBody.setBorder(new EmptyBorder(8, 8, 8, 8));
        detailBody.setLayout(new BoxLayout(detailBody, BoxLayout.Y_AXIS));
        detailBody.setBackground(Styles.PANEL);
        detailBody.setBorder(new EmptyBorder(8, 8, 8, 8));
        root.add(wrapNorth(listBody), LIST);
        root.add(wrapNorth(detailBody), DETAIL);
        add(root, BorderLayout.CENTER);
        cards.show(root, LIST);
    }

    private static JPanel wrapNorth(JPanel inner) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Styles.PANEL);
        p.add(inner, BorderLayout.NORTH);
        return p;
    }

    void setContext(TrackingService service, SessionHistory history) {
        this.service = service;
        this.history = history;
        reload();
    }

    void reload() {
        renderList();
        cards.show(root, LIST);
    }

    private void renderList() {
        listBody.removeAll();
        if (history == null) {
            listBody.add(Styles.keyLabel("Log in to view sessions"));
        } else {
            List<SessionHistory.SessionSummary> sessions = history.sessionsNewestFirst();
            if (sessions.isEmpty()) {
                listBody.add(Styles.keyLabel("No sessions yet"));
            }
            for (SessionHistory.SessionSummary s : sessions) {
                listBody.add(sessionRow(s));
                if (s.sessionId.equals(editingSessionId)) {
                    listBody.add(Box.createVerticalStrut(2));
                    listBody.add(editForm(s));
                } else if (s.sessionId.equals(expandedSessionId)) {
                    listBody.add(Box.createVerticalStrut(2));
                    listBody.add(sessionSummaryCard(s));
                    List<SessionHistory.TripSummary> trips = history.tripsFor(s.sessionId);
                    for (int i = 0; i < trips.size(); i++) {
                        listBody.add(Box.createVerticalStrut(2));
                        listBody.add(tripRow(s.sessionId, trips.get(i), i + 1));
                    }
                }
                listBody.add(Box.createVerticalStrut(4));
            }
        }
        listBody.revalidate();
        listBody.repaint();
    }

    private JPanel sessionRow(SessionHistory.SessionSummary s) {
        boolean expanded = s.sessionId.equals(expandedSessionId);
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(Styles.CARD);
        row.setBorder(new EmptyBorder(7, 8, 7, 8));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel caret = new JLabel(Styles.caret(expanded ? Styles.CARET_DOWN : Styles.CARET_RIGHT, Styles.ORANGE));
        caret.setBorder(new EmptyBorder(0, 0, 0, 7));

        JPanel textBlock = new JPanel();
        textBlock.setLayout(new BoxLayout(textBlock, BoxLayout.Y_AXIS));
        textBlock.setBackground(Styles.CARD);

        String displayName = (s.name == null || s.name.isEmpty()) ? s.category : s.name;
        JLabel name = new JLabel(displayName);
        name.setFont(FontManager.getRunescapeFont());
        name.setForeground(Styles.TEXT);
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        name.setToolTipText(displayName);

        JPanel meta = new JPanel();
        meta.setLayout(new BoxLayout(meta, BoxLayout.X_AXIS));
        meta.setBackground(Styles.CARD);
        meta.setAlignmentX(Component.LEFT_ALIGNMENT);
        String tripWord = s.tripCount == 1 ? "trip" : "trips";
        JLabel metaText = Styles.keyLabel(s.tripCount + " " + tripWord + " · ");
        JLabel net = new JLabel(GpFormat.format(s.netProfit));
        net.setFont(FontManager.getRunescapeSmallFont());
        net.setForeground(s.netProfit < 0 ? Styles.NEG : Styles.GP);
        meta.add(metaText);
        meta.add(net);

        textBlock.add(name);
        textBlock.add(meta);

        JPanel left = new JPanel(new BorderLayout());
        left.setBackground(Styles.CARD);
        left.add(caret, BorderLayout.WEST);
        left.add(textBlock, BorderLayout.CENTER);
        row.add(left, BorderLayout.CENTER);

        JButton edit = Styles.linkButton("Edit", Styles.ORANGE);
        edit.addActionListener(e -> {
            editingSessionId = s.sessionId.equals(editingSessionId) ? null : s.sessionId;
            expandedSessionId = null;
            renderList();
        });
        row.add(edit, BorderLayout.EAST);

        Styles.capHeight(row);
        Styles.clickable(row, () -> {
            expandedSessionId = s.sessionId.equals(expandedSessionId) ? null : s.sessionId;
            editingSessionId = null;
            renderList();
        });
        return row;
    }

    private JPanel tripRow(String sessionId, SessionHistory.TripSummary t, int number) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(Styles.TILE);
        row.setBorder(new EmptyBorder(6, 16, 6, 8));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel meta = new JPanel();
        meta.setLayout(new BoxLayout(meta, BoxLayout.X_AXIS));
        meta.setBackground(Styles.TILE);
        JLabel label = Styles.keyLabel("Trip " + number + " · " + t.kills + " kills · ");
        JLabel net = new JLabel(GpFormat.format(t.netProfit));
        net.setFont(FontManager.getRunescapeSmallFont());
        net.setForeground(t.netProfit < 0 ? Styles.NEG : Styles.GP);
        meta.add(label);
        meta.add(net);
        row.add(meta, BorderLayout.CENTER);

        row.add(new JLabel(Styles.caret(Styles.CARET_RIGHT, Styles.SUBTEXT)), BorderLayout.EAST);

        Styles.capHeight(row);
        Styles.clickable(row, () -> showDetail(sessionId, t.tripId));
        return row;
    }

    private JPanel editForm(SessionHistory.SessionSummary s) {
        JPanel form = Styles.card();

        JLabel nameKey = Styles.keyLabel("Name");
        nameKey.setAlignmentX(Component.LEFT_ALIGNMENT);
        JTextField name = new JTextField(s.name == null ? "" : s.name);
        styleField(name);
        form.add(nameKey);
        form.add(name);
        form.add(Box.createVerticalStrut(6));

        JLabel catKey = Styles.keyLabel("Category");
        catKey.setAlignmentX(Component.LEFT_ALIGNMENT);
        JTextField category = new JTextField(s.category == null ? "" : s.category);
        styleField(category);
        form.add(catKey);
        form.add(category);

        List<String> existing = history.categories();
        if (!existing.isEmpty()) {
            JPanel chips = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 4)) {
                @Override
                public Dimension getMaximumSize() {
                    return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
                }
            };
            chips.setBackground(Styles.CARD);
            chips.setBorder(new EmptyBorder(4, 0, 0, 0));
            chips.setAlignmentX(Component.LEFT_ALIGNMENT);
            for (String c : existing) {
                JButton chip = Styles.chip(c);
                chip.addActionListener(e -> category.setText(c));
                chips.add(chip);
            }
            form.add(chips);
        }
        form.add(Box.createVerticalStrut(8));

        JPanel buttons = new JPanel(new GridLayout(1, 2, 5, 0));
        buttons.setBackground(Styles.CARD);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton save = Styles.button("Save", Styles.ORANGE, Styles.PANEL);
        JButton cancel = Styles.button("Cancel", Styles.TILE, Styles.TEXT);
        save.addActionListener(e -> {
            String newName = name.getText();
            String newCat = category.getText().isEmpty() ? s.category : category.getText();
            applyEdit(s.sessionId, newName, newCat);
            editingSessionId = null;
            renderList();
        });
        cancel.addActionListener(e -> {
            editingSessionId = null;
            renderList();
        });
        buttons.add(save);
        buttons.add(cancel);
        Styles.capHeight(buttons);
        form.add(buttons);

        boolean isActive = service != null && s.sessionId.equals(service.activeSessionId());
        if (!isActive) {
            form.add(Box.createVerticalStrut(5));
            JButton delete = Styles.button("Delete session", Styles.NEG, Styles.PANEL);
            delete.setAlignmentX(Component.LEFT_ALIGNMENT);
            delete.addActionListener(e -> confirmAndDeleteSession(s.sessionId));
            Styles.capHeight(delete);
            form.add(delete);
        }
        return form;
    }

    private static void styleField(JTextField field) {
        field.setBackground(Styles.TILE);
        field.setForeground(Styles.TEXT);
        field.setCaretColor(Styles.TEXT);
        field.setFont(FontManager.getRunescapeSmallFont());
        field.setBorder(new EmptyBorder(4, 5, 4, 5));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        Styles.capHeight(field);
    }

    private void applyEdit(String sessionId, String newName, String newCategory) {
        boolean isActive = service != null && sessionId.equals(service.activeSessionId());
        if (isActive) {
            clientThread.invoke(() -> {
                service.renameActiveSession(newName);
                service.recategorizeActiveSession(newCategory);
            });
        } else {
            history.rename(sessionId, newName);
            history.recategorize(sessionId, newCategory);
        }
    }

    private void confirmAndDeleteSession(String sessionId) {
        if (service != null && sessionId.equals(service.activeSessionId())) {
            return; // never delete the in-progress session
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "Delete this session? This cannot be undone.",
                "Delete session", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        history.deleteSession(sessionId);
        editingSessionId = null;
        expandedSessionId = null;
        reload();
    }

    private void confirmAndDeleteTrip(String sessionId, String tripId) {
        if (service != null && sessionId.equals(service.activeSessionId())) {
            return; // never delete a trip from the in-progress session
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "Delete this trip? This cannot be undone.",
                "Delete trip", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        history.deleteTrip(sessionId, tripId);
        editingSessionId = null;
        expandedSessionId = null;
        reload();
    }

    private void showDetail(String sessionId, String tripId) {
        clientThread.invoke(() -> {
            SessionHistory.TripDetail d = history.tripDetail(sessionId, tripId);
            SwingUtilities.invokeLater(() -> renderDetail(sessionId, tripId, d));
        });
    }

    private void renderDetail(String sessionId, String tripId, SessionHistory.TripDetail d) {
        detailBody.removeAll();

        JButton back = Styles.linkButton("Back", Styles.ORANGE);
        back.setIcon(Styles.caret(Styles.CARET_LEFT, Styles.ORANGE));
        back.setIconTextGap(5);
        back.addActionListener(e -> cards.show(root, LIST));

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(Styles.PANEL);
        topBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        topBar.add(back, BorderLayout.WEST);
        boolean isActive = service != null && sessionId.equals(service.activeSessionId());
        if (!isActive) {
            JButton delete = Styles.linkButton("Delete trip", Styles.NEG);
            delete.addActionListener(e -> confirmAndDeleteTrip(sessionId, tripId));
            topBar.add(delete, BorderLayout.EAST);
        }
        Styles.capHeight(topBar);
        detailBody.add(topBar);
        detailBody.add(Box.createVerticalStrut(6));

        if (d != null) {
            JPanel summary = Styles.card();
            JPanel grid = new JPanel(new GridLayout(0, 2, 0, 3));
            grid.setBackground(Styles.CARD);
            grid.setAlignmentX(Component.LEFT_ALIGNMENT);
            grid.add(Styles.keyLabel("Net profit"));
            grid.add(signedValue(d.netProfit));
            grid.add(Styles.keyLabel("Missed"));
            JLabel missed = Styles.valueLabel(Styles.MISSED);
            missed.setText(GpFormat.format(d.missedValue));
            grid.add(missed);
            Styles.capHeight(grid);
            summary.add(grid);
            detailBody.add(summary);

            addKillsGroup(d.killsByNpc);

            addGroup("Picked up", d.pickedUp, Styles.GP);
            addGroup("Gathered", d.gathered, Styles.GP);
            addGroup("Used loot", d.usedLoot, Styles.TEXT);
            addGroup("Left on ground", d.leftOnGround, Styles.MISSED);
            addGroup("Supplies used", d.suppliesUsed, Styles.NEG);
            addXpGroup(d.xpGained);
        }
        detailBody.revalidate();
        detailBody.repaint();
        cards.show(root, DETAIL);
    }

    private void addKillsGroup(List<NpcKills> kills) {
        detailBody.add(Styles.sectionHeader("Kills"));
        JPanel card = Styles.card();
        if (kills == null || kills.isEmpty()) {
            JLabel none = Styles.keyLabel("None");
            none.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(none);
        } else {
            JPanel grid = new JPanel(new GridLayout(0, 2, 0, 3));
            grid.setBackground(Styles.CARD);
            grid.setAlignmentX(Component.LEFT_ALIGNMENT);
            int total = 0;
            for (NpcKills k : kills) {
                total += k.count;
                JLabel npc = Styles.keyLabel(k.npc);
                npc.setToolTipText(k.npc);
                grid.add(npc);
                JLabel v = Styles.valueLabel(Styles.TEXT);
                v.setText(Integer.toString(k.count));
                grid.add(v);
            }
            Styles.addBoldRow(grid, "Total", Integer.toString(total), Styles.TEXT);
            Styles.capHeight(grid);
            card.add(grid);
        }
        detailBody.add(card);
    }

    private void addXpGroup(List<SkillXp> xp) {
        detailBody.add(Styles.sectionHeader("XP gained"));
        JPanel card = Styles.card();
        if (xp == null || xp.isEmpty()) {
            JLabel none = Styles.keyLabel("None");
            none.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(none);
        } else {
            JPanel grid = new JPanel(new GridLayout(0, 2, 0, 3));
            grid.setBackground(Styles.CARD);
            grid.setAlignmentX(Component.LEFT_ALIGNMENT);
            long total = 0;
            for (SkillXp s : xp) {
                total += s.xp;
                JLabel skill = Styles.skillLabel(s.skill, skillIcons.get(s.skill));
                skill.setToolTipText(s.skill);
                grid.add(skill);
                JLabel v = Styles.valueLabel(Styles.XP);
                v.setText(GpFormat.format(s.xp));
                grid.add(v);
            }
            Styles.addBoldRow(grid, "Total", GpFormat.format(total), Styles.XP);
            Styles.capHeight(grid);
            card.add(grid);
        }
        detailBody.add(card);
    }

    private void addGroup(String title, List<SessionHistory.ItemLine> lines, java.awt.Color valueColor) {
        detailBody.add(Styles.sectionHeader(title));
        JPanel card = Styles.card();
        if (lines.isEmpty()) {
            JLabel none = Styles.keyLabel("None");
            none.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(none);
        } else {
            JPanel grid = new JPanel(new GridLayout(0, 2, 0, 3));
            grid.setBackground(Styles.CARD);
            grid.setAlignmentX(Component.LEFT_ALIGNMENT);
            for (SessionHistory.ItemLine l : lines) {
                JLabel k = Styles.keyLabel(l.label + " ×" + l.quantity);
                k.setToolTipText(Styles.itemTooltip(l.label, l.quantity, l.isPotion, l.dosesPerPotion));
                if (l.iconItemId != null) {
                    itemIcons.apply(k, l.iconItemId);
                }
                grid.add(k);
                JLabel v = Styles.valueLabel(valueColor);
                v.setText(GpFormat.format(l.gpValue));
                grid.add(v);
            }
            Styles.capHeight(grid);
            card.add(grid);
        }
        detailBody.add(card);
    }

    private JPanel sessionSummaryCard(SessionHistory.SessionSummary s) {
        JPanel card = Styles.card();

        JLabel gp = new JLabel(GpFormat.format(s.gpPerHour));
        JLabel xp = new JLabel(GpFormat.format(s.xpPerHour));
        JPanel tiles = new JPanel(new GridLayout(1, 2, 6, 0));
        tiles.setBackground(Styles.CARD);
        tiles.setAlignmentX(Component.LEFT_ALIGNMENT);
        tiles.add(Styles.tile(gp, "GP/hr", s.gpPerHour < 0 ? Styles.NEG : Styles.GP));
        tiles.add(Styles.tile(xp, "XP/hr", Styles.XP));
        Styles.capHeight(tiles);
        card.add(tiles);
        card.add(Box.createVerticalStrut(6));

        JPanel grid = new JPanel(new GridLayout(0, 2, 0, 3));
        grid.setBackground(Styles.CARD);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        grid.add(Styles.keyLabel("Avg net / trip"));
        grid.add(signedValue(s.avgNetProfitPerTrip));
        grid.add(Styles.keyLabel("Avg XP / trip"));
        JLabel avgXp = Styles.valueLabel(Styles.XP);
        avgXp.setText(GpFormat.format(s.avgXpPerTrip));
        grid.add(avgXp);
        grid.add(Styles.keyLabel("Avg kills / trip"));
        JLabel avgKills = Styles.valueLabel(Styles.TEXT);
        avgKills.setText(String.format(java.util.Locale.US, "%.1f", s.avgKillsPerTrip));
        grid.add(avgKills);
        Styles.capHeight(grid);
        card.add(grid);

        return card;
    }

    private static JLabel signedValue(long value) {
        JLabel l = Styles.valueLabel(value < 0 ? Styles.NEG : Styles.GP);
        l.setText(GpFormat.format(value));
        return l;
    }
}
