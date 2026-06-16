# Containers & Charged Items — Tracking Scoping

**Date:** 2026-06-16
**Status:** Scoping (pre-brainstorm). Not a committed design — captures the problem, the
available RuneLite signals, candidate approaches, and the open questions to resolve before
each becomes its own spec.

## The shared root problem

The tracking ledger is an inventory+equipment **diff** model: a net decrease of an item
id is "supplies used", a net increase reconciled against the kill-loot ground pool is
"picked up". Both features below break that assumption because **game state changes without
a corresponding inventory item change**:

- **Containers:** an item leaves the inventory (decrease) but isn't consumed — it moved into
  a storage container. The diff mis-classifies it as a supply used (or, for the looting bag,
  books loot as a cost).
- **Charges:** an item is consumed (a blowpipe shot) but it's an internal charge counter, so
  there is **no** inventory change at all — the diff misses it entirely.

These are the two limitations documented in the master design's "Known v1 limitations".
They need **different** solutions and should be **two separate tickets**.

---

## A. Storage containers (rune pouch, looting bag, sacks)

### Current (wrong) behavior

- **Supply containers** — rune pouch, herb sack, gem bag, coal bag, seed box. Items go in to
  be *used later*. Today: stashing runes into the pouch reads as an inventory decrease →
  falsely counted as "supplies used"; then casting a rune *from* the pouch is invisible (no
  inventory change) → not counted. Double-wrong: cost booked at the wrong time, real use
  never booked.
- **Loot container** — the looting bag. Items go in as *loot you picked up*. Today:
  depositing loot into the bag reads as an inventory decrease → counted as "supplies used".
  This is the worst case: profit booked as cost.

### RuneLite signals available (verified against client 1.12.28)

- **Rune pouch — fully readable, every tick.** Varbits expose the contents directly:
  `Varbits.RUNE_POUCH_RUNE1..6` (rune item ids per slot) + `RUNE_POUCH_AMOUNT1..4`
  (quantities). RuneLite's own `runepouch` plugin reads exactly these. (Newer gameval form:
  `VarbitID.RUNE_POUCH_TYPE_1..3` / `RUNE_POUCH_QUANTITY_1..3`.)
- **Looting bag — readable when synced.** `InventoryID.LOOTING_BAG` is a real item container
  (`client.getItemContainer(InventoryID.LOOTING_BAG)`), but it only updates when the bag is
  opened/deposited into, not continuously. RuneLite's loot-tracker reads it this way.
- **Herb / gem / coal / seed sacks — effectively not readable.** No varbits; contents are
  surfaced only via a "check" chat message. Tracking these reliably is not feasible with the
  current client surface.

### Candidate approach: extend "combined carried" to include container contents

The ledger already tracks a **combined** carried quantity across inventory **+** equipment
(so equipping gear or firing-then-recovering ammo nets correctly). The fix is to fold
container contents into that same combined quantity:

- **Rune pouch (recommended first):** read the 4 (type, amount) varbit pairs each tick and
  add them to the carried map under the rune item id. Effect:
  - Stashing runes (inventory −N, pouch +N) → **net zero** → no longer a false supply.
  - Casting a rune from the pouch (pouch −1) → a genuine **−1** decrease → correctly a
    supply used.
  - This is clean, well-bounded, high value (rune cost is a real recurring supply), and
    headless-testable (feed a fake carried+pouch snapshot).
- **Looting bag (second step):** harder because its contents are **loot**, not supply.
  Folding it into carried stops the "loot → supply" mis-count (deposits net to zero), but the
  loot in the bag should still count as **picked up** (profit), and is only realized when
  banked. Needs loot semantics + the synced-only read. Defer to its own step.
- **Sacks:** document as still-unhandled; out of scope until the client exposes contents.

### Architecture fit

Adapter-only. Enrich the `CarriedSnapshotSupplier` (or the `normalize` step) so the carried
map includes rune-pouch contents (and later the looting bag). The pure-core `TripLedger` is
**unchanged** — it just receives a more complete carried map. New testable surface: the
"read rune pouch varbits → merge into carried" function.

### Open questions (resolve in the brainstorm)

1. Scope of the first ticket — **rune pouch only**, or rune pouch + looting bag together?
   (Recommend rune pouch only; looting bag is a meaningfully different problem.)
2. Looting bag loot semantics: do bag contents count as **picked up** immediately on
   deposit, or only when banked? How to handle the synced-only container read (only update
   the carried baseline when the bag container event fires)?
3. Divine rune pouch holds 4 slots (up to `RUNE_POUCH_RUNE6`/`AMOUNT4`); confirm we read all
   slots, not just the classic 3.
4. Edge: runes that are *both* loose-inventory and in-pouch — the combined count handles
   this, but verify the dose/normalization layer doesn't interfere (runes aren't potions, so
   should be fine).

---

## B. Charged items (toxic blowpipe, trident, …)

### Current (wrong) behavior

Charges are an internal counter with **no** inventory change on consumption, so a blowpipe
shot (Zulrah scales + darts) or a trident cast (runes) registers **zero** supply cost. The
ledger never sees it.

### RuneLite signals available (verified against client 1.12.28)

The marquee combat charged items expose charge state directly via varp/varbit:

- **Toxic blowpipe** → `VarPlayerID.CHARGES_TOXIC_BLOWPIPE_QUANTITY` (a single clean varp).
- **Trident** (and fossil-island merfolk trident) → `VarbitID.FOSSIL_MERFOLK_TRIDENT_CHARGES`;
  sanguinesti staff, scythe of vitur, etc. have their own varbits.
- **Small charged jewelry** (bracelet of slaughter, ring of forging, dodgy necklace, …) →
  RuneLite only tracks these via **chat messages** (`itemcharges` plugin, `ItemChargeType`).
  Messy, per-item, low value — **out of scope**.

### Candidate approach: a new charge-delta signal path + per-item cost registry

Unlike containers, charges are not an inventory diff — they need a **new event path**:

- Subscribe to `VarbitChanged` (and varp changes). For each **supported** charged item, watch
  its charge varp/varbit; when it **decreases by N**, book N charges as consumed.
- Convert charges-consumed into a **supply cost** via a per-item **cost registry**: each
  supported item maps to "what one charge costs" (e.g. blowpipe: 1 dart + a fractional Zulrah
  scale per shot; trident: the per-cast rune cost). Value that via the existing `ItemValuer`
  and feed it into the ledger as supplies used.
- **The crux is the per-item cost model**, not the plumbing: the varp tells you *how many*
  charges went; you must encode *what each one costs*. That's item-specific and is where the
  real design effort is.

### Pilot recommendation

Do the **toxic blowpipe alone** first (one clean varp, well-known mechanics), prove the
end-to-end pattern (VarbitChanged → charge delta → cost → ledger supply → panel), then add
items one at a time behind the same registry. Do **not** attempt generic chat-message charge
tracking.

### Architecture fit

A new adapter component, e.g. `ChargedItemTracker`, fed by a `VarbitChanged` handler in the
plugin, holding a `ChargedItemRegistry` (item → charge-source varp/varbit + per-charge cost
in `ItemKey` terms). It emits supply-used entries into the existing ledger/trip. More new
surface than the container fix, but it follows the established "adapter logic behind an
interface, headless-tested" pattern.

### Open questions (resolve in the brainstorm)

1. Exact blowpipe cost-per-charge model: the varp tracks *charges* — confirm whether one
   "charge" = one dart + (1/3) scale, or how to express the scale fraction; decide how to
   value a fractional scale per shot (accumulate fractional, book whole scales?).
2. Baseline/priming: like XP, the first `VarbitChanged` per item should **prime** the
   baseline (don't count pre-existing charges as "used" on login or trip start).
3. Charging/recharging: adding charges (an **increase**) must be ignored as a supply (and is
   itself a supply cost paid at the bank, separate concern — likely out of scope, just don't
   double-count).
4. Which items in the pilot+ set: blowpipe only first; then trident, sang, scythe? Each needs
   its own cost rule.
5. Death/de-charge edge: some items lose charges on death — must not register that as a
   supply (the existing death-handling already stops feeding ticks; ensure varp changes are
   likewise gated).

---

## Recommendation

- **Two separate tickets.**
- **First: rune pouch (container losses).** Cleanest readable state, fits the existing
  combined-carried model with minimal new surface, high confidence, fully headless-testable.
- **Second: blowpipe charges (charged-item pilot).** High value and well-defined, but a
  larger new subsystem (new signal path + cost registry); start with one item.
- **Later:** looting bag (loot semantics), then more charged items; the unreadable sacks stay
  documented-but-unhandled.

Each ticket goes through the normal cycle: brainstorm → spec → plan → subagent-driven build → PR.
