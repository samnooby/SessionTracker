# Rune Pouch in Combined Carried — Design

**Date:** 2026-06-16
**Status:** Approved (brainstorming)
**Builds on:** the tracking ledger's combined inventory+equipment "carried" model.
**From:** `docs/superpowers/scoping/2026-06-16-containers-and-charges-scoping.md` (the
container-losses limitation).

## Overview

Today, runes moved into a rune pouch read as an inventory **decrease** and are mis-counted
as "supplies used"; runes cast **from** the pouch are invisible (no inventory change) and not
counted. This fixes both by folding the rune pouch's contents into the same combined "carried"
quantity the ledger already diffs:

- Stashing runes (inventory −N, pouch +N) → **nets to zero** → no false supply.
- Casting a rune from the pouch (pouch −1) → a genuine **−1** → correctly a supply used.

The pure core `TripLedger` is **unchanged** — it just receives a more complete carried map.
This is the rune-pouch-only first step of the deferred container work; the looting bag and
the unreadable sacks remain separate future tickets.

## Approach (chosen)

Extend the combined-carried map (inventory + equipment) with a **third source: rune-pouch
contents**, read from the pouch's varbits. Rejected alternatives: a separate pouch-delta
tracker (double-counts the stash case) and deposit-action suppression only (can't capture
casting from the pouch). The fold-into-carried approach reuses the entire existing
diff/ledger/normalization machinery for almost no new surface.

## Components

### `Runes` table — pure, headless-tested
The pouch's per-slot **type** varbit holds a rune-type *index* (not an item id). A small
static table maps that index → the rune's item id. Covers the standard runes and the common
combination runes; an unknown/zero index maps to nothing (skipped). This is stable game data
(changes only when Jagex adds a rune) — a code constant, not user configuration.

### `RunePouchReader` — RuneLite-side
Reads the rune pouch's slot varbits from the client and returns `Map<Integer,Integer>`
(runeItemId → quantity):
- Reads the **type** and **amount** varbits for each pouch slot (4 slots, covering the divine
  rune pouch's 4th slot).
- For each slot with a positive amount and a known type, maps type → rune item id via `Runes`
  and accumulates the amount.
- The varbit read is client-bound (manual-verified). The pure part — turning the raw
  `(types[], amounts[])` arrays into the runeItemId→qty map via the table — is split into a
  static helper so it is headless-tested.

### `CarriedSnapshots.combine` — 3-source overload (pure)
Add an overload `combine(inventory, equipment, pouch)` that merges all three positive-quantity
maps. The existing 2-arg overload stays (delegates to the 3-arg with an empty pouch), so
nothing else changes. `ClientCarriedSnapshotSupplier` reads the pouch via `RunePouchReader`
and passes it as the third source.

### Plugin `@Subscribe onVarbitChanged` — mark carried dirty
Casting purely from the pouch changes only a varbit (no `ItemContainerChanged`), so the
ledger would never re-read carried and would miss the consumption. The plugin subscribes to
`VarbitChanged`; when the changed varbit is one of the rune-pouch varbits, it calls
`service.markCarriedDirty()`, so the next `onTick` re-reads carried (now reflecting the pouch
change) and books the rune supply. (Stashing already marks dirty via the inventory
`ItemContainerChanged`, but marking on the pouch varbit too is harmless and keeps both
directions correct.)

## Data flow

```
inventory ┐
equipment ┼─→ CarriedSnapshots.combine(inv, equip, pouch) ─→ carried map ─→ TripLedger diff
pouch ────┘   (pouch = RunePouchReader: slot varbits → Runes table → runeItemId→qty)

VarbitChanged(rune-pouch varbit) ─→ service.markCarriedDirty() ─→ next onTick re-reads carried
```

## Edge handling

- **Loot vs supply overlap:** no special handling — runes fold into the existing combined diff
  like any other consumable. A looted rune is counted as picked-up when it enters inventory;
  stashing and casting then net through the same machinery.
- **Combination / unknown rune types** not in the table are skipped (folded in as nothing) — a
  documented minor inaccuracy, never a mis-count.
- **Dose normalization:** runes are not potions, so `CarriedNormalizer` leaves them as plain
  item ids; folding pouch runes in by item id is consistent with loose runes.
- **No pouch / not carrying one:** the pouch read returns an empty map; behavior is identical
  to today.

## Testing

**Headless unit tests:**
- `CarriedSnapshots.combine(inv, equip, pouch)`: three sources merge with summed quantities;
  the 2-arg overload still behaves as before (empty pouch).
- The `Runes` table / `RunePouchReader` pure helper: `(types[], amounts[])` → runeItemId→qty;
  known types map correctly, unknown/zero types skipped, amounts summed per rune.
- `TrackingService` (fakes): drive the `FakeCarried` supplier to simulate (a) stashing — a
  rune leaves inventory and appears in the pouch on the same settled tick → **no supply**; and
  (b) casting from the pouch — a pouch rune decreases with no inventory change → **a rune
  supply** is recorded. Reuses the existing ledger test harness; proves the end-to-end
  classification.

**Manual in-client verification:**
- Stash runes into the pouch → Supplies does not increase.
- Cast a spell using only pouch runes → the consumed runes appear as a supply used.
- Confirm the divine pouch's 4th slot is read.

## File touch list

- New: `src/main/java/com/goodrunetracker/adapter/Runes.java` — type-index → rune item id table
  + the pure `(types[], amounts[]) -> Map<Integer,Integer>` helper.
- New: `src/main/java/com/goodrunetracker/adapter/runelite/RunePouchReader.java` — reads the
  slot varbits from the client and calls the pure helper.
- Modify: `CarriedSnapshots.java` — add the 3-source `combine` overload.
- Modify: `ClientCarriedSnapshotSupplier.java` — read the pouch and pass it as the 3rd source.
- Modify: `GoodRuneTrackerPlugin.java` — `@Subscribe onVarbitChanged` → `markCarriedDirty()`
  on rune-pouch varbits.
- Tests: `CarriedSnapshotsTest.java` (extend), `RunesTest.java` (new),
  `TrackingServiceTest.java` (extend with the stash/cast scenarios).

## Out of scope (deferred)

- Looting bag (loot semantics, synced-only container read).
- Herb/gem/coal/seed sacks (contents unreadable; possible future deposit-action suppression).
- Any general/configurable container framework — each container is exposed differently and is
  added bespoke if and when it's worth it.
