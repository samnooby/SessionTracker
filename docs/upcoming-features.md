# Upcoming features

Stat ideas for the panel, roughly in priority order within each group.

## From data we already record

These need no new tracking or storage changes; they fit into `CategoryStats` and `PanelView`.

- [ ] **Deaths and death rate** — `Trip.died()` is stored but never shown. Show deaths per
  session and per category, and the net GP of trips with a death versus trips without, so a
  category's risk is visible.
- [x] **Kills per hour** — shown in the Stats tab's per-hour card.
- [ ] **Time between trips (banking time)** — session wall clock minus the sum of trip
  durations (minus `pausedMillis`). Show it as a share of the session, e.g. "18% of session
  spent banking", to show when a different bank or teleport setup would pay off.
- [ ] **Best, worst and median trip** — by net GP, so you can see how much an activity swings
  from trip to trip. A small "net per trip" sparkline in the session view would show the same thing.
- [ ] **Profit and supply cost per kill** — `netProfit / totalKills` and
  `suppliesValue / totalKills`, per NPC. Better than per-trip numbers when trip lengths vary.
- [ ] **XP per GP spent** — `totalXp / -netProfit` for skilling that costs money (prayer,
  construction, high alch), shown as GP/XP.
- [ ] **Left-on-ground ratio** — loot left behind as a percentage of loot picked up, alongside
  the existing average gp left per trip.
- [ ] **Lifetime totals per category** — total kills, net GP and hours across every session
  in a category, computed in `CategoryStats.from`.

## Needs new tracking

- [ ] **Time to next level** — combine XP/hr with current XP (`CurrentXpSupplier` already
  reads it) to estimate time left to level.
- [ ] **Drop log and rare drops** — how many times each item dropped and the kill count when it
  did, including "dry streak since last X". `dropped` has per-trip quantities but not when each
  drop happened.
- [x] **Kill times** — average and fastest time to kill per NPC, plus combat uptime.
- [ ] **Trends over time** — GP/hr this week vs. last week, or the last 5 sessions in a category
  vs. all of them. Session start times are already stored, so this is mostly UI work.
