# Good Rune Tracker

A RuneLite plugin that tracks your **trips** and **sessions** so you can see exactly what
you gained and spent: loot picked up versus left on the ground, supplies consumed, XP, and
GP/hr — all without leaving the game.

The plugin is a passive tracker. It only observes game events (loot, XP, inventory changes)
and displays the results in its own side panel. It never sends input, automates actions, or
modifies the game interface.

## Features

- **Trip tracking** — each trip records loot received, supplies used, and net GP, splitting
  loot that you actually picked up from loot left on the ground.
- **Session summaries** — roll trips up into a session with GP/hr and XP/hr tiles, plus
  average net GP and XP per trip.
- **Per-skill XP averages** — average XP per trip and per hour, broken down by skill.
- **Category stats** — group sessions by activity (e.g. the monster being killed) and compare
  XP and GP averages across them.
- **GP valuation** — items are valued using live Grand Exchange prices, with potion doses
  normalised so partial potions are counted fairly.
- **Death & bank detection** — optionally accounts for deaths and bank trips so a single
  trip's numbers stay accurate.

## Usage

Open the **Good Rune Tracker** panel from the RuneLite sidebar. Tracking starts automatically
when you log in. The panel has three tabs:

- **Now** — your current trip and session, updating live.
- **Sessions** — past sessions with expandable summaries.
- **Stats** — per-category XP and GP averages.

Session history is stored locally under your RuneLite directory
(`.runelite/goodrunetracker`). Nothing is sent anywhere.

## Building

```
./gradlew build        # compile and run tests
./gradlew runClient    # launch a dev RuneLite client with the plugin loaded
```

## License

BSD 2-Clause. See [LICENSE](LICENSE).
