# Charged Items (1:1 scale weapons) in Combined Carried — Design

**Date:** 2026-06-16
**Status:** Approved (brainstorming)
**Builds on:** the rune-pouch "fold a container into combined carried" work (PR #16) — same
mechanism, generalized to charged weapons.
**From:** `docs/superpowers/scoping/2026-06-16-containers-and-charges-scoping.md`.

## Overview

Charged weapons are charged by **consuming inventory items** (the toxic blowpipe / serpentine
helm / toxic staff of the dead all store **Zulrah's scales**). When you charge the weapon the
scales leave your inventory, so the diff ledger **already booked the cost — but at charge
time**, in whatever trip was active then (or none, if you charged at the bank). The trip where
you actually fight then shows **~zero** weapon cost. This fixes that **per-trip attribution**
by folding the weapon's stored scales into the same combined "carried" map the ledger diffs:

- Charge at the bank, then fight → the trip baselines with the stored scales counted as
  carried; shooting drains them (charge varbit ↓) → the diff books those scales as a supply
  **in the fighting trip**.
- **No double-count:** charging mid-trip is `inventory −N scales, weapon +N scales` → combined
  carried **unchanged** → not booked at charge; booked instead when consumed.

This is the exact same trick as the rune pouch — a charged weapon's scale store is just another
container holding a single item. The pure core `TripLedger` is unchanged.

## The "1:1" constraint (why this set)

Folding a container into carried by a single item id is correct only when **1 charge varbit
unit = 1 stored item**. That holds for the **Zulrah's-scale** weapons (the varbit counts
scales). It does **not** hold for:

- **Multi-item charges** (trident = runes + coins, scythe = blood runes + vials) — can't fold
  to one item id; need a per-charge cost model. Deferred.
- **Charge ≠ item ratios** (crystal weapons/tools: 1 shard = 100 charges; tomes: 1 page ≈ 20
  charges; venator bow / arclight: essence/shards) — folding the raw count over-counts; folding
  `count ÷ ratio` is lumpy. Deferred (a future "ratio" extension to the registry).
- **Unverified candidates** (e.g. venator bow — ratio not confirmed). Easy registry additions
  once their ratio is verified.

## Decisions (from brainstorming)

- **Coverage:** only confirmed **1 charge = 1 item** weapons. Initial set — all storing
  **Zulrah's scales**: **toxic blowpipe**, **serpentine helm**, **toxic staff of the dead**.
- **Blowpipe darts:** not tracked (no readable dart-type/count signal) — darts stay booked-at-
  load via the diff. Documented partial.
- **Registry-driven**, so adding more 1:1 items later is one line.

## Architecture

### `ChargedItems` — pure assembly helper (headless-tested)
`ChargedItems.contents(int[] varbitIds, int[] itemIds, IntUnaryOperator varbitToValue)` →
`Map<Integer,Integer>`: for each registry entry `i`, read `value = varbitToValue(varbitIds[i])`;
if `value > 0`, merge `{itemIds[i]: value}` (summing — so multiple worn scale weapons add their
counts under the same Zulrah's-scales id). RuneLite-free; the value source is injected, so it is
fully headless-tested with a fake resolver.

### `ChargedItemReader` — RuneLite-side
Holds the registry as parallel arrays (mirroring `RunePouchReader`):
- `CHARGE_VARBITS = { CHARGES_TOXIC_BLOWPIPE_QUANTITY, CHARGES_SERPENTINE_HELM_QUANTITY,
  CHARGES_TOXIC_STAFF_OF_THE_DEAD_QUANTITY }` (from `net.runelite.api.Varbits` or the gameval
  `VarbitID`).
- `ITEM_IDS = { ZULRAH_SCALE, ZULRAH_SCALE, ZULRAH_SCALE }` (Zulrah's scales item id, a stable
  constant — verified in-client).
- `contents()`: reads each varbit via `client.getVarbitValue(...)` and delegates to
  `ChargedItems.contents(CHARGE_VARBITS, ITEM_IDS, client::getVarbitValue)`.
- `static boolean isChargeVarbit(int varbitId)`: true if `varbitId` is in `CHARGE_VARBITS`.

### Fold into carried — `CarriedSnapshots.combine` becomes varargs
Replace the two existing `combine` overloads with a single varargs form
`combine(Map<Integer,Integer> inventory, Map<Integer,Integer> equipment,
Map<Integer,Integer>... extras)` that adds inventory, equipment, and each extra (positive
quantities only). Existing call sites (`combine(inv, equip)` and `combine(inv, equip, pouch)`)
keep working — they bind to 0 and 1 extras. `ClientCarriedSnapshotSupplier` passes both
sources: `combine(inv, equip, pouch.contents(), charged.contents())`.

### Mark carried dirty — extend the existing `onVarbitChanged` hook
The plugin already marks carried dirty on rune-pouch varbits. Extend the guard to also fire on
a registered charge varbit:
`if (service != null && (RunePouchReader.isRunePouchVarbit(id) || ChargedItemReader.isChargeVarbit(id)))`.
This is essential — consuming a charge changes only a varbit (no `ItemContainerChanged`), so it
must trigger the next-tick re-read.

## Data flow

```
inventory ┐
equipment ┤
rune pouch┼─→ CarriedSnapshots.combine(inv, equip, pouch, chargedItems) ─→ carried ─→ TripLedger diff
charged   ┘   (chargedItems = ChargedItemReader: registry varbits -> {Zulrah's-scales id: count})

VarbitChanged(rune-pouch OR charge varbit) ─→ service.markCarriedDirty() ─→ next onTick re-reads
```

## Edge handling

- **Multiple scale weapons worn** (e.g. blowpipe + serpentine helm): their scale counts sum
  under the one item id — exactly the player's total stored scales. Correct.
- **Charging mid-trip:** inventory −N scales / weapon +N scales → combined unchanged → not a
  supply; consumed later → a supply. No double-count.
- **Blowpipe darts:** unreadable → booked-at-load by the diff (status quo). Documented gap.
- **No charged weapon / zero charges:** the reader returns an empty map; behaviour unchanged.
- **Unknown/ratio items:** simply not in the registry → untouched.

## Testing

**Headless unit tests:**
- `ChargedItems.contents`: with a fake `varbitToValue`, entries map to their item ids; multiple
  entries with the same item id sum; zero-value entries are skipped.
- `CarriedSnapshots.combine` varargs: 0, 1, and 2 extras all merge with summed quantities; the
  existing 2-arg and 3-arg call shapes still behave.
- `TrackingService` (fakes): a charged scale weapon's scales are in the baseline; "shooting"
  (combined scale count drops by N, no inventory event) books N Zulrah's scales as a supply;
  "charging" (combined unchanged) books nothing.

**Manual in-client verification:** (needs a scale-charged weapon)
- Start a trip with a charged blowpipe/serpentine helm → fight → the consumed **scales** appear
  as a supply used in that trip.
- Charge the weapon mid-trip → no double-count.
- Confirm the `CHARGES_…_QUANTITY` varbit value equals the scale count, and the Zulrah's-scales
  item id is correct.

## File touch list

- New: `src/main/java/com/goodrunetracker/adapter/ChargedItems.java` — pure assembly helper.
- New: `src/main/java/com/goodrunetracker/adapter/runelite/ChargedItemReader.java` — registry +
  varbit reads + `isChargeVarbit`.
- Modify: `CarriedSnapshots.java` — collapse to a varargs `combine`.
- Modify: `ClientCarriedSnapshotSupplier.java` — pass the charged-items source too.
- Modify: `GoodRuneTrackerPlugin.java` — extend the `onVarbitChanged` guard with `isChargeVarbit`.
- Tests: `ChargedItemsTest.java` (new), `CarriedSnapshotsTest.java` (extend),
  `TrackingServiceTest.java` (extend — charge/shoot scenarios).

## Out of scope (deferred)

- Blowpipe darts; multi-item charges (trident, scythe); ratio≠1 items (crystal, tomes, venator
  bow, arclight); any item whose 1 charge = 1 item ratio isn't verified. All are easy registry
  additions (with a ratio field, for the non-1:1 ones) in a later ticket.
