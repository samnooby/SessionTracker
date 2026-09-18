package com.sessiontracker.adapter.runelite;

import static com.sessiontracker.adapter.runelite.Fixtures.COINS;
import static com.sessiontracker.adapter.runelite.Fixtures.MINUTE;
import static com.sessiontracker.adapter.runelite.Fixtures.OAK_LOGS;
import static com.sessiontracker.adapter.runelite.Fixtures.session;
import static com.sessiontracker.adapter.runelite.Fixtures.trip;
import static com.sessiontracker.adapter.runelite.Swing.assertHasText;
import static com.sessiontracker.adapter.runelite.Swing.assertNoText;
import static com.sessiontracker.adapter.runelite.Swing.button;
import static com.sessiontracker.adapter.runelite.Swing.click;
import static com.sessiontracker.adapter.runelite.Swing.findAll;
import static com.sessiontracker.adapter.runelite.Swing.flushEdt;
import static com.sessiontracker.adapter.runelite.Swing.label;
import static com.sessiontracker.adapter.runelite.Swing.onEdt;
import static com.sessiontracker.adapter.runelite.Swing.press;
import static com.sessiontracker.adapter.runelite.Swing.texts;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.sessiontracker.adapter.SessionHistory;
import com.sessiontracker.adapter.SessionStore;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import javax.swing.JTextField;
import org.junit.Before;
import org.junit.Test;

/** Renders the Sessions tab headless over a seeded store and drives it like a user would. */
public class SessionsTabTest {

    private static final long START = 1_700_000_000_000L;

    private SessionStore store;
    private SessionHistory history;
    private SessionsTab tab;

    @Before
    public void setUp() throws Exception {
        store = new SessionStore(Files.createTempDirectory("sessions-tab-test"), new Gson());
        history = new SessionHistory(store, "42", Fixtures::itemName);
        tab = new SessionsTab(Swing.inlineClientThread(), Collections.emptyMap(), (label, itemId) -> { });

        // Two 30-minute Vorkath trips an hour apart end to end; a shorter, older woodcutting session.
        store.save(session("vork", "Vorkath", "", START,
                trip("v1", START, START + 30 * MINUTE).kills("Vorkath", 5)
                        .pickedUp(COINS, 100_000, 1).xp("Ranged", 12_000).build(),
                trip("v2", START + 30 * MINUTE, START + 60 * MINUTE).kills("Vorkath", 4)
                        .pickedUp(COINS, 50_000, 1).xp("Ranged", 9_000).build()));
        store.save(session("oaks", "Oak logs", "Evening chop", START - 24 * 60 * MINUTE,
                trip("o1", START - 24 * 60 * MINUTE, START - 24 * 60 * MINUTE + 30 * MINUTE)
                        .gathered(OAK_LOGS, 100, 20).xp("Woodcutting", 3_750).build()));
    }

    private void show() throws Exception {
        onEdt(() -> tab.setContext(null, history));
        flushEdt();
    }

    @Test
    public void listsSessionsNewestFirstWithTripsDurationAndNet() throws Exception {
        show();

        List<String> visible = texts(tab);
        int vorkath = visible.indexOf("Vorkath");
        int oaks = visible.indexOf("Evening chop"); // a named session shows its name, not its category
        assertTrue("Vorkath row missing: " + visible, vorkath >= 0);
        assertTrue("newest session should be listed first: " + visible, vorkath < oaks);
        assertHasText(tab, "2 trips · 1h 0m · ");
        assertHasText(tab, "150.0K");
        assertHasText(tab, "1 trip · 30m · ");
        assertHasText(tab, "2.0K");
    }

    @Test
    public void expandingASessionShowsItsSummaryAndTrips() throws Exception {
        show();
        assertNoText(tab, "Trip 1");

        press(label(tab, "Vorkath"));
        flushEdt();

        assertHasText(tab, "Avg net / trip");
        assertHasText(tab, "75.0K");
        assertHasText(tab, "Avg kills / trip");
        assertHasText(tab, "4.5");
        assertHasText(tab, "Trip 1 · 30m · 5 kills · ");
        assertHasText(tab, "Trip 2 · 30m · 4 kills · ");
        assertHasText(tab, "100.0K");
        assertHasText(tab, "50.0K");

        press(label(tab, "Vorkath")); // collapses again
        flushEdt();
        assertNoText(tab, "Trip 1");
    }

    @Test
    public void openingATripShowsItsDetailAndBackReturnsToTheList() throws Exception {
        show();
        press(label(tab, "Vorkath"));
        flushEdt();

        press(label(tab, "Trip 1 · 30m · 5 kills · "));
        flushEdt();

        assertHasText(tab, "Net profit");
        assertHasText(tab, "Duration");
        assertHasText(tab, "30m");
        assertHasText(tab, "Kills");
        assertHasText(tab, "Picked up");
        assertHasText(tab, "Coins ×100000");
        assertHasText(tab, "Ranged");
        assertHasText(tab, "12.0K");
        assertNoText(tab, "Evening chop"); // the list is hidden behind the detail card

        click(button(tab, "Back"));
        flushEdt();
        assertHasText(tab, "Evening chop");
    }

    @Test
    public void editingRenamesAndRecategorizesTheSession() throws Exception {
        show();

        click(findAll(tab, javax.swing.JButton.class, b -> "Edit".equals(b.getText())).get(0));
        flushEdt();
        List<JTextField> fields = findAll(tab, JTextField.class, f -> true);
        assertEquals("expected name and category fields", 2, fields.size());
        onEdt(() -> {
            fields.get(0).setText("Bird night");
            fields.get(1).setText("Vorkath (ranged)");
        });
        click(button(tab, "Save"));
        flushEdt();

        SessionHistory.SessionSummary edited = history.sessionsNewestFirst().get(0);
        assertEquals("Bird night", edited.name);
        assertEquals("Vorkath (ranged)", edited.category);
        assertHasText(tab, "Bird night");
    }
}
