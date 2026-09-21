package com.sessiontracker.adapter.runelite;

import static com.sessiontracker.adapter.runelite.Fixtures.COINS;
import static com.sessiontracker.adapter.runelite.Fixtures.MINUTE;
import static com.sessiontracker.adapter.runelite.Fixtures.OAK_LOGS;
import static com.sessiontracker.adapter.runelite.Fixtures.SHARK;
import static com.sessiontracker.adapter.runelite.Fixtures.session;
import static com.sessiontracker.adapter.runelite.Fixtures.trip;
import static com.sessiontracker.adapter.runelite.Swing.assertHasText;
import static com.sessiontracker.adapter.runelite.Swing.assertNoText;
import static com.sessiontracker.adapter.runelite.Swing.button;
import static com.sessiontracker.adapter.runelite.Swing.click;
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
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/** Renders the Stats tab headless over a seeded store: category cards and the drill-in detail. */
public class StatsTabTest {

    private static final long START = 1_700_000_000_000L;

    private SessionHistory history;
    private StatsTab tab;

    @Before
    public void setUp() throws Exception {
        SessionStore store = new SessionStore(Files.createTempDirectory("stats-tab-test"), new Gson());
        history = new SessionHistory(store, "42", Fixtures::itemName);
        tab = new StatsTab(Swing.inlineClientThread(), new RecordingIcons(false));

        // Vorkath: 150K over one hour of wall clock, 2 sharks per trip. Oak logs: 2K over 30 minutes.
        store.save(session("vork", "Vorkath", "", START,
                trip("v1", START, START + 30 * MINUTE).kills("Vorkath", 5)
                        .pickedUp(COINS, 100_000, 1).supplies(SHARK, 2, 500).xp("Ranged", 12_000).build(),
                trip("v2", START + 30 * MINUTE, START + 60 * MINUTE).kills("Vorkath", 4)
                        .pickedUp(COINS, 50_000, 1).supplies(SHARK, 2, 500).xp("Ranged", 9_000).build()));
        store.save(session("oaks", "Oak logs", "", START - 24 * 60 * MINUTE,
                trip("o1", START - 24 * 60 * MINUTE, START - 24 * 60 * MINUTE + 30 * MINUTE)
                        .gathered(OAK_LOGS, 100, 20).xp("Woodcutting", 3_750).build()));
    }

    private void show() throws Exception {
        onEdt(() -> tab.setHistory(history));
        flushEdt();
    }

    @Test
    public void showsOneCardPerCategoryOrderedByGpPerHour() throws Exception {
        show();

        List<String> visible = texts(tab);
        int vorkath = visible.indexOf("Vorkath");
        int oaks = visible.indexOf("Oak logs");
        assertTrue("Vorkath card missing: " + visible, vorkath >= 0);
        assertTrue("Oak logs card missing: " + visible, oaks >= 0);
        assertTrue("higher GP/hr category should come first: " + visible, vorkath < oaks);
        assertHasText(tab, "1 session · 2 trips");
        assertHasText(tab, "1 session · 1 trip");
        assertHasText(tab, "148.0K"); // (150,000 - 4 × 500) gp over one hour
        assertHasText(tab, "4.0K");   // 2,000 gp over half an hour
    }

    @Test
    public void averageIconsKeepTheDecimalSoTwoAndAHalfSharksIsVisible() throws Exception {
        RecordingIcons icons = new RecordingIcons(true);
        tab = new StatsTab(Swing.inlineClientThread(), icons);
        show();

        press(label(tab, "Vorkath"));
        flushEdt();

        assertEquals("2.0", icons.quantityOn(SHARK));
        assertEquals("75000", icons.quantityOn(COINS));
    }

    @Test
    public void openingACategoryShowsItsAveragesAndBackReturnsToTheCards() throws Exception {
        show();

        press(label(tab, "Vorkath"));
        flushEdt();

        assertHasText(tab, "PER HOUR"); // section headers render uppercased
        assertHasText(tab, "PER-TRIP AVERAGES");
        assertHasText(tab, "74.0K");   // avg net per trip: (150,000 - 2,000) / 2
        assertHasText(tab, "Trip length");
        assertHasText(tab, "30m");
        assertHasText(tab, "PER SESSION");
        assertHasText(tab, "Avg session length");
        assertHasText(tab, "1h 0m");
        assertHasText(tab, "XP AVERAGES");
        assertHasText(tab, "Ranged");
        assertHasText(tab, "KILL AVERAGES");
        assertNoText(tab, "Oak logs");

        click(button(tab, "Back"));
        flushEdt();
        assertHasText(tab, "Oak logs");
    }
}
