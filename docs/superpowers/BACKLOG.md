# Good Rune Tracker — Backlog

A living list of what's next, compiled from the design specs' deferred / out-of-scope /
known-limitation notes plus new ideas. Check items off as they ship. Each item, when
picked up, goes through the usual cycle: brainstorm → spec → plan → subagent-driven build → PR.

## Recently shipped

- Phases 1–3: domain core, RuneLite adapter, tabbed **Now / Sessions / Stats** UI.
- Drop detection (looted-drop vs consume).
- Full visual styling pass on all three tabs.
- Per-trip **XP breakdown** by skill (with skill icons).
- Per-trip **kills breakdown** by NPC.

---

## Next up — data already captured, just needs aggregation + UI

These have the per-trip data in place (`SkillXp`, `NpcKills`, the trip maps), so they mostly
mirror the existing `SessionHistory.categoryDetail` supply-averages code.

- [ ] **XP averages on Sessions & Stats** — per-category XP/hr and avg XP/trip, broken down by
  skill. (Deferred from the per-trip XP ticket.)
- [ ] **Kill averages on Sessions & Stats** — per-category avg kills/trip per NPC. (Deferred from
  the per-trip kills ticket.)

## Requested features

- [ ] **Resume a session** — let the user re-open the most recently ended session and keep adding
  trips to it, instead of being forced to start a fresh session. Motivation: recover gracefully
  from stopping tracking by mistake. Needs: a "Resume last session" (or pick-a-recent-session)
  control that reloads a stored session as the active one and continues; must re-baseline the
  carried inventory and re-prime the per-skill XP baselines on resume, and only allow resuming a
  session belonging to the current account.

## Feature additions (from the master spec's deferred list)

- [ ] **In-game overlay** — a live HUD overlay in the game viewport, not just the side panel.
- [ ] **CSV / export** of session & trip history.
- [ ] **Multi-account comparison views.**
- [ ] **Idle-timeout auto-end** of sessions (today sessions end only manually).
- [ ] **Smarter trip detection** — region/teleport-based trip boundaries (today only the bank
  interface opening auto-ends a trip).

## Known tracking limitations (documented; fixing improves accuracy)

- [ ] **Charged items** (trident, blowpipe, etc.) — charge consumption is invisible to inventory
  diffs and is untracked.
- [ ] **Storage containers** (looting bag, rune pouch, herb sack, gem/coal bag, seed box) — moving
  items in reads as an inventory decrease and is counted as "supplies used"; the looting bag in
  particular mis-attributes stored loot to supplies.
- [ ] **Non-potion item transformations** (fletching, alching, etc.) — consumed inputs aren't
  classified cleanly.
