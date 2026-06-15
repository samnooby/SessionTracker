package com.goodrunetracker.adapter;

import com.goodrunetracker.core.Trip;
import com.goodrunetracker.core.TripLedger;
import com.goodrunetracker.core.item.CarriedNormalizer;
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
        panel.refresh();
    }

    public void markCarriedDirty() {
        inventoryDirty = true;
    }

    public void onTick() {
        if (ledger == null || awaitingDeathChoice) {
            return;
        }
        if (inventoryDirty) {
            ledger.updateCarried(normalize(carried.currentCarried()));
            inventoryDirty = false;
        }
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
    }

    public void onXp(String skill, long totalXp) {
        Long previous = lastXp.put(skill, totalXp);
        if (previous == null) {
            return;
        }
        long delta = totalXp - previous;
        if (ledger != null && !awaitingDeathChoice && delta > 0) {
            ledger.recordXp(skill, delta);
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

    public Optional<TripSnapshot> currentSnapshot() {
        if (ledger == null) {
            return Optional.empty();
        }
        long now = clock.nowMillis();
        Trip trip = ledger.build(tripId, tripStartMillis, now, tripDied);
        long picked = trip.pickedUpValue(valuer);
        long ground = trip.missedValue(valuer);
        long supplies = trip.suppliesValue(valuer);
        long duration = now - tripStartMillis;
        long net = picked - supplies;
        long gpPerHour = duration <= 0 ? 0 : net * MILLIS_PER_HOUR / duration;
        int tripNumber = activeSession.trips.size() + 1;
        return Optional.of(new TripSnapshot(tripNumber, duration, trip.totalKills(),
                picked, ground, supplies, trip.totalXp(), gpPerHour));
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
