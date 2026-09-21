# Session Tracker

[![Build](https://github.com/samnooby/SessionTracker/actions/workflows/build.yml/badge.svg)](https://github.com/samnooby/SessionTracker/actions/workflows/build.yml)

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
- **Storage containers** — the rune pouch, looting bag, seed box, plank sack and the forestry,
  huntsman's and tackle kits are read directly, so stashing items is never counted as a cost and
  unpacking them is never counted as a gain.
- **Open bags that collect for you** — a herb sack, gem bag, coal bag, fish barrel or log basket
  hides its contents from the client entirely, so what an open one swallows is read from the
  game's own gather messages and counted in the trip it happened in, rather than whenever you
  next empty the bag. Turn this off with **Track open storage bags** if a game update ever makes
  the counts look wrong.

## Usage

Open the **Session Tracker** panel from the RuneLite sidebar. Tracking starts automatically
when you log in — turn off **Auto-start tracking on login** in the plugin settings if you'd
rather start each session yourself with the **Start tracking** button. Stopping tracking by
hand stays stopped until your next login. The panel has three tabs:

- **Now** — your current trip and session, updating live. **Resume last trip** reopens the
  trip that just ended and folds the current one into it, for when the bank split a trip you
  meant to continue.
- **Sessions** — past sessions with expandable summaries. **Resume session** picks a finished
  session back up so new trips are added to it, with the time since it ended left out of its
  GP/hr. Trips and sessions can be deleted from here, including trips of the running session;
  every total and average is recomputed from what remains.
- **Stats** — per-category XP and GP averages.

Session history is stored locally under your RuneLite directory
(`.runelite/sessiontracker`). Nothing is sent anywhere.

### Known limitations

- **What an open bag collects is inferred, not read.** The game never exposes these contents, so
  the plugin counts the messages it prints as you gather. Anything it does not print is missed:
  herbs harvested from a farming patch, coal or gems picked up off the ground into an open bag,
  and the mining cape's extra ore. Those gains are not recorded at all, including after you empty
  the bag. Keep the bag closed and use **Fill** if you need them counted exactly.
- **A manual Fill or Empty resets the count** for that bag, because the plugin can no longer tell
  what is inside. Nothing is mis-counted; it just starts believing the bag is empty again.
- **Using an item on a sack** (rather than the sack's own Fill option) is not detected.
- **Charged items** are only tracked for the Zulrah's-scale weapons (blowpipe, serpentine helm,
  toxic staff).

## Building

```
./gradlew build        # compile and run tests
./gradlew runClient    # launch a dev RuneLite client with the plugin loaded
```

The tests run headless and cover the core ledger maths, the plugin's RuneLite event wiring
(login, loot, banking, death, logout) against a mocked client, and the Swing panels, so most
changes can be checked with `./gradlew test` instead of launching a client. GitHub Actions runs
the same build on every push and pull request.

## License

BSD 2-Clause. See [LICENSE](LICENSE).
