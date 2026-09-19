package com.sessiontracker.adapter;

import com.sessiontracker.core.Trip;
import com.sessiontracker.core.TripLedger;
import com.sessiontracker.core.item.CarriedNormalizer;
import com.sessiontracker.core.item.Doses;
import com.sessiontracker.core.item.ItemKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
    private final CurrentXpSupplier currentXp;
    private final TripNamingConfig naming;

    // Invariant: ledger != null implies activeSession != null (a trip only runs inside a session).
    private StoredSession activeSession;
    private TripLedger ledger;
    private String tripId;
    private long tripStartMillis;
    private boolean tripDied;
    private boolean inventoryDirty;
    private boolean awaitingDeathChoice;
    private boolean bankOpen;
    private boolean geOpen;
    // While > 0, the next inventory change is a container transfer (see onContainerTransfer) and
    // is rebaselined rather than reconciled. Counts down on quiet ticks so a click that changed
    // nothing (e.g. filling an already-full sack) cannot swallow a later real consumption.
    private int containerTransferTicks;
    private static final int CONTAINER_TRANSFER_WINDOW_TICKS = 3;
    private final Map<String, Long> lastXp = new HashMap<>();
    private final java.util.Set<ItemKey> droppedThisTick = new java.util.HashSet<>();

    // Snapshot valuation calls RuneLite's ItemManager, which must run on the client
    // thread. We compute it here (always invoked on the client thread) and cache a
    // plain value object the Swing panel can read from the EDT without touching the client.
    private TripSnapshot cachedSnapshot;
    private SessionSnapshot cachedSessionSnapshot;

    // Running totals of the session's completed trips, valued at their captured prices. They
    // only change when a trip ends, so the per-tick session snapshot never re-decodes them.
    private long completedNet;
    private long completedXp;
    private long completedGathered;

    // The current trip's valued contents. Rebuilt only when the ledger changes (kill, XP,
    // inventory reconcile); on quiet ticks the snapshot just refreshes duration and GP/hr.
    private TripValues tripValues;
    private boolean ledgerDirty;

    public TrackingService(Clock clock, CarriedSnapshotSupplier carried, IntFunction<String> names,
                           PotionRegistry potions, LiveItemValuer valuer, SessionStore store,
                           PanelView panel, String accountHash, CurrentXpSupplier currentXp,
                           TripNamingConfig naming) {
        this.clock = clock;
        this.carried = carried;
        this.names = names;
        this.potions = potions;
        this.valuer = valuer;
        this.store = store;
        this.panel = panel;
        this.accountHash = accountHash;
        this.currentXp = currentXp;
        this.naming = naming;
    }

    public boolean isTracking() {
        return activeSession != null;
    }

    public void startSession() {
        if (activeSession != null) {
            return;
        }
        // Prime every skill's baseline to its current total so the FIRST XP gain of each
        // skill this session is counted. StatChanged only fires on a change, so without a
        // baseline the first gain would merely prime lastXp and be lost.
        lastXp.clear();
        lastXp.putAll(currentXp.currentXp());
        activeSession = new StoredSession();
        activeSession.id = clock.newId();
        activeSession.accountHash = accountHash;
        activeSession.category = null;
        activeSession.name = "";
        activeSession.startMillis = clock.nowMillis();
        activeSession.trips = new ArrayList<>();
        completedNet = 0;
        completedXp = 0;
        completedGathered = 0;
        startTrip();
    }

    private void startTrip() {
        ledger = new TripLedger();
        tripId = clock.newId();
        tripStartMillis = clock.nowMillis();
        tripDied = false;
        inventoryDirty = false;
        awaitingDeathChoice = false;
        bankOpen = false;
        geOpen = false;
        containerTransferTicks = 0;
        ledger.updateCarried(normalize(carried.currentCarried()));
        ledgerDirty = true;
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
            Map<ItemKey, Integer> settled = normalize(carried.currentCarried());
            if (bankOpen || geOpen || containerTransferTicks > 0) {
                // Inventory changes while the bank or Grand Exchange is open (deposits,
                // withdrawals, collecting bought/sold offers), or right after a container
                // transfer, move items around rather than consuming or gaining them.
                ledger.rebaseline(settled);
            } else {
                ledger.updateCarried(settled, droppedThisTick);
                ledgerDirty = true;
            }
            inventoryDirty = false;
            containerTransferTicks = 0;
        } else if (containerTransferTicks > 0) {
            containerTransferTicks--;
        }
        droppedThisTick.clear();
        maybeNameAfterGather();
        refreshCache();
        panel.refresh();
    }

    /** Name the session after the first gathered item, if enabled and nothing named it yet. */
    private void maybeNameAfterGather() {
        if (activeSession.category == null && naming.nameAfterFirstGather()
                && ledger.firstGathered() != null) {
            activeSession.category = label(ledger.firstGathered());
        }
    }

    private String label(ItemKey key) {
        return key.isPotion() ? key.potionFamily() : names.apply(key.itemId());
    }

    public void onKill(String npc, Map<Integer, Integer> rawDrops) {
        if (ledger == null || awaitingDeathChoice) {
            return;
        }
        // Name the session after the first monster killed, if enabled and nothing named it
        // yet (gather may have named it first). User-editable later.
        if (activeSession.category == null && naming.nameAfterFirstKill()) {
            activeSession.category = npc;
        }
        ledger.recordKill(npc, normalize(rawDrops));
        ledgerDirty = true;
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
            ledgerDirty = true;
            refreshCache();
        }
    }

    /**
     * Bank interface opened. Always tracked: while the bank is open, inventory changes are
     * rebaselined rather than reconciled (see {@link #onTick()}). If {@code endTrip} is set
     * (the "Auto-end trip at bank" config), opening the bank also rolls the trip.
     */
    public void onBankOpened(boolean endTrip) {
        if (ledger == null || awaitingDeathChoice) {
            return;
        }
        if (endTrip) {
            rollTrip(); // resets bankOpen, so set the flag after the roll
        }
        bankOpen = true;
    }

    /** Manually end the current trip and start a fresh one (the "End trip" button). */
    public void endCurrentTrip() {
        if (ledger == null || awaitingDeathChoice) {
            return;
        }
        rollTrip();
    }

    private void rollTrip() {
        endTrip();
        if (activeSession != null) {
            startTrip();
        }
    }

    /** Bank interface closed. Pin the post-bank inventory as the baseline and resume tracking. */
    public void onBankClosed() {
        if (ledger == null || awaitingDeathChoice) {
            return;
        }
        bankOpen = false;
        ledger.rebaseline(normalize(carried.currentCarried()));
    }

    /**
     * Grand Exchange interface opened. While it is open, inventory changes are rebaselined
     * rather than reconciled (see {@link #onTick()}), so collecting bought/sold offers is not
     * miscounted as loot. Unlike the bank, opening the GE never ends the trip.
     */
    public void onGeOpened() {
        if (ledger == null || awaitingDeathChoice) {
            return;
        }
        geOpen = true;
    }

    /** Grand Exchange interface closed. Pin the post-GE inventory as the baseline and resume tracking. */
    public void onGeClosed() {
        if (ledger == null || awaitingDeathChoice) {
            return;
        }
        geOpen = false;
        ledger.rebaseline(normalize(carried.currentCarried()));
    }

    /**
     * Items are about to move between the inventory and a storage container whose contents the
     * client cannot see (filling or emptying a herb sack, gem bag, coal bag, fish barrel, log
     * basket...), or a readable container has just synced for the first time this login. The next
     * inventory change within a few ticks is a move, not a consumption or a gain, so it is
     * rebaselined: anything gathered earlier stays gathered, and nothing is booked as a supply.
     */
    public void onContainerTransfer() {
        if (ledger == null || awaitingDeathChoice) {
            return;
        }
        containerTransferTicks = CONTAINER_TRANSFER_WINDOW_TICKS;
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

    /**
     * Take a finished session back up as the active one and start a new trip in it. Any session
     * currently running is ended first (and kept if it recorded anything). The time between the
     * session ending and now is recorded as paused so it does not count against its GP/hr.
     */
    public void resumeSession(String sessionId) {
        if (awaitingDeathChoice) {
            return;
        }
        if (activeSession != null && sessionId.equals(activeSession.id)) {
            return;
        }
        StoredSession stored = store.copyOf(accountHash, sessionId);
        if (stored == null) {
            return;
        }
        if (activeSession != null) {
            endSession();
        }
        long now = clock.nowMillis();
        if (stored.endMillis > 0 && now > stored.endMillis) {
            stored.pausedMillis += now - stored.endMillis;
        }
        if (stored.trips == null) {
            stored.trips = new ArrayList<>();
        }
        lastXp.clear();
        lastXp.putAll(currentXp.currentXp());
        activeSession = stored;
        recomputeCompletedTotals();
        startTrip();
    }

    /**
     * Reopen the session's most recently completed trip and fold the current trip into it, as
     * if the trip had never been split (e.g. the bank ended it and the player is going straight
     * back). Loot that trip left on the ground reconciles again if it is picked up now.
     */
    public void resumeLastTrip() {
        if (ledger == null || awaitingDeathChoice || activeSession.trips.isEmpty()) {
            return;
        }
        StoredTrip last = activeSession.trips.remove(activeSession.trips.size() - 1);
        Trip previous = SessionMapper.toTrip(last);
        Trip current = ledger.build(tripId, tripStartMillis, clock.nowMillis(), tripDied);
        TripLedger merged = TripLedger.resuming(previous);
        merged.absorb(current);
        merged.rebaseline(normalize(carried.currentCarried()));
        ledger = merged;
        tripId = previous.id();
        tripStartMillis = previous.startMillis();
        tripDied = previous.died() || tripDied;
        recomputeCompletedTotals();
        persistActiveSession();
        ledgerDirty = true;
        refreshCache();
        panel.refresh();
    }

    /** Remove a completed trip from the active session; totals and averages follow from the rest. */
    public void deleteCompletedTrip(String tripId) {
        if (activeSession == null) {
            return;
        }
        if (!activeSession.trips.removeIf(t -> t.id.equals(tripId))) {
            return;
        }
        recomputeCompletedTotals();
        persistActiveSession();
        refreshCache();
        panel.refresh();
    }

    /** Save the active session, or drop its file if it no longer has any completed trips. */
    private void persistActiveSession() {
        if (activeSession.trips.isEmpty()) {
            store.delete(accountHash, activeSession.id);
        } else {
            store.save(activeSession);
        }
    }

    /** Rebuild the completed-trip totals from the stored trips (only on resume or delete). */
    private void recomputeCompletedTotals() {
        completedNet = 0;
        completedXp = 0;
        completedGathered = 0;
        for (StoredTrip st : activeSession.trips) {
            Trip t = SessionMapper.toTrip(st);
            FrozenItemValuer frozen = new FrozenItemValuer(SessionMapper.unitPrices(st));
            completedNet += t.netProfit(frozen);
            completedXp += t.totalXp();
            completedGathered += t.gatheredKeptValue(frozen);
        }
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
        if (ledgerDirty || tripValues == null) {
            tripValues = valueLedger(now);
            ledgerDirty = false;
        }
        TripValues v = tripValues;
        long duration = now - tripStartMillis;
        long net = v.picked + v.gathered - v.supplies;
        long gpPerHour = duration <= 0 ? 0 : net * MILLIS_PER_HOUR / duration;
        int tripNumber = activeSession.trips.size() + 1;
        return new TripSnapshot(tripNumber, duration, v.kills,
                v.picked, v.ground, v.supplies, v.gathered, v.consumed, v.totalXp, gpPerHour,
                v.xpBySkill, v.killsByNpc);
    }

    /** Value the live ledger. Only the duration-independent parts; see {@link #computeSnapshot()}. */
    private TripValues valueLedger(long now) {
        Trip trip = ledger.build(tripId, tripStartMillis, now, tripDied);
        // Picked-up and gathered are shown as "kept" (gross minus what we consumed this
        // trip); the consumed portion is reported separately as used loot. Net is unchanged:
        // keptPicked + keptGathered already excludes consumed, so we don't subtract it again.
        return new TripValues(trip.totalKills(),
                trip.pickedUpKeptValue(valuer), trip.missedValue(valuer), trip.suppliesValue(valuer),
                trip.gatheredKeptValue(valuer), trip.consumedLootValue(valuer), trip.totalXp(),
                SkillXp.sortedFrom(trip.xpGained()), NpcKills.sortedByCountDesc(trip.kills()));
    }

    private static final class TripValues {
        final int kills;
        final long picked;
        final long ground;
        final long supplies;
        final long gathered;
        final long consumed;
        final long totalXp;
        final List<SkillXp> xpBySkill;
        final List<NpcKills> killsByNpc;

        TripValues(int kills, long picked, long ground, long supplies, long gathered, long consumed,
                   long totalXp, List<SkillXp> xpBySkill, List<NpcKills> killsByNpc) {
            this.kills = kills;
            this.picked = picked;
            this.ground = ground;
            this.supplies = supplies;
            this.gathered = gathered;
            this.consumed = consumed;
            this.totalXp = totalXp;
            this.xpBySkill = xpBySkill;
            this.killsByNpc = killsByNpc;
        }
    }

    private SessionSnapshot computeSessionSnapshot() {
        long net = completedNet;
        long xp = completedXp;
        long gathered = completedGathered;
        int tripCount = activeSession.trips.size();
        if (cachedSnapshot != null) {
            // pickedGp/gatheredGp are already "kept" (consumed excluded), so don't subtract it.
            net += cachedSnapshot.pickedGp + cachedSnapshot.gatheredGp - cachedSnapshot.suppliesGp;
            xp += cachedSnapshot.totalXp;
            gathered += cachedSnapshot.gatheredGp;
            tripCount += 1;
        }
        long wallClock = clock.nowMillis() - activeSession.startMillis - activeSession.pausedMillis;
        long gpPerHour = wallClock <= 0 ? 0 : net * MILLIS_PER_HOUR / wallClock;
        return new SessionSnapshot(tripCount, net, xp, gpPerHour, gathered);
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
        if (trip.totalKills() == 0 && trip.suppliesUsed().isEmpty() && trip.totalXp() == 0
                && trip.gathered().isEmpty()) {
            return;
        }
        Map<ItemKey, Long> unitPrices = captureUnitPrices(trip);
        activeSession.trips.add(SessionMapper.toStored(trip, unitPrices));
        activeSession.endMillis = trip.endMillis();
        FrozenItemValuer frozen = new FrozenItemValuer(unitPrices);
        completedNet += trip.netProfit(frozen);
        completedXp += trip.totalXp();
        completedGathered += trip.gatheredKeptValue(frozen);
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
        keys.addAll(trip.gathered().keySet());
        keys.addAll(trip.consumedLoot().keySet());
        return keys;
    }

    private Map<ItemKey, Integer> normalize(Map<Integer, Integer> raw) {
        // Register potion families on the raw ids before they collapse into dose-keys.
        raw.forEach((id, qty) -> potions.observe(id, names.apply(id)));
        return CarriedNormalizer.normalize(raw, names);
    }
}
