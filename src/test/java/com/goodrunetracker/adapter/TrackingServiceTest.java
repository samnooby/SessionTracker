package com.goodrunetracker.adapter;

import static org.junit.Assert.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class TrackingServiceTest {

    static final class FakeClock implements Clock {
        long now = 0;
        int ids = 0;

        public long nowMillis() {
            return now;
        }

        public String newId() {
            return "id-" + (++ids);
        }
    }

    static final class FakeCarried implements CarriedSnapshotSupplier {
        Map<Integer, Integer> carried = new HashMap<>();

        public Map<Integer, Integer> currentCarried() {
            return new HashMap<>(carried);
        }
    }

    static final class FakePanel implements PanelView {
        int refreshes = 0;
        int deathPrompts = 0;

        public void refresh() {
            refreshes++;
        }

        public void showDeathPrompt() {
            deathPrompts++;
        }
    }

    private final ItemPriceSource oneGp = id -> 1;

    private TrackingService newService(FakeClock clock, FakeCarried carried, FakePanel panel,
                                       SessionStore store) {
        PotionRegistry potions = new PotionRegistry();
        LiveItemValuer valuer = new LiveItemValuer(oneGp, potions);
        return new TrackingService(clock, carried, id -> "Item " + id, potions, valuer, store,
                panel, "acct-A");
    }

    @Test
    public void startSessionStartsATripAndBaselinesInventory() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        carried.carried.put(560, 50);
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"));
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession();
        assertTrue(service.isTracking());
        assertTrue(service.currentSnapshot().isPresent());
        assertEquals(0, service.currentSnapshot().get().kills);
    }

    @Test
    public void killThenTickPickupIsTracked() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"));
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession();

        Map<Integer, Integer> drop = new HashMap<>();
        drop.put(560, 100);
        service.onKill("Demonic gorilla", drop);

        carried.carried.put(560, 60);
        service.markCarriedDirty();
        clock.now = 30_000;
        service.onTick();

        TripSnapshot snap = service.currentSnapshot().get();
        assertEquals(1, snap.kills);
        assertEquals(60, snap.pickedGp);
        assertEquals(40, snap.groundGp);
    }

    @Test
    public void endSessionPersistsAndDefaultsCategoryToFirstMonster() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        Path root = Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root);
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession();
        Map<Integer, Integer> drop = new HashMap<>();
        drop.put(560, 100);
        service.onKill("Demonic gorilla", drop);
        carried.carried.put(560, 100);
        service.markCarriedDirty();
        clock.now = 60_000;
        service.onTick();
        service.endSession();

        assertFalse(service.isTracking());
        List<StoredSession> saved = store.load("acct-A");
        assertEquals(1, saved.size());
        assertEquals("Demonic gorilla", saved.get(0).category);
        assertEquals(1, saved.get(0).trips.size());
        assertEquals(Integer.valueOf(100), saved.get(0).trips.get(0).pickedUp.get("item:560"));
    }

    @Test
    public void emptyTripIsNotPersisted() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        Path root = Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root);
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession();
        clock.now = 5_000;
        service.endSession();

        assertTrue(store.load("acct-A").isEmpty());
    }

    @Test
    public void xpFirstObservationPrimesBaselineThenCounts() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"));
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession();
        service.onXp("RANGED", 100_000);
        service.onXp("RANGED", 100_500);
        service.onXp("RANGED", 100_800);
        assertEquals(800, service.currentSnapshot().get().totalXp);
    }
}
