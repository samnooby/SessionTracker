# Good Rune Tracker — Phase 2b: RuneLite Glue & Minimal Panel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the plugin actually run in the RuneLite client — wire game events into the tested Phase 2a `TrackingService`, supply the RuneLite-backed implementations of its collaborator interfaces, add config, and ship a minimal Swing control panel.

**Architecture:** A thin `com.goodrunetracker.adapter.runelite` layer: a `Plugin` whose `@Subscribe` handlers forward to `TrackingService`, plus small classes implementing the Phase 2a seams (`Clock`, `CarriedSnapshotSupplier`, `ItemPriceSource`, item-name lookup, `PanelView`) against `Client`/`ItemManager`. Only two pieces hold real pure logic and are unit-tested (number formatting and the inventory+equipment combiner); the rest is compile-checked and verified by an in-client checklist.

**Tech Stack:** Java 11, Gradle, RuneLite client API (`compileOnly`), Swing, JUnit 4. No Lombok.

**Reference:** Phase 2 spec `docs/superpowers/specs/2026-06-14-phase2-runelite-adapter-design.md` (esp. "Phase 2b decisions"). Builds on the merged Phase 2a adapter.

---

## Phase 2a APIs this plan wires to (already present)

`com.goodrunetracker.adapter`:
- `TrackingService(Clock, CarriedSnapshotSupplier, IntFunction<String> names, PotionRegistry, LiveItemValuer, SessionStore, PanelView, String accountHash)` with public methods `isTracking()`, `startSession()`, `endSession()`, `markCarriedDirty()`, `onTick()`, `onKill(String, Map<Integer,Integer>)`, `onXp(String, long)`, `onBankOpened()`, `discardTrip()`, `onLocalPlayerDeath()`, `resolveDeath(boolean)`, `currentSnapshot() -> Optional<TripSnapshot>`.
- `Clock` (`long nowMillis()`, `String newId()`), `CarriedSnapshotSupplier` (`Map<Integer,Integer> currentCarried()`), `PanelView` (`void refresh()`, `void showDeathPrompt()`), `ItemPriceSource` (`int price(int)`).
- `TripSnapshot` public fields: `tripNumber`, `durationMillis`, `kills`, `pickedGp`, `groundGp`, `suppliesGp`, `totalXp`, `gpPerHour`.
- `PotionRegistry`, `LiveItemValuer(ItemPriceSource, PotionRegistry)`, `SessionStore(java.nio.file.Path)`.

## File Structure (new)

Pure, unit-tested:
- `src/main/java/com/goodrunetracker/adapter/GpFormat.java` — gp → short string ("1.46M").
- `src/main/java/com/goodrunetracker/adapter/CarriedSnapshots.java` — combine two id→qty maps.

RuneLite-coupled (compile-checked + in-client verified), under `src/main/java/com/goodrunetracker/adapter/runelite/`:
- `GoodRuneTrackerConfig.java` — `@ConfigGroup` interface.
- `GoodRuneTrackerPanel.java` — minimal `PluginPanel` implementing `PanelView`.
- `RuneLiteCollaborators` split into: `SystemClock.java`, `ItemManagerPriceSource.java`, `ClientCarriedSnapshotSupplier.java` (item-name lookup is an inline lambda).
- `GoodRuneTrackerPlugin.java` — `@PluginDescriptor`, `@Subscribe` wiring, login lifecycle.

Resources:
- `src/main/resources/runelite-plugin.properties`
- `src/main/resources/com/goodrunetracker/adapter/runelite/icon.png` (sidebar icon).

Dev runner (test sources):
- `src/test/java/com/goodrunetracker/adapter/runelite/GoodRuneTrackerPluginRun.java` — `main()` that launches a dev client with the plugin.

---

## Task 1: `GpFormat` (TDD)

**Files:**
- Create: `src/main/java/com/goodrunetracker/adapter/GpFormat.java`
- Test: `src/test/java/com/goodrunetracker/adapter/GpFormatTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.goodrunetracker.adapter;

import static org.junit.Assert.*;
import org.junit.Test;

public class GpFormatTest {

    @Test
    public void formatsPlainBelowAThousand() {
        assertEquals("950", GpFormat.format(950));
        assertEquals("0", GpFormat.format(0));
    }

    @Test
    public void formatsThousandsWithOneDecimal() {
        assertEquals("1.5K", GpFormat.format(1_500));
        assertEquals("412.0K", GpFormat.format(412_000));
    }

    @Test
    public void formatsMillionsWithTwoDecimals() {
        assertEquals("1.46M", GpFormat.format(1_460_000));
    }

    @Test
    public void formatsBillionsWithTwoDecimals() {
        assertEquals("3.10B", GpFormat.format(3_100_000_000L));
    }

    @Test
    public void formatsNegatives(){
        assertEquals("-64.0K", GpFormat.format(-64_000));
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests com.goodrunetracker.adapter.GpFormatTest`
Expected: FAIL — `GpFormat` does not exist.

- [ ] **Step 3: Implement**

```java
package com.goodrunetracker.adapter;

import java.util.Locale;

/** Formats gp amounts as short human strings (e.g. 1_460_000 -> "1.46M"). */
public final class GpFormat {

    private GpFormat() {
    }

    public static String format(long gp) {
        long abs = Math.abs(gp);
        if (abs >= 1_000_000_000L) {
            return String.format(Locale.US, "%.2fB", gp / 1_000_000_000.0);
        }
        if (abs >= 1_000_000L) {
            return String.format(Locale.US, "%.2fM", gp / 1_000_000.0);
        }
        if (abs >= 1_000L) {
            return String.format(Locale.US, "%.1fK", gp / 1_000.0);
        }
        return Long.toString(gp);
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew test --tests com.goodrunetracker.adapter.GpFormatTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/GpFormat.java src/test/java/com/goodrunetracker/adapter/GpFormatTest.java
git commit -m "feat: short gp number formatting"
```

---

## Task 2: `CarriedSnapshots.combine` (TDD)

**Files:**
- Create: `src/main/java/com/goodrunetracker/adapter/CarriedSnapshots.java`
- Test: `src/test/java/com/goodrunetracker/adapter/CarriedSnapshotsTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.goodrunetracker.adapter;

import static org.junit.Assert.*;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class CarriedSnapshotsTest {

    private Map<Integer, Integer> map(int... idQtyPairs) {
        Map<Integer, Integer> m = new HashMap<>();
        for (int i = 0; i < idQtyPairs.length; i += 2) {
            m.put(idQtyPairs[i], idQtyPairs[i + 1]);
        }
        return m;
    }

    @Test
    public void sumsSharedIdsAcrossContainers() {
        // arrows in both quiver (equipment) and inventory
        Map<Integer, Integer> result = CarriedSnapshots.combine(map(884, 1000), map(884, 500));
        assertEquals(Integer.valueOf(1500), result.get(884));
    }

    @Test
    public void mergesDistinctIds() {
        Map<Integer, Integer> result = CarriedSnapshots.combine(map(995, 100), map(4151, 1));
        assertEquals(Integer.valueOf(100), result.get(995));
        assertEquals(Integer.valueOf(1), result.get(4151));
    }

    @Test
    public void dropsNonPositiveQuantities() {
        Map<Integer, Integer> result = CarriedSnapshots.combine(map(995, 0, 560, 5), map(1, -3));
        assertFalse(result.containsKey(995));
        assertFalse(result.containsKey(1));
        assertEquals(Integer.valueOf(5), result.get(560));
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests com.goodrunetracker.adapter.CarriedSnapshotsTest`
Expected: FAIL — `CarriedSnapshots` does not exist.

- [ ] **Step 3: Implement**

```java
package com.goodrunetracker.adapter;

import java.util.HashMap;
import java.util.Map;

/** Combines the inventory and equipment id->quantity snapshots into one carried map. */
public final class CarriedSnapshots {

    private CarriedSnapshots() {
    }

    public static Map<Integer, Integer> combine(Map<Integer, Integer> inventory,
                                                Map<Integer, Integer> equipment) {
        Map<Integer, Integer> out = new HashMap<>();
        addPositive(out, inventory);
        addPositive(out, equipment);
        return out;
    }

    private static void addPositive(Map<Integer, Integer> out, Map<Integer, Integer> src) {
        for (Map.Entry<Integer, Integer> e : src.entrySet()) {
            if (e.getValue() != null && e.getValue() > 0) {
                out.merge(e.getKey(), e.getValue(), Integer::sum);
            }
        }
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew test --tests com.goodrunetracker.adapter.CarriedSnapshotsTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/CarriedSnapshots.java src/test/java/com/goodrunetracker/adapter/CarriedSnapshotsTest.java
git commit -m "feat: combine inventory and equipment snapshots"
```

---

## Task 3: `GoodRuneTrackerConfig`

**Files:**
- Create: `src/main/java/com/goodrunetracker/adapter/runelite/GoodRuneTrackerConfig.java`

RuneLite config interface. Not unit-tested (RuneLite-annotated); verified by the build compiling and the config panel appearing in-client.

- [ ] **Step 1: Create the config interface**

```java
package com.goodrunetracker.adapter.runelite;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("goodrunetracker")
public interface GoodRuneTrackerConfig extends Config {

    @ConfigItem(
        keyName = "bankDetection",
        name = "Auto-end trip at bank",
        description = "End the current trip automatically when the bank interface opens"
    )
    default boolean bankDetection() {
        return true;
    }

    @ConfigItem(
        keyName = "onGroundThreshold",
        name = "On-ground value threshold",
        description = "Hide left-on-ground items below this gp value in the readout"
    )
    default int onGroundThreshold() {
        return 0;
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/runelite/GoodRuneTrackerConfig.java
git commit -m "feat: plugin config (bank detection, on-ground threshold)"
```

---

## Task 4: RuneLite-backed collaborators

**Files:**
- Create: `src/main/java/com/goodrunetracker/adapter/runelite/SystemClock.java`
- Create: `src/main/java/com/goodrunetracker/adapter/runelite/ItemManagerPriceSource.java`
- Create: `src/main/java/com/goodrunetracker/adapter/runelite/ClientCarriedSnapshotSupplier.java`

Thin implementations of the Phase 2a seams. Compile-checked; behavior verified in-client.

- [ ] **Step 1: `SystemClock` implements `Clock`**

```java
package com.goodrunetracker.adapter.runelite;

import com.goodrunetracker.adapter.Clock;
import java.util.UUID;

/** Real clock: wall-clock time and random UUIDs. */
public final class SystemClock implements Clock {

    @Override
    public long nowMillis() {
        return System.currentTimeMillis();
    }

    @Override
    public String newId() {
        return UUID.randomUUID().toString();
    }
}
```

- [ ] **Step 2: `ItemManagerPriceSource` implements `ItemPriceSource`**

```java
package com.goodrunetracker.adapter.runelite;

import com.goodrunetracker.adapter.ItemPriceSource;
import net.runelite.client.game.ItemManager;

/** Live GE prices via RuneLite's ItemManager. */
public final class ItemManagerPriceSource implements ItemPriceSource {

    private final ItemManager itemManager;

    public ItemManagerPriceSource(ItemManager itemManager) {
        this.itemManager = itemManager;
    }

    @Override
    public int price(int itemId) {
        return itemManager.getItemPrice(itemId);
    }
}
```

- [ ] **Step 3: `ClientCarriedSnapshotSupplier` implements `CarriedSnapshotSupplier`**

```java
package com.goodrunetracker.adapter.runelite;

import com.goodrunetracker.adapter.CarriedSnapshots;
import com.goodrunetracker.adapter.CarriedSnapshotSupplier;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;

/** Reads inventory + equipment from the client and combines them into one carried map. */
public final class ClientCarriedSnapshotSupplier implements CarriedSnapshotSupplier {

    private final Client client;

    public ClientCarriedSnapshotSupplier(Client client) {
        this.client = client;
    }

    @Override
    public Map<Integer, Integer> currentCarried() {
        return CarriedSnapshots.combine(
                toMap(client.getItemContainer(InventoryID.INVENTORY)),
                toMap(client.getItemContainer(InventoryID.EQUIPMENT)));
    }

    private static Map<Integer, Integer> toMap(ItemContainer container) {
        Map<Integer, Integer> map = new HashMap<>();
        if (container == null) {
            return map;
        }
        for (Item item : container.getItems()) {
            if (item.getId() > 0 && item.getQuantity() > 0) {
                map.merge(item.getId(), item.getQuantity(), Integer::sum);
            }
        }
        return map;
    }
}
```

**API note for the implementer:** confirm against the client version that `InventoryID.INVENTORY`/`InventoryID.EQUIPMENT` are passed to `client.getItemContainer(...)` (some versions accept the enum directly; if it requires an int, use `.getId()`). Resolve at compile time.

- [ ] **Step 4: Verify compilation**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/runelite/SystemClock.java src/main/java/com/goodrunetracker/adapter/runelite/ItemManagerPriceSource.java src/main/java/com/goodrunetracker/adapter/runelite/ClientCarriedSnapshotSupplier.java
git commit -m "feat: RuneLite-backed clock, price source, and carried snapshot supplier"
```

---

## Task 5: `GoodRuneTrackerPanel`

**Files:**
- Create: `src/main/java/com/goodrunetracker/adapter/runelite/GoodRuneTrackerPanel.java`

Minimal `PluginPanel` implementing `PanelView`. Holds a nullable `TrackingService` (set on login). Buttons call the service; `refresh()` reads `currentSnapshot()`. All Swing mutation happens on the EDT (RuneLite calls `PanelView` methods from the client thread, so marshal with `SwingUtilities.invokeLater`). Verified in-client.

- [ ] **Step 1: Create the panel**

```java
package com.goodrunetracker.adapter.runelite;

import com.goodrunetracker.adapter.GpFormat;
import com.goodrunetracker.adapter.PanelView;
import com.goodrunetracker.adapter.TrackingService;
import com.goodrunetracker.adapter.TripSnapshot;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.Optional;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.PluginPanel;

/** Minimal control panel: start/stop tracking, end/discard trip, live readout, death prompt. */
public final class GoodRuneTrackerPanel extends PluginPanel implements PanelView {

    private TrackingService service;
    private boolean loggedIn;

    private final JButton startStop = new JButton("Start tracking");
    private final JButton endTrip = new JButton("End trip");
    private final JButton discardTrip = new JButton("Discard trip");
    private final JPanel deathPrompt = new JPanel();
    private final JButton keepDeath = new JButton("Keep");
    private final JButton discardDeath = new JButton("Discard");

    private final JLabel status = new JLabel("Log in to start tracking");
    private final JLabel kills = new JLabel("-");
    private final JLabel picked = new JLabel("-");
    private final JLabel ground = new JLabel("-");
    private final JLabel supplies = new JLabel("-");
    private final JLabel xp = new JLabel("-");
    private final JLabel gpPerHour = new JLabel("-");

    public GoodRuneTrackerPanel() {
        setLayout(new BorderLayout());
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        body.add(status);
        body.add(startStop);
        body.add(endTrip);
        body.add(discardTrip);

        JPanel stats = new JPanel(new GridLayout(0, 2));
        stats.add(new JLabel("Kills"));
        stats.add(kills);
        stats.add(new JLabel("Loot picked"));
        stats.add(picked);
        stats.add(new JLabel("On ground"));
        stats.add(ground);
        stats.add(new JLabel("Supplies"));
        stats.add(supplies);
        stats.add(new JLabel("XP"));
        stats.add(xp);
        stats.add(new JLabel("GP/hr"));
        stats.add(gpPerHour);
        body.add(stats);

        deathPrompt.setLayout(new GridLayout(0, 1));
        deathPrompt.add(new JLabel("You died this trip — keep or discard it?"));
        JPanel deathButtons = new JPanel();
        deathButtons.add(keepDeath);
        deathButtons.add(discardDeath);
        deathPrompt.add(deathButtons);
        deathPrompt.setVisible(false);
        body.add(deathPrompt);

        add(body, BorderLayout.NORTH);

        startStop.addActionListener(e -> onStartStop());
        wireButtons();
        renderControls();
    }

    private void wireButtons() {
        endTrip.addActionListener(e -> {
            if (service != null && service.isTracking()) {
                service.onBankOpened(); // ends the current trip and starts the next
            }
        });
        discardTrip.addActionListener(e -> {
            if (service != null && service.isTracking()) {
                service.discardTrip();
            }
        });
        keepDeath.addActionListener(e -> {
            if (service != null) {
                service.resolveDeath(true);
            }
            deathPrompt.setVisible(false);
            renderControls();
        });
        discardDeath.addActionListener(e -> {
            if (service != null) {
                service.resolveDeath(false);
            }
            deathPrompt.setVisible(false);
            renderControls();
        });
    }

    private void onStartStop() {
        if (service == null) {
            return;
        }
        if (service.isTracking()) {
            service.endSession();
        } else {
            service.startSession();
        }
        renderControls();
    }

    /** Called by the plugin on login/logout. */
    public void setService(TrackingService service, boolean loggedIn) {
        this.service = service;
        this.loggedIn = loggedIn;
        SwingUtilities.invokeLater(this::renderAll);
    }

    @Override
    public void refresh() {
        SwingUtilities.invokeLater(this::renderAll);
    }

    @Override
    public void showDeathPrompt() {
        SwingUtilities.invokeLater(() -> {
            deathPrompt.setVisible(true);
            renderControls();
        });
    }

    private void renderAll() {
        renderControls();
        renderStats();
    }

    private void renderControls() {
        boolean tracking = service != null && service.isTracking();
        startStop.setEnabled(loggedIn && service != null);
        startStop.setText(tracking ? "Stop tracking" : "Start tracking");
        endTrip.setEnabled(tracking);
        discardTrip.setEnabled(tracking);
        if (!loggedIn) {
            status.setText("Log in to start tracking");
        } else {
            status.setText(tracking ? "Tracking…" : "Ready");
        }
    }

    private void renderStats() {
        Optional<TripSnapshot> snap = service == null ? Optional.empty() : service.currentSnapshot();
        if (!snap.isPresent()) {
            kills.setText("-");
            picked.setText("-");
            ground.setText("-");
            supplies.setText("-");
            xp.setText("-");
            gpPerHour.setText("-");
            return;
        }
        TripSnapshot s = snap.get();
        kills.setText(Integer.toString(s.kills));
        picked.setText(GpFormat.format(s.pickedGp));
        ground.setText(GpFormat.format(s.groundGp));
        supplies.setText(GpFormat.format(s.suppliesGp));
        xp.setText(GpFormat.format(s.totalXp));
        gpPerHour.setText(GpFormat.format(s.gpPerHour));
    }
}
```

The "End trip" button calls `service.onBankOpened()` (the public "end this trip and start the next" transition); "Discard trip" calls `service.discardTrip()`; the death-prompt buttons call `service.resolveDeath(true/false)`.

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/runelite/GoodRuneTrackerPanel.java
git commit -m "feat: minimal tracking control panel"
```

---

## Task 6: Plugin wiring, descriptor resources, and login lifecycle

**Files:**
- Create: `src/main/java/com/goodrunetracker/adapter/runelite/GoodRuneTrackerPlugin.java`
- Create: `src/main/resources/runelite-plugin.properties`
- Create: `src/main/resources/com/goodrunetracker/adapter/runelite/icon.png` (any 24x24 PNG)
- Create: `src/test/java/com/goodrunetracker/adapter/runelite/GoodRuneTrackerPluginRun.java`

- [ ] **Step 1: Create `runelite-plugin.properties`**

```properties
displayName=Good Rune Tracker
author=Sam
description=Track trips and sessions: loot picked up vs left on the ground, supplies used, XP, and GP/hr.
tags=loot,xp,tracking,session,trip
plugins=com.goodrunetracker.adapter.runelite.GoodRuneTrackerPlugin
```

- [ ] **Step 2: Add a 24x24 icon**

Create `src/main/resources/com/goodrunetracker/adapter/runelite/icon.png` — any simple 24x24 PNG (a solid-color square is fine for now). Generate one, e.g.:

Run:
```bash
python3 - <<'PY'
from pathlib import Path
import struct, zlib
def png(path, size=24, rgb=(120,90,40)):
    raw = b''
    for y in range(size):
        raw += b'\x00' + bytes(rgb) * size
    def chunk(t, d):
        return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t+d) & 0xffffffff)
    ihdr = struct.pack('>IIBBBBB', size, size, 8, 2, 0, 0, 0)
    data = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', ihdr) + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b'')
    Path(path).write_bytes(data)
png('src/main/resources/com/goodrunetracker/adapter/runelite/icon.png')
print('wrote icon')
PY
```

- [ ] **Step 3: Create the plugin**

```java
package com.goodrunetracker.adapter.runelite;

import com.goodrunetracker.adapter.LiveItemValuer;
import com.goodrunetracker.adapter.PotionRegistry;
import com.goodrunetracker.adapter.SessionStore;
import com.goodrunetracker.adapter.TrackingService;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntFunction;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.InventoryID;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.RuneLite;

@PluginDescriptor(
    name = "Good Rune Tracker",
    description = "Track trips and sessions: loot, supplies, XP, GP/hr",
    tags = {"loot", "xp", "tracking", "session", "trip"}
)
public class GoodRuneTrackerPlugin extends Plugin {

    @Inject private Client client;
    @Inject private ItemManager itemManager;
    @Inject private ClientToolbar clientToolbar;
    @Inject private GoodRuneTrackerConfig config;

    private GoodRuneTrackerPanel panel;
    private NavigationButton navButton;
    private TrackingService service;

    @Provides
    GoodRuneTrackerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(GoodRuneTrackerConfig.class);
    }

    @Override
    protected void startUp() {
        panel = new GoodRuneTrackerPanel();
        BufferedImage icon = ImageUtil.loadImageResource(GoodRuneTrackerPlugin.class, "icon.png");
        navButton = NavigationButton.builder()
                .tooltip("Good Rune Tracker")
                .icon(icon)
                .panel(panel)
                .build();
        clientToolbar.addNavigation(navButton);

        if (client.getGameState() == GameState.LOGGED_IN) {
            buildService();
        }
    }

    @Override
    protected void shutDown() {
        if (service != null) {
            service.endSession();
            service = null;
        }
        clientToolbar.removeNavigation(navButton);
        panel = null;
    }

    private void buildService() {
        PotionRegistry potions = new PotionRegistry();
        LiveItemValuer valuer = new LiveItemValuer(new ItemManagerPriceSource(itemManager), potions);
        SessionStore store = new SessionStore(RuneLite.RUNELITE_DIR.toPath().resolve("goodrunetracker"));
        IntFunction<String> names = id -> itemManager.getItemComposition(id).getName();
        service = new TrackingService(
                new SystemClock(),
                new ClientCarriedSnapshotSupplier(client),
                names,
                potions,
                valuer,
                store,
                panel,
                Long.toString(client.getAccountHash()));
        panel.setService(service, true);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGGED_IN) {
            if (service == null) {
                buildService();
            }
        } else if (event.getGameState() == GameState.LOGIN_SCREEN
                || event.getGameState() == GameState.HOPPING) {
            if (service != null) {
                service.endSession();
                service = null;
            }
            panel.setService(null, false);
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (service != null) {
            service.onTick();
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (service == null) {
            return;
        }
        int id = event.getContainerId();
        if (id == InventoryID.INVENTORY.getId() || id == InventoryID.EQUIPMENT.getId()) {
            service.markCarriedDirty();
        }
    }

    @Subscribe
    public void onNpcLootReceived(NpcLootReceived event) {
        if (service == null) {
            return;
        }
        Map<Integer, Integer> drops = new HashMap<>();
        for (ItemStack stack : event.getItems()) {
            drops.merge(stack.getId(), stack.getQuantity(), Integer::sum);
        }
        service.onKill(event.getNpc().getName(), drops);
    }

    @Subscribe
    public void onStatChanged(StatChanged event) {
        if (service != null) {
            service.onXp(event.getSkill().getName(), event.getXp());
        }
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event) {
        if (service != null && config.bankDetection() && event.getGroupId() == InterfaceID.BANK) {
            service.onBankOpened();
        }
    }

    @Subscribe
    public void onActorDeath(ActorDeath event) {
        if (service != null && event.getActor() == client.getLocalPlayer()) {
            service.onLocalPlayerDeath();
        }
    }
}
```

**API notes for the implementer (resolve at compile time against the client version):**
- `InterfaceID.BANK` is the bank widget group id. Newer RuneLite moved interface ids to `net.runelite.api.gameval.InterfaceID`; if `net.runelite.api.widgets.InterfaceID.BANK` does not resolve, use `net.runelite.api.gameval.InterfaceID.BANK`, or the integer `12` with a named constant. Confirm in-client that opening the bank ends the trip.
- `StatChanged.getXp()` returns total experience for the skill; `getSkill().getName()` gives the skill name string. Confirm.
- `NpcLootReceived` is `net.runelite.client.events.NpcLootReceived`; `getItems()` is a `Collection<ItemStack>` (`net.runelite.client.game.ItemStack`). Confirm.
- `client.getAccountHash()` returns a `long` (-1 when logged out). The `onGameStateChanged` LOGGED_IN path runs only when logged in, so the hash is valid there.

- [ ] **Step 4: Create the dev runner (test sources)**

```java
package com.goodrunetracker.adapter.runelite;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Launches a development RuneLite client with this plugin loaded. Run from your IDE. */
public final class GoodRuneTrackerPluginRun {

    public static void main(String[] args) throws Exception {
        ExternalPluginManager.loadBuiltin(GoodRuneTrackerPlugin.class);
        RuneLite.main(args);
    }
}
```

- [ ] **Step 5: Verify the build compiles**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`. All existing unit tests still pass; the new RuneLite classes compile. If an API name fails to resolve, fix per the API notes above (this is expected — resolve against the actual client version) and re-run.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/goodrunetracker/adapter/runelite/GoodRuneTrackerPlugin.java src/main/resources/runelite-plugin.properties src/main/resources/com/goodrunetracker/adapter/runelite/icon.png src/test/java/com/goodrunetracker/adapter/runelite/GoodRuneTrackerPluginRun.java
git commit -m "feat: plugin wiring, descriptor, and login lifecycle"
```

---

## Task 7: In-client verification

**Files:** none (manual verification). This task confirms the wiring works against a live client. It cannot be unit-tested.

- [ ] **Step 1: Launch the dev client**

Run `GoodRuneTrackerPluginRun.main` from your IDE (or configure a Gradle run for it). A RuneLite client window opens with the plugin loaded.

- [ ] **Step 2: Confirm the panel loads**

The "Good Rune Tracker" icon appears in the sidebar; clicking it shows the panel with "Log in to start tracking" and a disabled Start button.

- [ ] **Step 3: Log in and start a session**

Log into an account. The Start button enables. Click "Start tracking" → status shows "Tracking…", End/Discard enable.

- [ ] **Step 4: Kill a monster and verify live tracking**

Kill an NPC that drops loot. Confirm: Kills increments; pick up some loot and leave some on the ground → "Loot picked" and "On ground" update accordingly; GP/hr and XP update. Drink a potion / eat food → "Supplies" increases.

- [ ] **Step 5: Verify bank rollover**

Open a bank. Confirm the trip ends and a new one begins (the readout resets for the new trip; tripNumber advances internally). Confirm a session JSON file now exists under `~/.runelite/goodrunetracker/<accountHash>/`.

- [ ] **Step 6: Verify death prompt**

Die (e.g. in the Duel Arena or a safe death scenario) → the death prompt appears. Click "Discard" → the dead trip is dropped. Repeat and click "Keep" → the trip persists marked died.

- [ ] **Step 7: Verify end session and logout**

Click "Stop tracking" → session saves; confirm the JSON file reflects the trips. Log out mid-session in a separate run → tracking auto-stops and saves.

- [ ] **Step 8: Inspect a saved file**

Open a `~/.runelite/goodrunetracker/<accountHash>/<sessionId>.json` and confirm it has the expected shape: category, trips with kills/dropped/pickedUp/missed/suppliesUsed/xpGained and a `unitPrices` map.

- [ ] **Step 9: Record results**

Note any discrepancies (wrong numbers, missed events, API mismatches). File follow-ups for anything off. If all steps pass, Phase 2b is verified.

---

## Phase 2b done — what comes next

- **Phase 2c:** event-based drop-detection (distinguish dropping an item from consuming it via `ItemSpawned` on the player's tile), removing the looted-then-dropped limitation.
- **Phase 3:** the polished tabbed Now/Sessions/Stats panel, history browsing, edit/recategorize, and the per-trip `FrozenItemValuer` read path (`Function<Trip,ItemValuer>` core overloads + `StoredSession → Session` reader).
