package com.goodrunetracker.adapter;

import static org.junit.Assert.*;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class TrackingServiceDeathAndBankTest {

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
        int deathPrompts = 0;

        public void refresh() {
        }

        public void showDeathPrompt() {
            deathPrompts++;
        }
    }

    private final ItemPriceSource oneGp = id -> 1;

    private TrackingService newService(FakeClock clock, FakeCarried carried, FakePanel panel,
                                       SessionStore store) {
        PotionRegistry potions = new PotionRegistry();
        TripNamingConfig naming = new TripNamingConfig() {
            public boolean nameAfterFirstKill() {
                return true;
            }

            public boolean nameAfterFirstGather() {
                return true;
            }
        };
        return new TrackingService(clock, carried, id -> "Item " + id, potions,
                new LiveItemValuer(oneGp, potions), store, panel, "acct-A",
                java.util.HashMap::new, naming);
    }

    private void killOnce(TrackingService service, FakeCarried carried, FakeClock clock) {
        Map<Integer, Integer> drop = new HashMap<>();
        drop.put(560, 100);
        service.onKill("Demonic gorilla", drop);
        carried.carried.put(560, carried.carried.getOrDefault(560, 0) + 100);
        service.markCarriedDirty();
        clock.now += 10_000;
        service.onTick();
    }

    @Test
    public void bankOpeningRollsTripOverAndKeepsTracking() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"));
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession();
        killOnce(service, carried, clock);
        service.onBankOpened(true);

        assertTrue(service.isTracking());
        assertEquals(2, service.currentSnapshot().get().tripNumber);
        service.endSession();
        assertEquals(1, store.load("acct-A").get(0).trips.size());
    }

    @Test
    public void discardTripDropsItButKeepsSession() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"));
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession();
        killOnce(service, carried, clock);
        service.discardTrip();

        assertTrue(service.isTracking());
        assertEquals(1, service.currentSnapshot().get().tripNumber);
        service.endSession();
        assertTrue(store.load("acct-A").isEmpty());
    }

    @Test
    public void deathPromptsAndPausesTickFeeding() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"));
        FakePanel panel = new FakePanel();
        TrackingService service = newService(clock, carried, panel, store);

        service.startSession();
        killOnce(service, carried, clock);

        service.onLocalPlayerDeath();
        assertEquals(1, panel.deathPrompts);

        carried.carried.clear();
        service.markCarriedDirty();
        clock.now += 10_000;
        service.onTick();
        assertEquals(0, service.currentSnapshot().get().suppliesGp);

        service.resolveDeath(true);
        service.endSession();
        StoredTrip saved = store.load("acct-A").get(0).trips.get(0);
        assertTrue(saved.died);
        assertTrue(saved.suppliesUsed.isEmpty());
    }

    @Test
    public void deathDiscardThrowsAwayTheDeadTrip() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"));
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession();
        killOnce(service, carried, clock);
        service.onLocalPlayerDeath();
        service.resolveDeath(false);
        assertTrue(service.isTracking());
        service.endSession();
        assertTrue(store.load("acct-A").isEmpty());
    }

    @Test
    public void unresolvedDeathAtSessionEndDoesNotStickIntoNextSession() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"));
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession();
        killOnce(service, carried, clock);
        service.onLocalPlayerDeath();   // prompt open, awaitingDeathChoice = true
        service.endSession();           // session ends WITHOUT resolveDeath

        // The next session must track normally despite the unresolved death flag.
        service.startSession();
        Map<Integer, Integer> drop = new HashMap<>();
        drop.put(560, 50);
        service.onKill("Vorkath", drop);
        carried.carried.put(560, carried.carried.getOrDefault(560, 0) + 50);
        service.markCarriedDirty();
        clock.now += 10_000;
        service.onTick();
        assertEquals(1, service.currentSnapshot().get().kills); // proves onKill/onTick are live
    }

    @Test
    public void unresolvedDeathAtSessionEndDiscardsTheDeadTrip() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"));
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession();
        killOnce(service, carried, clock);
        service.onLocalPlayerDeath();   // unconfirmed death
        service.endSession();           // ends before resolveDeath

        // The unconfirmed dead trip must be dropped, not persisted as "kept".
        assertTrue(store.load("acct-A").isEmpty());
    }

    @Test
    public void twoNonEmptyTripsPersistInOneSession() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"));
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession();
        killOnce(service, carried, clock);   // trip 1: kill + pickup
        service.onBankOpened(true);              // persists trip 1, starts trip 2
        service.onBankClosed();              // leave the bank before the next kill
        killOnce(service, carried, clock);   // trip 2: kill + pickup
        service.endSession();                // persists trip 2

        StoredSession s = store.load("acct-A").get(0);
        assertEquals(2, s.trips.size());
        // session end time tracks the last trip's end
        assertEquals(s.trips.get(1).endMillis, s.endMillis);
    }
}
