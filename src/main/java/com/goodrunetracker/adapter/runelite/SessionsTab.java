package com.goodrunetracker.adapter.runelite;

import com.goodrunetracker.adapter.GpFormat;
import com.goodrunetracker.adapter.SessionHistory;
import com.goodrunetracker.adapter.TrackingService;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import net.runelite.client.callback.ClientThread;

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
        setLayout(new BorderLayout());
        root.setLayout(cards);
        listBody.setLayout(new BoxLayout(listBody, BoxLayout.Y_AXIS));
        detailBody.setLayout(new BoxLayout(detailBody, BoxLayout.Y_AXIS));
        root.add(wrapNorth(listBody), LIST);
        root.add(wrapNorth(detailBody), DETAIL);
        add(root, BorderLayout.CENTER);
        cards.show(root, LIST);
    }

    private static JPanel wrapNorth(JPanel inner) {
        JPanel p = new JPanel(new BorderLayout());
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
            listBody.add(new JLabel("Log in to view sessions"));
        } else {
            List<SessionHistory.SessionSummary> sessions = history.sessionsNewestFirst();
            if (sessions.isEmpty()) {
                listBody.add(new JLabel("No sessions yet"));
            }
            for (SessionHistory.SessionSummary s : sessions) {
                listBody.add(sessionRow(s));
                if (s.sessionId.equals(editingSessionId)) {
                    listBody.add(editForm(s));
                } else if (s.sessionId.equals(expandedSessionId)) {
                    for (SessionHistory.TripSummary t : history.tripsFor(s.sessionId)) {
                        listBody.add(tripRow(s.sessionId, t));
                    }
                }
            }
        }
        listBody.revalidate();
        listBody.repaint();
    }

    private JPanel sessionRow(SessionHistory.SessionSummary s) {
        JPanel row = new JPanel(new BorderLayout());
        String label = (s.name == null || s.name.isEmpty() ? s.category : s.name)
                + "  " + s.tripCount + " trips · " + GpFormat.format(s.netProfit);
        JButton header = new JButton((s.sessionId.equals(expandedSessionId) ? "▾ " : "▸ ") + label);
        header.addActionListener(e -> {
            expandedSessionId = s.sessionId.equals(expandedSessionId) ? null : s.sessionId;
            editingSessionId = null;
            renderList();
        });
        JButton pencil = new JButton("✎");
        pencil.addActionListener(e -> {
            editingSessionId = s.sessionId.equals(editingSessionId) ? null : s.sessionId;
            expandedSessionId = s.sessionId;
            renderList();
        });
        row.add(header, BorderLayout.CENTER);
        row.add(pencil, BorderLayout.EAST);
        return row;
    }

    private JButton tripRow(String sessionId, SessionHistory.TripSummary t) {
        JButton b = new JButton("    Trip · " + t.kills + " kills · " + GpFormat.format(t.netProfit) + " ›");
        b.addActionListener(e -> showDetail(sessionId, t.tripId));
        return b;
    }

    private JPanel editForm(SessionHistory.SessionSummary s) {
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        JTextField name = new JTextField(s.name == null ? "" : s.name);
        JComboBox<String> category = new JComboBox<>(history.categories().toArray(new String[0]));
        category.setEditable(true);
        category.setSelectedItem(s.category);
        form.add(new JLabel("Name"));
        form.add(name);
        form.add(new JLabel("Category"));
        form.add(category);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton save = new JButton("Save");
        JButton cancel = new JButton("Cancel");
        save.addActionListener(e -> {
            String newName = name.getText();
            Object cat = category.getSelectedItem();
            String newCat = cat == null ? s.category : cat.toString();
            applyEdit(s.sessionId, newName, newCat);
            editingSessionId = null;
            renderList();
        });
        cancel.addActionListener(e -> { editingSessionId = null; renderList(); });
        buttons.add(save);
        buttons.add(cancel);
        form.add(buttons);
        return form;
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
        SessionHistory.TripDetail d = history.tripDetail(sessionId, tripId);
        detailBody.removeAll();
        JButton back = new JButton("‹ Back");
        back.addActionListener(e -> cards.show(root, LIST));
        detailBody.add(back);
        if (d != null) {
            detailBody.add(new JLabel("Net " + GpFormat.format(d.netProfit)
                    + " · Missed " + GpFormat.format(d.missedValue)));
            addGroup("Picked up", d.pickedUp);
            addGroup("Left on ground", d.leftOnGround);
            addGroup("Supplies used", d.suppliesUsed);
        }
        detailBody.revalidate();
        detailBody.repaint();
        cards.show(root, DETAIL);
    }

    private void addGroup(String title, List<SessionHistory.ItemLine> lines) {
        detailBody.add(new JLabel(title));
        if (lines.isEmpty()) {
            JLabel none = new JLabel("  —");
            detailBody.add(none);
            return;
        }
        JPanel grid = new JPanel(new GridLayout(0, 2));
        for (SessionHistory.ItemLine l : lines) {
            grid.add(new JLabel(l.label + " ×" + l.quantity));
            grid.add(new JLabel(GpFormat.format(l.gpValue)));
        }
        detailBody.add(grid);
    }
}
