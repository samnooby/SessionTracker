# Per-Category Loot Averages (picked / supplies / left-on-ground / gross drops) — Design

**Date:** 2026-06-17
**Status:** Approved (brainstorming)
**Builds on:** the per-category averages already on the Stats detail (XP averages, kill
averages, supplies averages) — same per-item-table pattern.

## Overview

Add **per-item average tables** to the Stats category detail for the loot a trip produces, so
you can compare, per category: what you **picked up**, what **supplies** you used, what you
**left on ground**, and the **gross drops** the NPCs gave. The existing "Per-trip averages"
block already shows aggregate gp figures (net, missed); this adds the per-item breakdown,
matching the existing Avg-supplies / XP-averages / kill-averages tables.

The data already exists: `Trip` exposes `pickedUp()`, `dropped()` (gross), and `missed()`
(left on ground), all `Map<ItemKey,Integer>`. No core changes.

## Decisions (from brainstorming)

- **Four per-item tables**, in this order, right after the "Per-trip averages" block:
  1. **Avg picked up / trip** — GP-green values.
  2. **Avg supplies / trip** — the existing supplies table, moved into this position; red values (cost).
  3. **Avg left on ground / trip** — amber values (matches the existing "Missed").
  4. **Gross avg drops / trip** — neutral white values (total dropped, not all kept → not "profit").
- The XP-averages and kill-averages tables stay **after** this group (unchanged).
- Each table: per-item rows `label ×qty  gp` (qty 1-dp), sorted by avg gp descending, with a bold
  **Total** (avg gp/trip); "None" when the category has none.

## Architecture

### Data layer (headless, unit-tested) — all in `SessionHistory`

- **Generalize the per-item-average carrier.** The existing `SupplyAverage {label,
  avgQtyPerTrip(double), avgGpPerTrip(long)}` is renamed/added as a generic **`ItemAverage`**
  with the same fields, reused for all four loot kinds (picked, supplies, missed, dropped). To
  avoid a wide rename, introduce `ItemAverage` and have the supplies path use it too (the field
  name `supplies` on `CategoryDetail` stays; its element type becomes `ItemAverage`).
- **`CategoryDetail` gains three lists** (each `List<ItemAverage>`, sorted by avg gp desc) and
  their per-trip gp totals:
  - `pickedAverages` + `avgPickedGpPerTrip`
  - `missedAverages` + (`avgMissedPerTrip` already exists — reuse as the missed total)
  - `droppedAverages` + `avgDroppedGpPerTrip`
- **One shared helper** computes a per-item-average list + total from a trip-map extractor:
  given the category's sessions, the per-trip valuer `fn`, `tripCount`, and a
  `Function<Trip, Map<ItemKey,Integer>>` (e.g. `Trip::pickedUp`), it sums per-item qty and
  per-item gp (`fn.apply(t).value(key, qty)`) across all trips, then `avgQty = totalQty /
  tripCount`, `avgGp = totalGp / tripCount`, sorted by avgGp desc, plus the summed total. The
  existing supplies computation is refactored onto this helper, and picked / missed / dropped
  reuse it with `Trip::pickedUp`, `Trip::missed`, `Trip::dropped`.
- Item labels resolve through the existing `label(ItemKey)` (item id → name via the injected
  `names`, potion family → family). Because that touches `names` (which on the live client is
  `ItemManager`), `categoryDetail` is already invoked on the client thread by the Stats tab
  (existing behaviour) — unchanged.

### UI layer (RuneLite glue, manual-verified) — `StatsTab.renderDetail`

Reuses the existing per-item table rendering (the supplies table's grid + bold Total). Insert
the four tables, in order, immediately after the "Per-trip averages" card:
1. `Avg picked up / trip` — `Styles.GP`.
2. `Avg supplies / trip` — `Styles.NEG` (this is the existing supplies table, just repositioned).
3. `Avg left on ground / trip` — `Styles.MISSED`.
4. `Gross avg drops / trip` — `Styles.TEXT`.

The "XP averages" and "Kill averages" sections remain after these four, unchanged. A small
shared render helper (section header + card + per-item grid + bold Total + "None" empty path)
removes the duplication across the four loot tables.

## Data flow

```
stored sessions ─→ SessionHistory.categoryDetail(cat) ─→ CategoryDetail{
    pickedAverages,  avgPickedGpPerTrip,
    supplies,        avgTotalSuppliesGpPerTrip,   (existing)
    missedAverages,  avgMissedPerTrip,            (total already present)
    droppedAverages, avgDroppedGpPerTrip,
    ... }
  └─→ StatsTab: four per-item tables after "Per-trip averages"
```

## Testing

**Headless unit tests** (`SessionHistoryTest`):
- `categoryDetail` produces correct `pickedAverages` / `missedAverages` / `droppedAverages`:
  per-item avg qty (totalQty / tripCount) and avg gp (totalGp / tripCount, via the per-trip
  captured prices), sorted by avg gp desc, summing to the right per-trip totals. Use trips with
  known dropped/picked/missed maps and prices.
- The refactored supplies path still produces the same values (existing supplies test stays green).

**Manual in-client verification:** a category detail shows the four tables in order (picked /
supplies / left-on-ground / gross drops) with correct per-item averages and totals, then XP and
kill averages below.

## File touch list

- Modify: `SessionHistory.java` — `ItemAverage` carrier (generalized from `SupplyAverage`); the
  shared per-item-average helper; `CategoryDetail` new lists/totals; `categoryDetail` populates
  picked/missed/dropped and refactors supplies onto the helper.
- Modify: `StatsTab.java` — render the four loot tables in order (a shared per-item-table helper);
  reposition the supplies table.
- Tests: `SessionHistoryTest.java`.

## Out of scope

- Sessions-tab or Now-tab loot breakdowns (this is Stats category detail only).
- Any change to net-profit / gp-hr / missed aggregate figures (the "Per-trip averages" block is
  unchanged).
