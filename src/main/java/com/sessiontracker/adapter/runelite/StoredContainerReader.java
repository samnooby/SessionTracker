package com.sessiontracker.adapter.runelite;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;

/**
 * Reads the storage containers the client exposes as real item containers and folds their
 * contents into carried. Moving an item into one of these is then a net-zero change (inventory
 * down, container up), and using an item out of one is a genuine decrease.
 *
 * <p>These containers are only sent to the client once the game has a reason to (opening,
 * depositing into, or checking them), so until then they read as empty. The plugin handles the
 * first sync of each container by rebaselining that tick rather than treating the contents that
 * appear as gathered.
 */
public final class StoredContainerReader {

    private static final int[] CONTAINER_IDS = {
        InventoryID.LOOTING_BAG,
        InventoryID.SEED_BOX,
        InventoryID.FORESTRY_KIT,
        InventoryID.HUNTSMANS_KIT,
        InventoryID.TACKLE_BOX,
    };

    private final Client client;

    public StoredContainerReader(Client client) {
        this.client = client;
    }

    /** Combined contents of every readable storage container as itemId -&gt; quantity. */
    public Map<Integer, Integer> contents() {
        Map<Integer, Integer> out = new HashMap<>();
        for (int id : CONTAINER_IDS) {
            ItemContainer container = client.getItemContainer(id);
            if (container == null) {
                continue;
            }
            for (Item item : container.getItems()) {
                if (item.getId() > 0 && item.getQuantity() > 0) {
                    out.merge(item.getId(), item.getQuantity(), Integer::sum);
                }
            }
        }
        return out;
    }

    /** True if {@code containerId} is one of the storage containers folded into carried. */
    public static boolean isStoredContainer(int containerId) {
        for (int id : CONTAINER_IDS) {
            if (id == containerId) {
                return true;
            }
        }
        return false;
    }
}
