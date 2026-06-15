package com.goodrunetracker.adapter;

import com.goodrunetracker.core.Trip;
import com.goodrunetracker.core.TripLedger;
import com.goodrunetracker.core.item.CarriedNormalizer;
import com.goodrunetracker.core.item.Doses;
import com.goodrunetracker.core.item.ItemKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * Session/trip lifecycle state machine. RuneLite-bound collaborators are injected
 * as interfaces so this runs headless in tests. While a session is active there is
 * always exactly one active trip.
 */
public final class TrackingService {

    private static final long MILLIS_PER_HOUR = 3_600_000L;

    private final Clock clock;
    private final CarriedSnapshotSupplier carried;
    private final IntFunction<String> names;
    private final PotionRegistry potions;
    private final LiveItemValuer valuer;
    private final SessionStore store;
    private final PanelView panel;
    private final String accountHash;

    // Invariant: ledger != null implies activeSession != null (a trip only runs inside a session).
    private StoredSession activeSession;
    private TripLedger ledger;
    private String tripId;
    private long tripStartMillis;
    private boolean tripDied;
    private boolean inventoryDirty;
    private boolean awaitingDeathChoice;
    private final Map<String, Long> lastXp = new HashMap<>();
    private final java.util.Set<ItemKey> droppedThisTick = new java.util.HashSet<>();

    // Snapshot valuation calls RuneLite's ItemManager, which must run on the client
    // thread. We compute it here (always invoked on the client thread) and cache a
    // plain value object the Swing panel can read from the EDT without touching the client.
    private TripSnapshot cachedSnapshot;
    private SessionSnapshot cachedSessionSnapshot;

    public TrackingService(Clock clock, CarriedSnapshotSupplier carried, IntFunction<String> names,
                           PotionRegistry potions, LiveItemValuer valuer, SessionStore store,
                           PanelView panel, String accountHash) {
        this.clock = clock;
        this.carried = carried;
        this.names = names;
        this.potions = potions;
        this.valuer = valuer;
        this.store = store;
        this.panel = panel;
        this.accountHash = accountHash;
    }

    public boolean isTracking() {
        return activeSession != null;
    }

    public void startSession() {
        if (activeSession != null) {
            return;
        }
        lastXp.clear();
        activeSession = new StoredSession();
        activeSession.id = clock.newId();
        activeSession.accountHash = accountHash;
        activeSession.category = null;
        activeSession.name = "";
        activeSession.startMillis = clock.nowMillis();
        activeSession.trips = new ArrayList<>();
        startTrip();
    }

    private void startTrip() {
        ledger = new TripLedger();
        tripId = clock.newId();
        tripStartMillis = clock.nowMillis();
        tripDied = false;
        inventoryDirty = false;
        awaitingDeathChoice = false;
        ledger.updateCarried(normalize(carried.currentCarried()));
        refreshCache();
        panel.refresh();
    }

    public void markCarriedDirty() {
        inventoryDirty = true;
    }

    /** Record that the player just dropped this raw item id (from the "Drop" menu action). */
    public void markDropped(int rawItemId) {
        droppedThisTick.add(toKey(rawItemId));
    }

    private ItemKey toKey(int rawItemId) {
        return Doses.parse(names.apply(rawItemId))
                .map(form -> ItemKey.potion(form.family()))
                .orElse(ItemKey.item(rawItemId));
    }

    public void onTick() {
        if (ledger == null || awaitingDeathChoice) {
            return;
        }
        if (inventoryDirty) {
            ledger.updateCarried(normalize(carried.currentCarried()), droppedThisTick);
            inventoryDirty = false;
        }
        droppedThisTick.clear();
        refreshCache();
        panel.refresh();
    }

    public void onKill(String npc, Map<Integer, Integer> rawDrops) {
        if (ledger == null || awaitingDeathChoice) {
            return;
        }
        // Default the session category to the first monster killed (user-editable later),
        // so every persisted save carries a category, not just the final one.
        if (activeSession.category == null) {
            activeSession.category = npc;
        }
        ledger.recordKill(npc, normalize(rawDrops));
        refreshCache();
    }

    public void onXp(String skill, long totalXp) {
        Long previous = lastXp.put(skill, totalXp);
        if (previous == null) {
            return;
        }
        long delta = totalXp - previous;
        if (ledger != null && !awaitingDeathChoice && delta > 0) {
            ledger.recordXp(skill, delta);
            refreshCache();
        }
    }

    public void onBankOpened() {
        if (ledger == null || awaitingDeathChoice) {
            return;
        }
        endTrip();
        if (activeSession != null) {
            startTrip();
        }
    }

    public void discardTrip() {
        ledger = null;
        if (activeSession != null) {
            startTrip();
        }
    }

    public void onLocalPlayerDeath() {
        if (ledger == null || awaitingDeathChoice) {
            return;
        }
        tripDied = true;
        awaitingDeathChoice = true; // stops onTick from feeding the post-death snapshot
        panel.showDeathPrompt();
    }

    public void resolveDeath(boolean keep) {
        if (!awaitingDeathChoice) {
            return;
        }
        awaitingDeathChoice = false;
        if (keep) {
            endTrip();
            if (activeSession != null) {
                startTrip();
            }
        } else {
            discardTrip();
        }
    }

    /**
     * The latest cached snapshot. Safe to call from any thread (e.g. the Swing EDT) —
     * it returns a precomputed value object and never touches the client. The cache is
     * refreshed by {@link #refreshCache()} from the client thread after state changes.
     */
    public Optional<TripSnapshot> currentSnapshot() {
        return Optional.ofNullable(cachedSnapshot);
    }

    public String activeSessionId() {
        return activeSession == null ? null : activeSession.id;
    }

    public Optional<SessionSnapshot> currentSessionSnapshot() {
        return Optional.ofNullable(cachedSessionSnapshot);
    }

    public void renameActiveSession(String name) {
        if (activeSession == null) {
            return;
        }
        activeSession.name = name;
        if (!activeSession.trips.isEmpty()) {
            store.save(activeSession);
        }
    }

    public void recategorizeActiveSession(String category) {
        if (activeSession == null) {
            return;
        }
        activeSession.category = category;
        if (!activeSession.trips.isEmpty()) {
            store.save(activeSession);
        }
    }

    /** Recompute the cached snapshot. MUST be called on the client thread (it values items). */
    private void refreshCache() {
        cachedSnapshot = ledger == null ? null : computeSnapshot();
        cachedSessionSnapshot = activeSession == null ? null : computeSessionSnapshot();
    }

    private TripSnapshot computeSnapshot() {
        long now = clock.nowMillis();
        Trip trip = ledger.build(tripId, tripStartMillis, now, tripDied);
        long picked = trip.pickedUpValue(valuer);
        long ground = trip.missedValue(valuer);
        long supplies = trip.suppliesValue(valuer);
        long duration = now - tripStartMillis;
        long net = picked - supplies;
        long gpPerHour = duration <= 0 ? 0 : net * MILLIS_PER_HOUR / duration;
        int tripNumber = activeSession.trips.size() + 1;
        return new TripSnapshot(tripNumber, duration, trip.totalKills(),
                picked, ground, supplies, trip.totalXp(), gpPerHour);
    }

    private SessionSnapshot computeSessionSnapshot() {
        long net = 0;
        long xp = 0;
        for (StoredTrip st : activeSession.trips) {
            Trip t = SessionMapper.toTrip(st);
            FrozenItemValuer frozen = new FrozenItemValuer(SessionMapper.unitPrices(st));
            net += t.netProfit(frozen);
            xp += t.totalXp();
        }
        int tripCount = activeSession.trips.size();
        if (cachedSnapshot != null) {
            net += cachedSnapshot.pickedGp - cachedSnapshot.suppliesGp;
            xp += cachedSnapshot.totalXp;
            tripCount += 1;
        }
        long wallClock = clock.nowMillis() - activeSession.startMillis;
        long gpPerHour = wallClock <= 0 ? 0 : net * MILLIS_PER_HOUR / wallClock;
        return new SessionSnapshot(tripCount, net, xp, gpPerHour);
    }

    public void endSession() {
        if (activeSession == null) {
            return;
        }
        if (awaitingDeathChoice) {
            // Session ended with an unconfirmed death: drop the dead trip rather than
            // persisting one the user never chose to keep.
            ledger = null;
            awaitingDeathChoice = false;
        }
        if (ledger != null) {
            endTrip();
        }
        if (activeSession.category == null) {
            activeSession.category = "Uncategorized";
        }
        activeSession.endMillis = clock.nowMillis();
        if (!activeSession.trips.isEmpty()) {
            store.save(activeSession);
        }
        activeSession = null;
        ledger = null;
        refreshCache();
        panel.refresh();
    }

    private void endTrip() {
        if (ledger == null) {
            return;
        }
        Trip trip = ledger.build(tripId, tripStartMillis, clock.nowMillis(), tripDied);
        ledger = null;
        if (trip.totalKills() == 0 && trip.suppliesUsed().isEmpty() && trip.totalXp() == 0) {
            return;
        }
        Map<ItemKey, Long> unitPrices = captureUnitPrices(trip);
        activeSession.trips.add(SessionMapper.toStored(trip, unitPrices));
        activeSession.endMillis = trip.endMillis();
        store.save(activeSession);
    }

    private Map<ItemKey, Long> captureUnitPrices(Trip trip) {
        Map<ItemKey, Long> prices = new HashMap<>();
        for (ItemKey key : allKeys(trip)) {
            prices.put(key, valuer.unitValue(key));
        }
        return prices;
    }

    private static Set<ItemKey> allKeys(Trip trip) {
        Set<ItemKey> keys = new HashSet<>();
        keys.addAll(trip.dropped().keySet());
        keys.addAll(trip.pickedUp().keySet());
        keys.addAll(trip.missed().keySet());
        keys.addAll(trip.suppliesUsed().keySet());
        return keys;
    }

    private Map<ItemKey, Integer> normalize(Map<Integer, Integer> raw) {
        // Register potion families on the raw ids before they collapse into dose-keys.
        raw.forEach((id, qty) -> potions.observe(id, names.apply(id)));
        return CarriedNormalizer.normalize(raw, names);
    }
}
