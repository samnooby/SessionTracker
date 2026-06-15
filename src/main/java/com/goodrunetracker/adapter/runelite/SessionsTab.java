package com.goodrunetracker.adapter.runelite;

import com.goodrunetracker.adapter.GpFormat;
import com.goodrunetracker.adapter.SessionHistory;
import com.goodrunetracker.adapter.TrackingService;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
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
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel();
    private final JPanel listBody = new JPanel();
    private final JPanel detailBody = new JPanel();

    private TrackingService service;
    private SessionHistory history;
    private String expandedSessionId;
    private String editingSessionId;

    SessionsTab(ClientThread clientThread) {
        this.clientThread = clientThread;
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

    private void showDetail(String sessionId, String tripId) {
        clientThread.invoke(() -> {
            SessionHistory.TripDetail d = history.tripDetail(sessionId, tripId);
            SwingUtilities.invokeLater(() -> renderDetail(d));
        });
    }

    private void renderDetail(SessionHistory.TripDetail d) {
        detailBody.removeAll();

        JButton back = Styles.linkButton("Back", Styles.ORANGE);
        back.setIcon(Styles.caret(Styles.CARET_LEFT, Styles.ORANGE));
        back.setIconTextGap(5);
        back.setAlignmentX(Component.LEFT_ALIGNMENT);
        back.addActionListener(e -> cards.show(root, LIST));
        detailBody.add(back);
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

            addGroup("Picked up", d.pickedUp, Styles.GP);
            addGroup("Left on ground", d.leftOnGround, Styles.MISSED);
            addGroup("Supplies used", d.suppliesUsed, Styles.NEG);
        }
        detailBody.revalidate();
        detailBody.repaint();
        cards.show(root, DETAIL);
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
                grid.add(Styles.keyLabel(l.label + " ×" + l.quantity));
                JLabel v = Styles.valueLabel(valueColor);
                v.setText(GpFormat.format(l.gpValue));
                grid.add(v);
            }
            Styles.capHeight(grid);
            card.add(grid);
        }
        detailBody.add(card);
    }

    private static JLabel signedValue(long value) {
        JLabel l = Styles.valueLabel(value < 0 ? Styles.NEG : Styles.GP);
        l.setText(GpFormat.format(value));
        return l;
    }
}
