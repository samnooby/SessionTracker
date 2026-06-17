package com.sessiontracker.adapter.runelite;

import com.sessiontracker.adapter.CurrentXpSupplier;
import com.sessiontracker.adapter.LiveItemValuer;
import com.sessiontracker.adapter.TripNamingConfig;
import com.sessiontracker.adapter.PotionRegistry;
import com.sessiontracker.adapter.SessionHistory;
import com.sessiontracker.adapter.SessionStore;
import com.sessiontracker.adapter.TrackingService;
import com.google.inject.Provides;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntFunction;
import javax.inject.Inject;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
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
    name = "Session Tracker",
    description = "Track trips and sessions: loot, supplies, XP, GP/hr",
    tags = {"loot", "xp", "tracking", "session", "trip"}
)
public class SessionTrackerPlugin extends Plugin {

    @Inject private Client client;
    @Inject private ItemManager itemManager;
    @Inject private ClientToolbar clientToolbar;
    @Inject private ClientThread clientThread;
    @Inject private SessionTrackerConfig config;
    @Inject private SkillIconManager skillIconManager;

    private SessionTrackerPanel panel;
    private NavigationButton navButton;
    private TrackingService service;

    @Provides
    SessionTrackerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(SessionTrackerConfig.class);
    }

    @Override
    protected void startUp() {
        panel = new SessionTrackerPanel(clientThread, buildSkillIcons());
        BufferedImage icon = ImageUtil.loadImageResource(SessionTrackerPlugin.class, "icon.png");
        navButton = NavigationButton.builder()
                .tooltip("Session Tracker")
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

    /** Skill-name -> small skill icon, resolved once. Keyed by Skill.getName() to match stored XP keys. */
    private Map<String, Icon> buildSkillIcons() {
        Map<String, Icon> icons = new HashMap<>();
        for (Skill skill : Skill.values()) {
            if (skill == Skill.OVERALL) {
                continue;
            }
            try {
                BufferedImage img = skillIconManager.getSkillImage(skill, true);
                if (img != null) {
                    Image scaled = img.getScaledInstance(16, 16, Image.SCALE_SMOOTH);
                    icons.put(skill.getName(), new ImageIcon(scaled));
                }
            } catch (RuntimeException ignored) {
                // A not-yet-released skill may lack an icon resource; skip it.
            }
        }
        return icons;
    }

    private void buildService() {
        PotionRegistry potions = new PotionRegistry();
        LiveItemValuer valuer = new LiveItemValuer(new ItemManagerPriceSource(itemManager), potions);
        SessionStore store = new SessionStore(RuneLite.RUNELITE_DIR.toPath().resolve("sessiontracker"));
        IntFunction<String> names = id -> itemManager.getItemComposition(id).getName();
        service = new TrackingService(
                new SystemClock(),
                new ClientCarriedSnapshotSupplier(client),
                names,
                potions,
                valuer,
                store,
                panel,
                Long.toString(client.getAccountHash()),
                currentXp(),
                namingConfig());
        SessionHistory history = new SessionHistory(store, Long.toString(client.getAccountHash()), names);
        panel.setService(service, true, history);
    }

    /** Current total XP for every real skill, keyed by Skill.getName(). Read on the client thread. */
    private CurrentXpSupplier currentXp() {
        return () -> {
            Map<String, Long> xp = new HashMap<>();
            for (Skill skill : Skill.values()) {
                if (skill == Skill.OVERALL) {
                    continue;
                }
                xp.put(skill.getName(), (long) client.getSkillExperience(skill));
            }
            return xp;
        };
    }

    /** Live view over the auto-naming config settings. */
    private TripNamingConfig namingConfig() {
        return new TripNamingConfig() {
            @Override
            public boolean nameAfterFirstKill() {
                return config.nameAfterFirstKill();
            }

            @Override
            public boolean nameAfterFirstGather() {
                return config.nameAfterFirstGather();
            }
        };
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
            panel.setService(null, false, null);
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
        // Always notify the service so banking inventory changes aren't miscounted; the config
        // only decides whether opening the bank also ends the current trip.
        if (service != null && event.getGroupId() == InterfaceID.BANK) {
            service.onBankOpened(config.bankDetection());
        }
    }

    @Subscribe
    public void onWidgetClosed(WidgetClosed event) {
        if (service != null && event.getGroupId() == InterfaceID.BANK) {
            service.onBankClosed();
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (service != null && "Drop".equals(event.getMenuOption()) && event.getItemId() > 0) {
            service.markDropped(event.getItemId());
        }
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        if (service != null
                && (RunePouchReader.isRunePouchVarbit(event.getVarbitId())
                    || ChargedItemReader.isChargeVarbit(event.getVarbitId()))) {
            service.markCarriedDirty();
        }
    }

    @Subscribe
    public void onActorDeath(ActorDeath event) {
        if (service != null && event.getActor() == client.getLocalPlayer()) {
            service.onLocalPlayerDeath();
        }
    }
}
