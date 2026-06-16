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

### `RunePouch` assembly helper — pure, headless-tested
The pouch's per-slot **type** varbit holds a rune-type *index* (not an item id). The
index → rune-item-id mapping is **not hardcoded** — it is read at runtime from the game's own
cache enum (see `RunePouchReader`). The pure, RuneLite-free helper
`RunePouch.contents(int[] types, int[] amounts, IntUnaryOperator typeToItemId)` does the
assembly: for each slot with a positive amount and a non-zero type, resolve the item id via
the injected `typeToItemId` resolver and accumulate the amount into a `Map<Integer,Integer>`
(runeItemId → quantity); a type whose resolver returns ≤ 0 is skipped. Because the resolver is
injected, this is fully headless-tested with a fake mapping.

### `RunePouchReader` — RuneLite-side
Reads the rune pouch's slot varbits from the client and returns the runeItemId→quantity map:
- Reads the **type** and **amount** varbits for each pouch slot (`Varbits.RUNE_POUCH_RUNE1..4`
  and `RUNE_POUCH_AMOUNT1..4` — 4 slots, covering the divine rune pouch's 4th slot) via
  `client.getVarbitValue(...)`.
- Builds the resolver from the game cache enum the same way RuneLite's own runepouch plugin
  does: `typeValue -> client.getEnum(<rune-pouch enum id>).getIntValue(typeValue)` (the enum
  maps the type-varbit value to the rune item id). The enum id is the one RuneLite uses
  (observed as `982` in client 1.12.28); the implementation pins it to a named constant with a
  comment.
- Calls `RunePouch.contents(types, amounts, resolver)` and returns the result. This whole
  class is client-bound (manual-verified); the assembly logic it delegates to is the pure
  helper above.

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
- **Unknown rune types** (resolver returns ≤ 0) are skipped (folded in as nothing) — a
  documented minor inaccuracy, never a mis-count. Combination runes resolve fine because the
  cache enum already maps them.
- **Dose normalization:** runes are not potions, so `CarriedNormalizer` leaves them as plain
  item ids; folding pouch runes in by item id is consistent with loose runes.
- **No pouch / not carrying one:** the pouch read returns an empty map; behavior is identical
  to today.

## Testing

**Headless unit tests:**
- `CarriedSnapshots.combine(inv, equip, pouch)`: three sources merge with summed quantities;
  the 2-arg overload still behaves as before (empty pouch).
- `RunePouch.contents(types[], amounts[], typeToItemId)`: with a fake resolver, known types map
  to their item id and sum per rune; a type the resolver returns ≤ 0 for is skipped; zero
  amounts contribute nothing.
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

- New: `src/main/java/com/goodrunetracker/adapter/RunePouch.java` — pure assembly helper
  `contents(int[] types, int[] amounts, IntUnaryOperator typeToItemId)`.
- New: `src/main/java/com/goodrunetracker/adapter/runelite/RunePouchReader.java` — reads the
  slot varbits + builds the cache-enum resolver and calls `RunePouch.contents`.
- Modify: `CarriedSnapshots.java` — add the 3-source `combine` overload.
- Modify: `ClientCarriedSnapshotSupplier.java` — read the pouch and pass it as the 3rd source.
- Modify: `GoodRuneTrackerPlugin.java` — `@Subscribe onVarbitChanged` → `markCarriedDirty()`
  on rune-pouch varbits.
- Tests: `CarriedSnapshotsTest.java` (extend), `RunePouchTest.java` (new),
  `TrackingServiceTest.java` (extend with the stash/cast scenarios).

## Out of scope (deferred)

- Looting bag (loot semantics, synced-only container read).
- Herb/gem/coal/seed sacks (contents unreadable; possible future deposit-action suppression).
- Any general/configurable container framework — each container is exposed differently and is
  added bespoke if and when it's worth it.
