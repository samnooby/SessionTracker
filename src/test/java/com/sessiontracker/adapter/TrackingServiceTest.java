package com.sessiontracker.adapter;

import static org.junit.Assert.*;
import com.sessiontracker.core.Trip;
import com.sessiontracker.core.item.ItemKey;
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

    static final class FakeXp implements CurrentXpSupplier {
        final Map<String, Long> xp = new HashMap<>();

        public Map<String, Long> currentXp() {
            return new HashMap<>(xp);
        }
    }

    private static TripNamingConfig naming(boolean kill, boolean gather) {
        return new TripNamingConfig() {
            public boolean nameAfterFirstKill() {
                return kill;
            }

            public boolean nameAfterFirstGather() {
                return gather;
            }
        };
    }

    private TrackingService newService(FakeClock clock, FakeCarried carried, FakePanel panel,
                                       SessionStore store) {
        return newService(clock, carried, panel, store, new FakeXp(), naming(true, true));
    }

    private TrackingService newService(FakeClock clock, FakeCarried carried, FakePanel panel,
                                       SessionStore store, CurrentXpSupplier currentXp) {
        return newService(clock, carried, panel, store, currentXp, naming(true, true));
    }

    private TrackingService newService(FakeClock clock, FakeCarried carried, FakePanel panel,
                                       SessionStore store, TripNamingConfig naming) {
        return newService(clock, carried, panel, store, new FakeXp(), naming);
    }

    private TrackingService newService(FakeClock clock, FakeCarried carried, FakePanel panel,
                                       SessionStore store, CurrentXpSupplier currentXp,
                                       TripNamingConfig naming) {
        PotionRegistry potions = new PotionRegistry();
        LiveItemValuer valuer = new LiveItemValuer(oneGp, potions);
        return new TrackingService(clock, carried, id -> "Item " + id, potions, valuer, store,
                panel, "acct-A", currentXp, naming);
    }

    @Test
    public void startSessionStartsATripAndBaselinesInventory() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        carried.carried.put(560, 50);
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"), new com.google.gson.Gson());
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
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"), new com.google.gson.Gson());
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
        SessionStore store = new SessionStore(root, new com.google.gson.Gson());
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

    private String savedCategory(SessionStore store) {
        return store.load("acct-A").get(0).category;
    }

    @Test
    public void firstGatheredItemNamesTheCategoryWhenGatherNamingEnabled() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"), new com.google.gson.Gson());
        TrackingService service = newService(clock, carried, new FakePanel(), store, naming(true, true));

        service.startSession();          // baseline empty inventory
        carried.carried.put(1521, 1);    // chop an oak log (no kill)
        service.markCarriedDirty();
        clock.now = 10_000;
        service.onTick();
        service.endSession();

        assertEquals("Item 1521", savedCategory(store));
    }

    @Test
    public void killBeforeGatherWinsWhenBothEnabled() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"), new com.google.gson.Gson());
        TrackingService service = newService(clock, carried, new FakePanel(), store, naming(true, true));

        service.startSession();
        service.onKill("Goblin", new HashMap<>());   // kill first
        carried.carried.put(1521, 1);                // then gather
        service.markCarriedDirty();
        clock.now = 10_000;
        service.onTick();
        service.endSession();

        assertEquals("Goblin", savedCategory(store));
    }

    @Test
    public void killDoesNotNameWhenOnlyGatherNamingEnabled() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"), new com.google.gson.Gson());
        TrackingService service = newService(clock, carried, new FakePanel(), store, naming(false, true));

        service.startSession();
        service.onKill("Goblin", new HashMap<>());   // kill naming OFF -> ignored
        carried.carried.put(377, 1);                 // gather a raw fish -> names it
        service.markCarriedDirty();
        clock.now = 10_000;
        service.onTick();
        service.endSession();

        assertEquals("Item 377", savedCategory(store));
    }

    @Test
    public void noNamingSettingsLeavesCategoryUncategorized() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"), new com.google.gson.Gson());
        TrackingService service = newService(clock, carried, new FakePanel(), store, naming(false, false));

        service.startSession();
        service.onKill("Goblin", new HashMap<>());
        carried.carried.put(1521, 1);
        service.markCarriedDirty();
        clock.now = 10_000;
        service.onTick();
        service.endSession();

        assertEquals("Uncategorized", savedCategory(store));
    }

    @Test
    public void depositingGatheredItemsAtBankIsNotASupply() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"), new com.google.gson.Gson());
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession();
        carried.carried.put(1521, 28);          // chopped 28 oak logs
        service.markCarriedDirty();
        clock.now = 10_000;
        service.onTick();

        service.onBankOpened(false);            // bank opens, trip continues
        carried.carried.remove(1521);           // deposit all 28
        service.markCarriedDirty();
        clock.now = 20_000;
        service.onTick();
        service.onBankClosed();

        TripSnapshot snap = service.currentSnapshot().get();
        assertEquals(0, snap.suppliesGp);        // deposit is not a supply
        assertEquals(28, snap.gatheredGp);       // the gathered logs still count as profit
    }

    @Test
    public void withdrawingItemsAtBankIsNotCountedAsGathered() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"), new com.google.gson.Gson());
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession();
        service.onBankOpened(false);
        carried.carried.put(385, 3);            // withdraw 3 sharks
        service.markCarriedDirty();
        clock.now = 10_000;
        service.onTick();
        service.onBankClosed();

        carried.carried.put(385, 1);            // ate 2 after leaving the bank
        service.markCarriedDirty();
        clock.now = 20_000;
        service.onTick();

        TripSnapshot snap = service.currentSnapshot().get();
        assertEquals(0, snap.gatheredGp);        // withdrawal isn't a gain
        assertEquals(2, snap.suppliesGp);        // brought food eaten is a supply
    }

    @Test
    public void depositInPostBankTripIsNotPersistedAsSupplyWhenEndTripEnabled() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"), new com.google.gson.Gson());
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession();
        carried.carried.put(1521, 28);          // gather 28 logs
        service.markCarriedDirty();
        clock.now = 10_000;
        service.onTick();

        service.onBankOpened(true);             // ends gather trip, starts a new one
        carried.carried.remove(1521);           // deposit in the new trip
        service.markCarriedDirty();
        clock.now = 20_000;
        service.onTick();
        service.onBankClosed();
        service.endSession();

        List<StoredSession> saved = store.load("acct-A");
        assertEquals(1, saved.get(0).trips.size());                 // only the gather run persists
        Trip t = SessionMapper.toTrip(saved.get(0).trips.get(0));
        assertEquals(Integer.valueOf(28), t.gathered().get(ItemKey.item(1521)));
        assertTrue(t.suppliesUsed().isEmpty());                     // the deposit never booked a supply
    }

    @Test
    public void emptyTripIsNotPersisted() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        Path root = Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root, new com.google.gson.Gson());
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
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"), new com.google.gson.Gson());
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession();
        service.onXp("RANGED", 100_000);
        service.onXp("RANGED", 100_500);
        service.onXp("RANGED", 100_800);
        assertEquals(800, service.currentSnapshot().get().totalXp);
    }

    @Test
    public void firstXpGainOfEachSkillIsCounted() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"), new com.google.gson.Gson());
        FakeXp xp = new FakeXp();
        xp.xp.put("Attack", 1000L);      // existing totals before the session starts
        xp.xp.put("Woodcutting", 5000L);
        TrackingService service = newService(clock, carried, new FakePanel(), store, xp);

        service.startSession();          // primes baselines for every skill
        service.onXp("Attack", 1004);    // first hit: +4 (previously lost)
        service.onXp("Woodcutting", 5025); // first log later in the trip: +25 (previously lost)

        assertEquals(29, service.currentSnapshot().get().totalXp);
    }

    @Test
    public void xpBaselineResetsBetweenSessions() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"), new com.google.gson.Gson());
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession();
        service.onXp("RANGED", 100_000);
        service.onXp("RANGED", 100_500);
        service.endSession();

        service.startSession();
        service.onXp("RANGED", 200_000); // must RE-PRIME, not count the 99_500 gap
        assertEquals(0, service.currentSnapshot().get().totalXp);
    }

    @Test
    public void droppingALootedItemMovesItToMissedNotSupplies() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"), new com.google.gson.Gson());
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession();
        Map<Integer, Integer> drop = new HashMap<>();
        drop.put(560, 100);
        service.onKill("Demonic gorilla", drop);
        carried.carried.put(560, 100);
        service.markCarriedDirty();
        clock.now = 10_000;
        service.onTick();

        service.markDropped(560);
        carried.carried.remove(560);
        service.markCarriedDirty();
        clock.now = 20_000;
        service.onTick();

        TripSnapshot snap = service.currentSnapshot().get();
        assertEquals(0, snap.pickedGp);
        assertEquals(100, snap.groundGp);
        assertEquals(0, snap.suppliesGp);
    }

    @Test
    public void consumingALootedItemIsNotChargedAsSupply() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"), new com.google.gson.Gson());
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession();
        Map<Integer, Integer> drop = new HashMap<>();
        drop.put(385, 4);
        service.onKill("x", drop);
        carried.carried.put(385, 4);
        service.markCarriedDirty();
        clock.now = 10_000;
        service.onTick();

        carried.carried.put(385, 1);   // eat 3, NO markDropped -> looted, not a supply
        service.markCarriedDirty();
        clock.now = 20_000;
        service.onTick();

        TripSnapshot snap = service.currentSnapshot().get();
        assertEquals(1, snap.pickedGp);                  // only the 1 still held shows as picked up
        assertEquals(3, snap.consumedLootGp);            // the 3 eaten show as used loot
        assertEquals(0, snap.suppliesGp);                // eaten loot is not a supply
        assertEquals(1L, service.currentSessionSnapshot().get().netProfit); // kept 1 of 4

        service.endSession();
        StoredSession saved = store.load("acct-A").get(0);
        Trip trip = SessionMapper.toTrip(saved.trips.get(0));
        assertEquals(Integer.valueOf(3), trip.consumedLoot().get(ItemKey.item(385)));
        assertTrue(trip.suppliesUsed().isEmpty());
    }

    @Test
    public void reloadedTripValuesIdenticallyToWhenRecorded() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        java.nio.file.Path root = Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root, new com.google.gson.Gson());
        ItemPriceSource prices = id -> id == 560 ? 5 : 1; // item 560 worth 5gp each
        PotionRegistry potions = new PotionRegistry();
        LiveItemValuer live = new LiveItemValuer(prices, potions);
        TrackingService service = new TrackingService(clock, carried, id -> "Item " + id,
                potions, live, store, new FakePanel(), "acct-A", new FakeXp(), naming(true, true));

        service.startSession();
        Map<Integer, Integer> drop = new HashMap<>();
        drop.put(560, 100);
        service.onKill("Demonic gorilla", drop);
        carried.carried.put(560, 100); // pick up all 100
        service.markCarriedDirty();
        clock.now = 60_000;
        service.onTick();
        TripSnapshot snap = service.currentSnapshot().get();
        long liveNet = snap.pickedGp - snap.suppliesGp; // 100 * 5 = 500, no supplies
        service.endSession();

        StoredSession reloaded = store.load("acct-A").get(0);
        StoredTrip storedTrip = reloaded.trips.get(0);
        Trip trip = SessionMapper.toTrip(storedTrip);
        FrozenItemValuer frozen = new FrozenItemValuer(SessionMapper.unitPrices(storedTrip));
        assertEquals(liveNet, trip.netProfit(frozen));
        assertEquals(500, liveNet);
    }

    @Test
    public void droppingABroughtItemThenPickingItUpClearsTheSupplyCharge() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        carried.carried.put(1265, 1);          // bring a pickaxe
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"), new com.google.gson.Gson());
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession();                 // baseline includes the pickaxe

        service.markDropped(1265);              // drop it
        carried.carried.remove(1265);
        service.markCarriedDirty();
        clock.now = 10_000;
        service.onTick();                       // charged as a supply

        carried.carried.put(1265, 1);           // pick it back up
        service.markCarriedDirty();
        clock.now = 20_000;
        service.onTick();

        TripSnapshot snap = service.currentSnapshot().get();
        assertEquals(0, snap.suppliesGp);       // supply charge reversed
        assertEquals(0, snap.pickedGp);         // not counted as loot either
    }

    @Test
    public void sessionSnapshotRollsUpPersistedAndCurrentTrips() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"), new com.google.gson.Gson());
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession();
        Map<Integer, Integer> drop = new HashMap<>();
        drop.put(560, 100);
        service.onKill("Vorkath", drop);
        carried.carried.put(560, 100);
        service.markCarriedDirty();
        clock.now = 1_800_000L;
        service.onTick();
        service.onBankOpened(true);
        service.onBankClosed();          // leave the bank before the next kill

        service.onKill("Vorkath", drop);
        carried.carried.put(560, 150);
        service.markCarriedDirty();
        clock.now = 3_600_000L;
        service.onTick();

        SessionSnapshot snap = service.currentSessionSnapshot().get();
        assertEquals(2, snap.tripCount);
        assertEquals(150L, snap.netProfit);
        assertEquals(150L, snap.gpPerHour);
    }

    @Test
    public void snapshotXpIsEmptyBeforeAnyXp() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"), new com.google.gson.Gson());
        TrackingService service = newService(clock, carried, new FakePanel(), store);
        service.startSession();
        assertTrue(service.currentSnapshot().get().xpBySkill.isEmpty());
    }

    @Test
    public void snapshotListsXpPerSkillAlphabetically() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"), new com.google.gson.Gson());
        TrackingService service = newService(clock, carried, new FakePanel(), store);
        service.startSession();
        service.onXp("Ranged", 100_000); // primes
        service.onXp("Attack", 50_000);  // primes
        service.onXp("Ranged", 100_300); // +300
        service.onXp("Attack", 50_200);  // +200

        TripSnapshot snap = service.currentSnapshot().get();
        assertEquals(2, snap.xpBySkill.size());
        assertEquals("Attack", snap.xpBySkill.get(0).skill);
        assertEquals(200L, snap.xpBySkill.get(0).xp);
        assertEquals("Ranged", snap.xpBySkill.get(1).skill);
        assertEquals(300L, snap.xpBySkill.get(1).xp);
        assertEquals(500L, snap.totalXp);
    }

    @Test
    public void snapshotKillsByNpcIsEmptyBeforeAnyKill() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"), new com.google.gson.Gson());
        TrackingService service = newService(clock, carried, new FakePanel(), store);
        service.startSession();
        assertTrue(service.currentSnapshot().get().killsByNpc.isEmpty());
    }

    @Test
    public void snapshotListsKillsByNpcMostKilledFirst() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"), new com.google.gson.Gson());
        TrackingService service = newService(clock, carried, new FakePanel(), store);
        service.startSession();
        Map<Integer, Integer> noDrop = new HashMap<>();
        service.onKill("Goblin", noDrop);
        service.onKill("Bird", noDrop);
        service.onKill("Goblin", noDrop);
        service.onKill("Goblin", noDrop);

        TripSnapshot snap = service.currentSnapshot().get();
        assertEquals(2, snap.killsByNpc.size());
        assertEquals("Goblin", snap.killsByNpc.get(0).npc);
        assertEquals(3, snap.killsByNpc.get(0).count);
        assertEquals("Bird", snap.killsByNpc.get(1).npc);
        assertEquals(1, snap.killsByNpc.get(1).count);
        assertEquals(4, snap.kills);
    }

    @Test
    public void stashingRunesIntoThePouchIsNotASupply() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        carried.carried.put(556, 100); // 100 air runes carried (inventory or pouch -- combined)
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"), new com.google.gson.Gson());
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession(); // baseline: 556 -> 100

        // Stash all 100 runes into the pouch: combined total is unchanged.
        carried.carried.put(556, 100);
        service.markCarriedDirty();
        clock.now = 10_000;
        service.onTick();

        assertEquals(0, service.currentSnapshot().get().suppliesGp);
    }

    @Test
    public void castingRunesFromThePouchIsASupply() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        carried.carried.put(556, 100);
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"), new com.google.gson.Gson());
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession(); // baseline: 556 -> 100

        // Cast 3 runes from the pouch: combined total drops by 3, no inventory event needed.
        carried.carried.put(556, 97);
        service.markCarriedDirty();
        clock.now = 10_000;
        service.onTick();

        assertEquals(3, service.currentSnapshot().get().suppliesGp); // oneGp valuer -> 3 runes = 3
    }

    @Test
    public void chargingAScaleWeaponIsNotASupply() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        carried.carried.put(12934, 1000); // 1000 Zulrah's scales (inventory or weapon -- combined)
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"), new com.google.gson.Gson());
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession(); // baseline: 12934 -> 1000

        carried.carried.put(12934, 1000); // charge: scales move inventory -> weapon, combined unchanged
        service.markCarriedDirty();
        clock.now = 10_000;
        service.onTick();

        assertEquals(0, service.currentSnapshot().get().suppliesGp);
    }

    @Test
    public void shootingAScaleWeaponBooksScalesAsSupply() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        carried.carried.put(12934, 1000);
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"), new com.google.gson.Gson());
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession(); // baseline: 12934 -> 1000

        carried.carried.put(12934, 700); // shot 300 scales from the weapon, no inventory event
        service.markCarriedDirty();
        clock.now = 10_000;
        service.onTick();

        assertEquals(300, service.currentSnapshot().get().suppliesGp); // oneGp valuer -> 300
    }

    @Test
    public void gatheredResourcesAppearInSnapshotAndPersist() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        Path root = Files.createTempDirectory("grt");
        SessionStore store = new SessionStore(root, new com.google.gson.Gson());
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession();                 // baseline empty
        carried.carried.put(1511, 27);          // chopped 27 logs, no kill
        service.markCarriedDirty();
        clock.now = 30_000;
        service.onTick();

        assertEquals(27, service.currentSnapshot().get().gatheredGp); // oneGp valuer
        service.endSession();

        StoredSession saved = store.load("acct-A").get(0);
        Trip trip = SessionMapper.toTrip(saved.trips.get(0));
        assertEquals(Integer.valueOf(27), trip.gathered().get(ItemKey.item(1511)));
    }

    @Test
    public void gatheredAddsToNetProfit() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"), new com.google.gson.Gson());
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession();
        carried.carried.put(1511, 100);         // gathered 100 logs (gross, no supplies)
        service.markCarriedDirty();
        clock.now = 3_600_000L;                 // 1 hour
        service.onTick();
        service.onBankOpened(true);                 // ends the trip, persisting gathered=100

        SessionSnapshot snap = service.currentSessionSnapshot().get();
        assertEquals(100L, snap.netProfit);     // 0 picked + 100 gathered - 0 supplies
        assertEquals(100L, snap.gatheredGp);
    }

    @Test
    public void renameAndRecategorizeActiveSessionPersistAfterTripEnds() throws Exception {
        FakeClock clock = new FakeClock();
        FakeCarried carried = new FakeCarried();
        SessionStore store = new SessionStore(Files.createTempDirectory("grt"), new com.google.gson.Gson());
        TrackingService service = newService(clock, carried, new FakePanel(), store);

        service.startSession();
        Map<Integer, Integer> drop = new HashMap<>();
        drop.put(560, 100);
        service.onKill("Vorkath", drop);
        carried.carried.put(560, 100);
        service.markCarriedDirty();
        clock.now = 60_000;
        service.onTick();
        service.onBankOpened(true);

        service.renameActiveSession("my grind");
        service.recategorizeActiveSession("Bossing");

        SessionHistory history = new SessionHistory(store, "acct-A", id -> "Item " + id);
        SessionHistory.SessionSummary summary = history.sessionsNewestFirst().get(0);
        assertEquals("my grind", summary.name);
        assertEquals("Bossing", summary.category);
    }
}
