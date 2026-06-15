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
import net.runelite.api.InventoryID;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
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

@PluginDescriptor(
    name = "Good Rune Tracker",
    description = "Track trips and sessions: loot, supplies, XP, GP/hr",
    tags = {"loot", "xp", "tracking", "session", "trip"}
)
public class GoodRuneTrackerPlugin extends Plugin {

    @Inject private Client client;
    @Inject private ItemManager itemManager;
    @Inject private ClientToolbar clientToolbar;
    @Inject private ClientThread clientThread;
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
        panel = new GoodRuneTrackerPanel(clientThread);
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
