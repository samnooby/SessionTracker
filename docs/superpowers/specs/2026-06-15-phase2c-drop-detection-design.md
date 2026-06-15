# Good Rune Tracker — Phase 2c: Drop Detection Design

**Date:** 2026-06-15
**Status:** Approved (brainstorming)
**Builds on:** Phase 1 core, Phase 2a adapter logic, Phase 2b RuneLite glue.

## Overview

Phase 2c removes the documented **looted-then-dropped** limitation: today the ledger
treats any inventory decrease as a supply used, so an item you loot and then drop
again is wrongly counted as a supply (and never returns to "left on the ground").
Phase 2c detects an actual **drop** and reclassifies it correctly.

## The core problem

An inventory decrease can be either a **drop** (item hits the ground) or a
**consume** (food eaten, potion drunk, ammo fired). Inventory deltas alone cannot
distinguish them. Critically, an inventory-only heuristic — "if a decreasing item
was looted this trip, undo the pickup" — would **regress** the common and
currently-correct overlap case: a consumable that also drops as loot (e.g. prayer
potions at demonic gorillas, which you both loot and drink). Under that heuristic,
drinking a looted prayer dose would be mis-counted as un-picking-up loot rather
than as a supply. The current ledger handles that overlap correctly and must keep
doing so.

The only reliable drop signal is therefore an explicit one: the **"Drop" menu
action**.

## Detection signal — the "Drop" menu action

Subscribe to RuneLite's `MenuOptionClicked`. When the menu option is **"Drop"** on
an inventory item, the player dropped that item. We record the dropped item for the
current game tick; the magnitude comes from the inventory delta we already compute
per tick. No tile geometry, no polling, no ambiguity about drop-vs-consume.

This is deliberately chosen over an `ItemSpawned`/player-tile correlation: the menu
action is lighter (no tile math) and directly captures the player's drop intent.

## Classification rule (confirmed)

For a per-item inventory decrease of `L` units this tick, for `ItemKey` k:

- **k was dropped this tick** → it is a drop:
  - reverse the looted portion first: `reversed = min(L, pickedUp[k])`. Reducing
    `pickedUp[k]` by `reversed` automatically returns that quantity to **missed**
    (since `missed = dropped − pickedUp`), and the same quantity is restored to the
    still-on-the-ground pool so a later re-pickup reconciles correctly.
  - the remainder `L − reversed` is a **brought** item dropped → counts as a
    **supply used** (matches the earlier decision that dropping a brought item is a
    supply).
- **k was not dropped this tick** → a consume → **supply used** (unchanged). This
  preserves the looted-and-consumed overlap: drinking a looted prayer dose is a
  decrease with no "Drop" action, so it stays a supply.

## Architecture

### Core — `TripLedger` (pure Java, TDD)

- Add an overload `updateCarried(Map<ItemKey,Integer> settledCarried,
  Set<ItemKey> droppedThisTick)` implementing the classification rule above.
- The existing `updateCarried(Map<ItemKey,Integer> settledCarried)` delegates to
  the new overload with an empty set, so every current Phase 1/2a ledger test stands
  unchanged and the no-drop behaviour is identical to today.
- Decreases are processed in `ItemKey` (dose-normalized) space, like all existing
  ledger logic.

### Adapter — `TrackingService` (pure Java, fake-tested)

- Add `markDropped(int rawItemId)`: normalize the raw id to its `ItemKey` (the same
  dose-aware normalization used elsewhere) and add it to a per-tick `droppedThisTick`
  set held by the service.
- `onTick()` passes `droppedThisTick` into `ledger.updateCarried(carried, dropped)`
  and then clears the set. The set is consulted on the same tick the inventory
  decrease settles.

### RuneLite glue — `GoodRuneTrackerPlugin` (manual-verified)

- Add an `@Subscribe onMenuOptionClicked(MenuOptionClicked event)` handler: when the
  option is `"Drop"` and an inventory item is the target, call
  `service.markDropped(itemId)`.
- This runs on the client thread (RuneLite events are on the client thread), and
  `markDropped` does no client access beyond the name lookup already used by
  normalization, so there is no threading concern.

## v1 scope and documented edges

- **Cross-tick lag:** in practice the "Drop" click and the resulting inventory
  change land on the same tick, so `droppedThisTick` is consumed correctly. A rare
  lag (decrease settling a tick after the click) could misclassify that drop as a
  consume. Accepted and documented.
- **Drop-and-consume of the same item in one tick:** the whole decrease for that
  item is treated as a drop. Extremely rare; accepted.
- **Destroyed items** ("Destroy" on untradeables) produce no "Drop" action and no
  ground item → still counted as a consume/supply. Unchanged; acceptable.
- **Non-"Drop" ways items leave inventory** (trade, deposit, use-on) are out of
  scope — banking ends the trip, and trading/using are not tracked.

## Testing

- **Ledger (TDD, headless):** drop a looted item → returns to missed, not a supply;
  drop a brought item → supply; consume (including a looted prayer dose) → supply,
  overlap still correct; mixed drop-and-consume across ticks; the existing
  no-drop-set delegation keeps all prior behaviour.
- **`TrackingService` (fake-tested):** `markDropped` buffers and is applied + cleared
  per tick; an end-to-end "loot then drop" sequence yields missed, not supply.
- **RuneLite glue (manual in-client):** loot an item, drop it, confirm "On ground"
  rises and "Supplies" does not; confirm consuming a looted potion still counts as a
  supply.

## Out of scope

- Tile/`ItemSpawned`-based detection (rejected in favour of the menu action).
- Any change to how consumes, kills, pickups, or banking already work.
