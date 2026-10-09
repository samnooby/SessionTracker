package com.sessiontracker.adapter.runelite;

import com.sessiontracker.adapter.CurrentXpSupplier;
import com.sessiontracker.adapter.LiveItemValuer;
import com.sessiontracker.adapter.StashLedger;
import com.sessiontracker.adapter.TripNamingConfig;
import com.sessiontracker.adapter.PotionRegistry;
import com.sessiontracker.adapter.SessionHistory;
import com.sessiontracker.adapter.SessionStore;
import com.sessiontracker.adapter.TrackingService;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntFunction;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@PluginDescriptor(
    name = "Session Tracker",
    description = "Track trips and sessions: loot, supplies, XP, GP/hr",
    tags = {"loot", "xp", "tracking", "session", "trip"},
    internalName = "session-tracker",
    // Sessions used to live in .runelite/sessiontracker; the client moves them into the plugin's
    // data directory the first time getPluginDirectory() is called.
    legacyDataDirectory = "sessiontracker"
)
public class SessionTrackerPlugin extends Plugin {

    /** The game's line for a successful pickpocket, as RuneLite's loot tracker matches it. */
    private static final Pattern PICKPOCKET = Pattern.compile("You pick (the )?.+'s? pocket.*");

    @Inject private Client client;
    @Inject private Gson gson;
    @Inject private ItemManager itemManager;
    @Inject private ClientToolbar clientToolbar;
    @Inject private ClientThread clientThread;
    @Inject private SessionTrackerConfig config;
    @Inject private SkillIconManager skillIconManager;

    private SessionTrackerPanel panel;
    private NavigationButton navButton;
    private TrackingService service;

    /**
     * One-shot: a session should start as soon as the inventory is readable. Consumed by
     * {@link #onGameTick}, never re-armed by the plugin itself, so manually stopping tracking
     * stays stopped for the rest of the login.
     */
    private volatile boolean pendingAutoStart;

    /**
     * Storage containers (looting bag, seed box...) the client has sent us since login. The first
     * sync of each brings its pre-existing contents into carried, which must not read as gathered.
     */
    private final Set<Integer> syncedContainers = new HashSet<>();

    /**
     * What the opaque containers (herb sack, gem bag, coal bag, fish barrel, log basket) appear to
     * have swallowed. They expose no contents, so this is inferred from the game's gather messages
     * and reconciled each tick against what the inventory actually received.
     */
    private final StashLedger stash = new StashLedger();

    /** The inventory as it stood at the end of the last tick, to settle this tick's gathers. */
    private Map<Integer, Integer> lastInventory = new HashMap<>();

    /** A gather message arrived this tick, so the carried snapshot needs rereading. */
    private boolean stashDirty;

    /**
     * Tick the player last clicked a quick-deposit object, or -1. Only arms the deposit: what
     * leaves the inventory counts as banked once the player's deposit animation confirms it.
     */
    private int quickDepositClickTick = -1;

    /**
     * Tick the player last pickpocketed someone, or -1. The server reports pickpocket loot as NPC
     * loot on the same tick, and it isn't a kill.
     */
    private int pickpocketTick = -1;

    /** Storage screens (see {@link StorageScreens}) currently open. */
    private final Set<Integer> openStorage = new HashSet<>();

    /**
     * Where session JSON is stored: the plugin's data directory, resolved on start-up. Package-private
     * so tests can point it at a temp directory before starting the plugin.
     */
    Filepath storeRoot;

    @Provides
    SessionTrackerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(SessionTrackerConfig.class);
    }

    @Override
    protected void startUp() throws IOException {
        if (storeRoot == null) {
            storeRoot = getPluginDirectory();
        }
        ItemIconProvider itemIcons = new ItemManagerIconProvider(itemManager, clientThread, config::showItemIcons);
        panel = new SessionTrackerPanel(clientThread, buildSkillIcons(), itemIcons);
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
        endService();
        clientToolbar.removeNavigation(navButton);
        panel = null;
    }

    /** Ends and saves the running session, if any, and stops tracking until the next login. */
    private void endService() {
        if (service != null) {
            service.endSession();
            service = null;
        }
        pendingAutoStart = false;
    }

    /** Skill-name -> small skill icon, resolved once. Keyed by Skill.getName() to match stored XP keys. */
    private Map<String, Icon> buildSkillIcons() {
        Map<String, Icon> icons = new HashMap<>();
        for (Skill skill : Skill.values()) {
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
        SessionStore store = new SessionStore(storeRoot, gson.newBuilder().setPrettyPrinting().create());
        IntFunction<String> names = id -> itemManager.getItemComposition(id).getName();
        service = new TrackingService(
                new SystemClock(),
                new ClientCarriedSnapshotSupplier(client, stash),
                names,
                potions,
                valuer,
                store,
                panel,
                Long.toString(client.getAccountHash()),
                currentXp(),
                namingConfig());
        SessionHistory history = new SessionHistory(store, Long.toString(client.getAccountHash()), names, potions);
        panel.setService(service, true, history);
        pendingAutoStart = config.autoStartTracking();
        syncedContainers.clear();
        stash.clearAll();
        quickDepositClickTick = -1;
        pickpocketTick = -1;
        openStorage.clear();
        lastInventory = currentInventory();
    }

    /**
     * Start the pending session once the inventory is actually readable. Deliberately deferred to
     * a game tick rather than done at LOGGED_IN: the item containers can still be null at that
     * point, and {@link TrackingService#startSession()} baselines whatever is carried right then.
     * Baselining an empty inventory would make the first real container update look like the whole
     * inventory had just been gathered.
     */
    private void consumePendingAutoStart() {
        if (!pendingAutoStart || service == null) {
            return;
        }
        if (client.getItemContainer(InventoryID.INV) == null) {
            return; // not loaded yet; try again next tick
        }
        pendingAutoStart = false;
        service.startSession();
    }

    /** Current total XP for every real skill, keyed by Skill.getName(). Read on the client thread. */
    private CurrentXpSupplier currentXp() {
        return () -> {
            Map<String, Long> xp = new HashMap<>();
            for (Skill skill : Skill.values()) {
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
        } else if (event.getGameState() == GameState.LOGIN_SCREEN) {
            endService();
            panel.setService(null, false, null);
        }
    }

    /**
     * Closing the client while logged in skips both {@link #shutDown()} and the logout, so the
     * session is ended here or the unbanked trip is lost. The client holds its exit (for up to ten
     * seconds) until the save on the client thread finishes.
     */
    @Subscribe
    public void onClientShutdown(ClientShutdown event) {
        if (service == null) {
            return;
        }
        CompletableFuture<Void> ended = new CompletableFuture<>();
        clientThread.invoke(() -> {
            try {
                endService();
            } finally {
                ended.complete(null);
            }
        });
        event.waitFor(ended);
    }

    /**
     * Turning auto-start on mid-login arms it immediately, so the setting takes effect without
     * needing a relog. Only ever arms it — turning it off never stops a session already running.
     */
    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!"sessiontracker".equals(event.getGroup())
                || !SessionTrackerConfig.AUTO_START_TRACKING.equals(event.getKey())) {
            return;
        }
        if (config.autoStartTracking() && service != null && !service.isTracking()) {
            pendingAutoStart = true;
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        consumePendingAutoStart();
        settleStash();
        if (service != null) {
            service.onTick();
        }
    }

    /**
     * Believe this tick's gather messages, minus anything the inventory actually received (which
     * means the container was too full to take it). Runs before the tracker reads carried, so a
     * gain lands in the trip it happened in.
     */
    private void settleStash() {
        Map<Integer, Integer> inventory = currentInventory();
        stash.settle(lastInventory, inventory);
        lastInventory = inventory;
        if (stashDirty && service != null) {
            service.markCarriedDirty();
            stashDirty = false;
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (service == null) {
            return;
        }
        if (isPickpocket(event)) {
            pickpocketTick = client.getTickCount();
            return;
        }
        if (!config.trackOpenBags()) {
            return;
        }
        for (StashBags.Bag bag : StashBags.all()) {
            if (!carriesOpen(bag)) {
                continue;
            }
            OptionalInt item = bag.match(event.getMessage());
            if (item.isPresent()) {
                stash.gathered(bag.name(), item.getAsInt(), 1);
                stashDirty = true;
                return;
            }
        }
    }

    /** A game message (not a player's chat) saying the player just picked someone's pocket. */
    private static boolean isPickpocket(ChatMessage event) {
        ChatMessageType type = event.getType();
        return (type == ChatMessageType.GAMEMESSAGE || type == ChatMessageType.SPAM
                || type == ChatMessageType.MESBOX)
                && PICKPOCKET.matcher(event.getMessage()).matches();
    }

    /** True if an open form of this container is in the inventory or worn. */
    private boolean carriesOpen(StashBags.Bag bag) {
        return hasOpen(client.getItemContainer(InventoryID.INV), bag)
                || hasOpen(client.getItemContainer(InventoryID.WORN), bag);
    }

    private static boolean hasOpen(ItemContainer container, StashBags.Bag bag) {
        if (container == null) {
            return false;
        }
        for (Item item : container.getItems()) {
            if (bag.isOpen(item.getId())) {
                return true;
            }
        }
        return false;
    }

    private Map<Integer, Integer> currentInventory() {
        Map<Integer, Integer> out = new HashMap<>();
        ItemContainer container = client.getItemContainer(InventoryID.INV);
        if (container == null) {
            return out;
        }
        for (Item item : container.getItems()) {
            if (item.getId() > 0 && item.getQuantity() > 0) {
                out.merge(item.getId(), item.getQuantity(), Integer::sum);
            }
        }
        return out;
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (service == null) {
            return;
        }
        int id = event.getContainerId();
        if (id == InventoryID.INV || id == InventoryID.WORN) {
            service.markCarriedDirty();
        } else if (StoredContainerReader.isStoredContainer(id)) {
            if (syncedContainers.add(id)) {
                // First time we see this container: its contents were already there, not gathered.
                service.onContainerTransfer();
            }
            service.markCarriedDirty();
        }
    }

    /**
     * The drop as the game server reports it, rather than RuneLite's guess from what appeared on
     * the ground. That can include drops that never touch the ground: those that land in the
     * inventory count as picked up, and any that go elsewhere as left behind.
     */
    @Subscribe
    public void onServerNpcLoot(ServerNpcLoot event) {
        if (service == null || client.getTickCount() == pickpocketTick) {
            return;
        }
        Map<Integer, Integer> drops = new HashMap<>();
        for (ItemStack stack : event.getItems()) {
            drops.merge(stack.getId(), stack.getQuantity(), Integer::sum);
        }
        service.onKill(Text.removeTags(event.getComposition().getName()), drops);
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
        if (service != null && event.getGroupId() == InterfaceID.BANKMAIN) {
            // Containers are usually emptied here, and the bank rebaselines anyway, so stop
            // believing anything is inside them rather than carrying a phantom around.
            stash.clearAll();
            service.onBankOpened(config.bankDetection());
        }
        // Collecting bought/sold offers changes the inventory but isn't loot; suppress it while open.
        if (service != null && event.getGroupId() == InterfaceID.GE_OFFERS) {
            service.onGeOpened();
        }
        // Storing isn't using anything up; keep what goes in counted as kept.
        if (service != null && StorageScreens.isStorage(event.getGroupId())) {
            quickDepositClickTick = -1; // a deposit box screen opened instead; it handles itself
            if (openStorage.isEmpty()) {
                service.onStorageOpened();
            }
            openStorage.add(event.getGroupId());
        }
    }

    @Subscribe
    public void onWidgetClosed(WidgetClosed event) {
        if (service != null && event.getGroupId() == InterfaceID.BANKMAIN) {
            service.onBankClosed();
        }
        if (service != null && event.getGroupId() == InterfaceID.GE_OFFERS) {
            service.onGeClosed();
        }
        // Some storage spans two screens (the seed vault); resume once the last one closes.
        if (service != null && openStorage.remove(event.getGroupId()) && openStorage.isEmpty()) {
            service.onStorageClosed();
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (service == null) {
            return;
        }
        if (isObjectInteraction(event.getMenuAction())
                && (QuickDeposits.isDepositObject(event.getId()) || isExtraQuickDeposit(event.getMenuTarget()))) {
            quickDepositClickTick = client.getTickCount();
        } else if (isMoveOrInteraction(event.getMenuAction())) {
            // Walking off or interacting with something else abandons the deposit.
            quickDepositClickTick = -1;
        }
        if ("Drop".equals(event.getMenuOption()) && event.getItemId() > 0) {
            service.markDropped(event.getItemId());
        }
        // Filling or emptying a sack/bag/barrel moves items; the client can't see inside these.
        if (event.isItemOp() && StashContainers.isTransfer(event.getItemId(), event.getMenuOption())) {
            // Whatever we believed was inside is now wrong, and the move itself must not be booked.
            StashBags.byAnyItemId(event.getItemId()).ifPresent(bag -> stash.clear(bag.name()));
            service.onContainerTransfer();
        }
    }

    /**
     * The player handed something over. If they had just clicked a quick deposit, this is it
     * happening: what leaves the inventory now went to the bank.
     */
    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        if (service == null || quickDepositClickTick < 0 || event.getActor() != client.getLocalPlayer()) {
            return;
        }
        if (client.getTickCount() - quickDepositClickTick > QuickDeposits.REACH_TICKS) {
            quickDepositClickTick = -1;
            return;
        }
        if (QuickDeposits.isDepositAnimation(event.getActor().getAnimation())) {
            quickDepositClickTick = -1;
            service.onQuickDeposit();
        }
    }

    /**
     * True if the clicked target (an object, or "item -> object" for Use) is one the player added
     * under Extra quick deposit objects, for anything the built-in list misses.
     */
    private boolean isExtraQuickDeposit(String target) {
        String configured = config.extraQuickDepositObjects();
        if (target == null || configured == null || configured.isEmpty()) {
            return false;
        }
        String plain = Text.removeTags(target);
        int arrow = plain.lastIndexOf("->");
        String object = (arrow >= 0 ? plain.substring(arrow + 2) : plain).trim();
        for (String name : Text.fromCSV(configured)) {
            if (!name.isEmpty() && name.equalsIgnoreCase(object)) {
                return true;
            }
        }
        return false;
    }

    /** An option on a game object, including using an item on one. */
    private static boolean isObjectInteraction(MenuAction action) {
        return action != null && action.name().contains("GAME_OBJECT");
    }

    private static boolean isMoveOrInteraction(MenuAction action) {
        if (action == null) {
            return false;
        }
        return action == MenuAction.WALK || isObjectInteraction(action) || action.name().startsWith("NPC_");
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        if (service != null
                && (RunePouchReader.isRunePouchVarbit(event.getVarbitId())
                    || ChargedItemReader.isChargeVarbit(event.getVarbitId())
                    || PlankSackReader.isPlankSackVarbit(event.getVarbitId()))) {
            service.markCarriedDirty();
        }
    }

    @Subscribe
    public void onActorDeath(ActorDeath event) {
        if (service == null) {
            return;
        }
        if (event.getActor() == client.getLocalPlayer()) {
            service.onLocalPlayerDeath();
        } else if (event.getActor() instanceof NPC) {
            NPC npc = (NPC) event.getActor();
            service.onNpcDeath(npc.getIndex(), npc.getName());
        }
    }

    /** The player's own hits time the fight: the first one starts the clock on that NPC. */
    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event) {
        if (service != null && event.getActor() instanceof NPC && event.getHitsplat().isMine()) {
            service.onNpcHit(((NPC) event.getActor()).getIndex());
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        if (service == null) {
            return;
        }
        NPC npc = event.getNpc();
        if (npc.isDead()) {
            // Normally ActorDeath already ended the fight; this catches a death it didn't report.
            service.onNpcDeath(npc.getIndex(), npc.getName());
        }
        service.onNpcDespawned(npc.getIndex());
    }
}
