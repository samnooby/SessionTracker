package com.sessiontracker.adapter.runelite;

import static com.sessiontracker.adapter.runelite.Fixtures.COINS;
import static com.sessiontracker.adapter.runelite.Fixtures.SHARK;
import static com.sessiontracker.adapter.runelite.Fixtures.itemName;
import static com.sessiontracker.adapter.runelite.Fixtures.key;
import static com.sessiontracker.adapter.runelite.Swing.assertHasText;
import static com.sessiontracker.adapter.runelite.Swing.assertNoText;
import static com.sessiontracker.adapter.runelite.Swing.button;
import static com.sessiontracker.adapter.runelite.Swing.click;
import static com.sessiontracker.adapter.runelite.Swing.flushEdt;
import static com.sessiontracker.adapter.runelite.Swing.label;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.sessiontracker.adapter.SessionHistory;
import com.sessiontracker.adapter.SessionStore;
import com.sessiontracker.adapter.StoredSession;
import com.sessiontracker.adapter.StoredTrip;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Drives the real plugin through the RuneLite events a play session produces, against a mocked
 * client, and checks what lands on disk and in the panel. This is the "does it work without
 * booting the client" test: login, auto-start, kill, loot, eat, bank, GE, death, logout.
 */
public class SessionTrackerPluginTest {

    private static final int BONES = 526;
    private static final int SAPPHIRE = 1607;
    private static final int LOOTING_BAG_CONTAINER = net.runelite.api.gameval.InventoryID.LOOTING_BAG;
    private static final String ACCOUNT = "42";

    @Inject private SessionTrackerPlugin plugin;

    @Mock private Client client;
    @Mock private ItemManager itemManager;
    @Mock private ClientToolbar clientToolbar;
    @Mock private ClientThread clientThread;
    @Mock private SessionTrackerConfig config;
    @Mock private SkillIconManager skillIconManager;
    private final Gson gson = new Gson();

    @Mock private ItemContainer inventory;
    @Mock private ItemContainer equipment;
    @Mock private ItemContainer lootingBag;
    @Mock private Player localPlayer;

    private Path storeRoot;
    private Item[] inventoryItems = new Item[0];
    private Item[] lootingBagItems = new Item[0];
    private boolean lootingBagSynced; // the client only has the container once the game sends it
    private int hitpointsXp = 1_000;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        storeRoot = Files.createTempDirectory("sessiontracker-plugin-test");

        // Client-thread work runs inline: the test is single-threaded apart from the EDT.
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(clientThread).invoke(any(Runnable.class));

        when(config.autoStartTracking()).thenReturn(true);
        when(config.bankDetection()).thenReturn(true);
        when(config.nameAfterFirstKill()).thenReturn(true);
        when(config.nameAfterFirstGather()).thenReturn(true);
        when(config.showItemIcons()).thenReturn(false);

        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        when(client.getAccountHash()).thenReturn(42L);
        when(client.getLocalPlayer()).thenReturn(localPlayer);
        when(client.getItemContainer(InventoryID.INVENTORY)).thenReturn(inventory);
        when(client.getItemContainer(InventoryID.EQUIPMENT)).thenReturn(equipment);
        when(client.getVarbitValue(anyInt())).thenReturn(0);
        when(client.getItemContainer(anyInt())).thenAnswer(invocation -> {
            Integer containerId = invocation.getArgument(0);
            return containerId == LOOTING_BAG_CONTAINER && lootingBagSynced ? lootingBag : null;
        });
        when(inventory.getItems()).thenAnswer(invocation -> inventoryItems);
        when(equipment.getItems()).thenReturn(new Item[0]);
        when(lootingBag.getItems()).thenAnswer(invocation -> lootingBagItems);
        when(client.getSkillExperience(any(Skill.class)))
                .thenAnswer(invocation -> invocation.getArgument(0) == Skill.HITPOINTS ? hitpointsXp : 0);

        priceItem(SHARK, 800);
        priceItem(COINS, 1);
        priceItem(BONES, 100);
        priceItem(SAPPHIRE, 400);
        priceItem(ItemID.LOOTING_BAG, 0);
        priceItem(ItemID.GEM_BAG, 0);

        // Bind the mocks with the Guice RuneLite already ships, so no extra test library is needed.
        Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(Client.class).toInstance(client);
                bind(ItemManager.class).toInstance(itemManager);
                bind(ClientToolbar.class).toInstance(clientToolbar);
                bind(ClientThread.class).toInstance(clientThread);
                bind(SessionTrackerConfig.class).toInstance(config);
                bind(SkillIconManager.class).toInstance(skillIconManager);
                bind(Gson.class).toInstance(gson);
            }
        }).injectMembers(this);
        plugin.storeRoot = storeRoot;
        plugin.startUp();
    }

    @After
    public void tearDown() throws Exception {
        plugin.shutDown();
        flushEdt();
    }

    // ----- scenarios -----

    @Test
    public void autoStartsOnLoginAndPersistsTheTripWhenTheBankOpens() throws Exception {
        login();
        inventoryItems = items(new Item(SHARK, 5));
        tick(); // inventory is readable now, so the pending auto-start fires here

        kill("Vorkath", new ItemStack(BONES, 1), new ItemStack(COINS, 1_000));
        inventoryBecomes(new Item(SHARK, 5), new Item(COINS, 1_000)); // picked up the coins
        tick();
        inventoryBecomes(new Item(SHARK, 4), new Item(COINS, 1_000)); // ate a shark
        tick();
        gainHitpointsXp(120);
        tick(); // XP is recorded immediately but the panel re-renders on the next tick
        flushEdt();

        // The Now tab reflects the live trip before it is banked.
        SessionTrackerPanel panel = panel();
        label(panel, "Tracking");
        assertHasText(panel, "Vorkath");
        assertHasText(panel, "Hitpoints");
        assertHasText(panel, "120");

        openBank(); // bankDetection is on, so this ends the trip and saves the session

        List<StoredSession> sessions = stored();
        assertEquals(1, sessions.size());
        StoredSession session = sessions.get(0);
        assertEquals(ACCOUNT, session.accountHash);
        assertEquals("Vorkath", session.category);
        assertEquals(1, session.trips.size());

        StoredTrip trip = session.trips.get(0);
        assertEquals(Integer.valueOf(1), trip.kills.get("Vorkath"));
        assertEquals(Integer.valueOf(1_000), trip.pickedUp.get(key(COINS)));
        assertEquals(Integer.valueOf(1), trip.missed.get(key(BONES)));
        assertEquals(Integer.valueOf(1), trip.suppliesUsed.get(key(SHARK)));
        assertEquals(Long.valueOf(120), trip.xpGained.get(Skill.HITPOINTS.getName()));
        assertEquals(Long.valueOf(800), trip.unitPrices.get(key(SHARK)));
        assertFalse(trip.died);

        // Frozen prices value the trip: 1,000 coins picked up minus one 800gp shark.
        SessionHistory history = new SessionHistory(new SessionStore(storeRoot, gson), ACCOUNT, Fixtures::itemName);
        assertEquals(200, history.sessionsNewestFirst().get(0).netProfit);
    }

    @Test
    public void bankingDoesNotCountWithdrawalsOrDepositsAsLootOrSupplies() throws Exception {
        when(config.bankDetection()).thenReturn(false); // keep one trip across the bank visit
        login();
        inventoryItems = items(new Item(SHARK, 5));
        tick();
        kill("Vorkath");

        openBank();
        inventoryBecomes(new Item(SHARK, 20)); // withdrew sharks
        tick();
        closeBank();

        inventoryBecomes(new Item(SHARK, 19)); // ate one after leaving the bank
        tick();
        logout();

        StoredTrip trip = onlyTrip();
        assertNull(trip.gathered.get(key(SHARK)));
        assertEquals(Integer.valueOf(1), trip.suppliesUsed.get(key(SHARK)));
    }

    @Test
    public void grandExchangeCollectionsAreNotLoot() throws Exception {
        login();
        inventoryItems = items(new Item(SHARK, 5));
        tick();
        kill("Vorkath");

        openWidget(InterfaceID.GRAND_EXCHANGE);
        inventoryBecomes(new Item(SHARK, 5), new Item(COINS, 50_000)); // collected a sold offer
        tick();
        closeWidget(InterfaceID.GRAND_EXCHANGE);
        logout();

        StoredTrip trip = onlyTrip();
        assertNull(trip.gathered.get(key(COINS)));
        assertNull(trip.pickedUp.get(key(COINS)));
    }

    @Test
    public void withAutoStartOffTheStartButtonBeginsTracking() throws Exception {
        when(config.autoStartTracking()).thenReturn(false);
        login();
        inventoryItems = items(new Item(SHARK, 5));
        tick();
        kill("Vorkath");
        flushEdt();

        SessionTrackerPanel panel = panel();
        label(panel, "Ready");
        assertTrue(button(panel, "Start tracking").isEnabled());
        assertFalse(button(panel, "End trip").isEnabled());

        click(button(panel, "Start tracking"));
        flushEdt();

        label(panel, "Tracking");
        button(panel, "Stop tracking");
        assertTrue(button(panel, "End trip").isEnabled());

        kill("Vorkath");
        logout();
        assertEquals(Integer.valueOf(1), onlyTrip().kills.get("Vorkath")); // only the kill after Start
    }

    @Test
    public void deathPromptKeepsTheTripWhenAsked() throws Exception {
        login();
        inventoryItems = items(new Item(SHARK, 5));
        tick();
        kill("Vorkath");

        plugin.onActorDeath(new ActorDeath(localPlayer));
        flushEdt();

        SessionTrackerPanel panel = panel();
        assertHasText(panel, "You died this trip");
        assertTrue(stored().isEmpty());

        click(button(panel, "Keep"));
        flushEdt();

        assertNoText(panel, "You died this trip");
        StoredTrip trip = onlyTrip();
        assertTrue(trip.died);
        assertEquals(Integer.valueOf(1), trip.kills.get("Vorkath"));
    }

    @Test
    public void logoutEndsTheSessionAndResetsThePanel() throws Exception {
        login();
        inventoryItems = items(new Item(SHARK, 5));
        tick();
        kill("Vorkath");

        logout();
        flushEdt();

        assertEquals(1, stored().size());
        assertTrue(stored().get(0).endMillis > 0);
        SessionTrackerPanel panel = panel();
        label(panel, "Log in to start tracking");
        assertFalse(button(panel, "Start tracking").isEnabled());
    }

    @Test
    public void emptyTripsAreNotPersisted() throws Exception {
        login();
        inventoryItems = items(new Item(SHARK, 5));
        tick();
        openBank();
        closeBank();
        logout();

        assertTrue(stored().isEmpty());
    }

    @Test
    public void depositingLootIntoTheLootingBagKeepsItAsPickedUp() throws Exception {
        login();
        inventoryItems = items(new Item(SHARK, 5), new Item(ItemID.LOOTING_BAG, 1));
        tick();

        // Kill, pick up the coins, then deposit them. The bag's container syncs for the first
        // time on that deposit, so its contents must not read as gathered.
        kill("Vorkath", new ItemStack(COINS, 1_000));
        inventoryBecomes(new Item(SHARK, 5), new Item(ItemID.LOOTING_BAG, 1), new Item(COINS, 1_000));
        tick();
        lootingBagBecomes(new Item(COINS, 1_000));
        inventoryBecomes(new Item(SHARK, 5), new Item(ItemID.LOOTING_BAG, 1));
        tick();

        // Once synced, a further deposit is a plain net-zero move.
        kill("Vorkath", new ItemStack(COINS, 500));
        inventoryBecomes(new Item(SHARK, 5), new Item(ItemID.LOOTING_BAG, 1), new Item(COINS, 500));
        tick();
        lootingBagBecomes(new Item(COINS, 1_500));
        inventoryBecomes(new Item(SHARK, 5), new Item(ItemID.LOOTING_BAG, 1));
        tick();

        // Eating still counts: the bag doesn't mask real consumption.
        inventoryBecomes(new Item(SHARK, 4), new Item(ItemID.LOOTING_BAG, 1));
        tick();
        logout();

        StoredTrip trip = onlyTrip();
        assertEquals(Integer.valueOf(1_500), trip.pickedUp.get(key(COINS)));
        assertNull(trip.suppliesUsed.get(key(COINS)));
        assertNull(trip.gathered.get(key(COINS)));
        assertEquals(Integer.valueOf(1), trip.suppliesUsed.get(key(SHARK)));
    }

    @Test
    public void fillingAndEmptyingAGemBagIsNeitherASupplyNorAGain() throws Exception {
        login();
        inventoryItems = items(new Item(ItemID.GEM_BAG, 1));
        tick();

        inventoryBecomes(new Item(ItemID.GEM_BAG, 1), new Item(SAPPHIRE, 1)); // mined a gem
        tick();
        clickItemOption(ItemID.GEM_BAG, "Fill");
        inventoryBecomes(new Item(ItemID.GEM_BAG, 1));                        // it went into the bag
        tick();
        clickItemOption(ItemID.GEM_BAG, "Empty");
        inventoryBecomes(new Item(ItemID.GEM_BAG, 1), new Item(SAPPHIRE, 1)); // and back out
        tick();
        logout();

        StoredTrip trip = onlyTrip();
        assertEquals(Integer.valueOf(1), trip.gathered.get(key(SAPPHIRE)));
        assertNull(trip.suppliesUsed.get(key(SAPPHIRE)));
    }

    // ----- event helpers -----

    private void login() {
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        GameStateChanged event = new GameStateChanged();
        event.setGameState(GameState.LOGGED_IN);
        plugin.onGameStateChanged(event);
    }

    private void logout() {
        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        GameStateChanged event = new GameStateChanged();
        event.setGameState(GameState.LOGIN_SCREEN);
        plugin.onGameStateChanged(event);
    }

    private void tick() {
        plugin.onGameTick(new GameTick());
    }

    private static Item[] items(Item... items) {
        return items;
    }

    private void inventoryBecomes(Item... items) {
        inventoryItems = items;
        plugin.onItemContainerChanged(new ItemContainerChanged(InventoryID.INVENTORY.getId(), inventory));
    }

    /** The game sends (or re-sends) the looting bag container with these contents. */
    private void lootingBagBecomes(Item... items) {
        lootingBagItems = items;
        lootingBagSynced = true;
        plugin.onItemContainerChanged(new ItemContainerChanged(LOOTING_BAG_CONTAINER, lootingBag));
    }

    /** The player clicks an item option such as Fill or Empty on an inventory item. */
    private void clickItemOption(int itemId, String option) {
        MenuEntry entry = mock(MenuEntry.class);
        when(entry.isItemOp()).thenReturn(true);
        when(entry.getItemId()).thenReturn(itemId);
        when(entry.getOption()).thenReturn(option);
        plugin.onMenuOptionClicked(new MenuOptionClicked(entry));
    }

    private void kill(String npcName, ItemStack... drops) {
        NPC npc = mock(NPC.class);
        when(npc.getName()).thenReturn(npcName);
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemStack drop : drops) {
            stacks.add(drop);
        }
        plugin.onNpcLootReceived(new NpcLootReceived(npc, stacks));
    }

    private void gainHitpointsXp(int delta) {
        hitpointsXp += delta;
        plugin.onStatChanged(new StatChanged(Skill.HITPOINTS, hitpointsXp, 99, 99));
    }

    private void openBank() {
        openWidget(InterfaceID.BANK);
    }

    private void closeBank() {
        closeWidget(InterfaceID.BANK);
    }

    private void openWidget(int groupId) {
        WidgetLoaded event = new WidgetLoaded();
        event.setGroupId(groupId);
        plugin.onWidgetLoaded(event);
    }

    private void closeWidget(int groupId) {
        plugin.onWidgetClosed(new WidgetClosed(groupId, 0, false));
    }

    // ----- setup / inspection helpers -----

    private void priceItem(int id, int price) {
        ItemComposition composition = mock(ItemComposition.class);
        when(composition.getName()).thenReturn(itemName(id));
        when(itemManager.getItemComposition(id)).thenReturn(composition);
        when(itemManager.getItemPrice(id)).thenReturn(price);
    }

    private List<StoredSession> stored() {
        return new SessionStore(storeRoot, gson).load(ACCOUNT);
    }

    private StoredTrip onlyTrip() {
        List<StoredSession> sessions = stored();
        assertEquals("expected exactly one stored session", 1, sessions.size());
        assertEquals("expected exactly one stored trip", 1, sessions.get(0).trips.size());
        return sessions.get(0).trips.get(0);
    }

    /** The panel the plugin registered on the sidebar. */
    private SessionTrackerPanel panel() {
        ArgumentCaptor<NavigationButton> captor = ArgumentCaptor.forClass(NavigationButton.class);
        verify(clientToolbar).addNavigation(captor.capture());
        return (SessionTrackerPanel) captor.getValue().getPanel();
    }
}
