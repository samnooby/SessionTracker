package com.sessiontracker.adapter.runelite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Varbits;
import net.runelite.api.gameval.VarbitID;
import org.junit.Before;
import org.junit.Test;

/** The client-facing carried reader: inventory + equipment + rune pouch + charged items. */
public class ClientCarriedSnapshotSupplierTest {

    private static final int SHARK = 385;
    private static final int COINS = 995;
    private static final int FIRE_RUNE = 554;
    private static final int ZULRAH_SCALE = 12934;

    private final Client client = mock(Client.class);
    private final ItemContainer inventory = mock(ItemContainer.class);
    private final ItemContainer equipment = mock(ItemContainer.class);

    @Before
    public void setUp() {
        when(client.getItemContainer(InventoryID.INVENTORY)).thenReturn(inventory);
        when(client.getItemContainer(InventoryID.EQUIPMENT)).thenReturn(equipment);
        when(inventory.getItems()).thenReturn(new Item[0]);
        when(equipment.getItems()).thenReturn(new Item[0]);
        when(client.getVarbitValue(anyInt())).thenReturn(0);
    }

    @Test
    public void combinesInventoryAndEquipmentAndSkipsEmptySlots() {
        when(inventory.getItems()).thenReturn(new Item[] {
            new Item(SHARK, 5), new Item(-1, 0), new Item(COINS, 1_000), new Item(SHARK, 2),
        });
        when(equipment.getItems()).thenReturn(new Item[] { new Item(SHARK, 1) });

        Map<Integer, Integer> carried = new ClientCarriedSnapshotSupplier(client).currentCarried();

        assertEquals(Integer.valueOf(8), carried.get(SHARK));
        assertEquals(Integer.valueOf(1_000), carried.get(COINS));
        assertEquals(2, carried.size());
    }

    @Test
    public void missingContainersReadAsEmpty() {
        when(client.getItemContainer(InventoryID.INVENTORY)).thenReturn(null);
        when(client.getItemContainer(InventoryID.EQUIPMENT)).thenReturn(null);

        assertTrue(new ClientCarriedSnapshotSupplier(client).currentCarried().isEmpty());
    }

    @Test
    public void runePouchContentsCountAsCarried() {
        EnumComposition runeEnum = mock(EnumComposition.class);
        when(runeEnum.getIntValue(3)).thenReturn(FIRE_RUNE);
        when(client.getEnum(982)).thenReturn(runeEnum);
        when(client.getVarbitValue(Varbits.RUNE_POUCH_RUNE1)).thenReturn(3);
        when(client.getVarbitValue(Varbits.RUNE_POUCH_AMOUNT1)).thenReturn(500);

        Map<Integer, Integer> carried = new ClientCarriedSnapshotSupplier(client).currentCarried();

        assertEquals(Integer.valueOf(500), carried.get(FIRE_RUNE));
    }

    @Test
    public void chargedWeaponsCountTheirScales() {
        when(client.getVarbitValue(VarbitID.CHARGES_TOXIC_BLOWPIPE_QUANTITY)).thenReturn(150);
        when(client.getVarbitValue(VarbitID.CHARGES_SERPENTINE_HELM_QUANTITY)).thenReturn(50);

        Map<Integer, Integer> carried = new ClientCarriedSnapshotSupplier(client).currentCarried();

        assertEquals(Integer.valueOf(200), carried.get(ZULRAH_SCALE));
    }
}
