package com.sessiontracker.adapter.runelite;

import com.sessiontracker.adapter.CarriedSnapshots;
import com.sessiontracker.adapter.CarriedSnapshotSupplier;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;

/**
 * Reads everything the player is carrying from the client and combines it into one carried map:
 * inventory, equipment, rune pouch, charged weapons, the readable storage containers (looting
 * bag, seed box, ...) and the plank sack.
 */
public final class ClientCarriedSnapshotSupplier implements CarriedSnapshotSupplier {

    private final Client client;
    private final RunePouchReader pouch;
    private final ChargedItemReader charged;
    private final StoredContainerReader stored;
    private final PlankSackReader planks;

    public ClientCarriedSnapshotSupplier(Client client) {
        this.client = client;
        this.pouch = new RunePouchReader(client);
        this.charged = new ChargedItemReader(client);
        this.stored = new StoredContainerReader(client);
        this.planks = new PlankSackReader(client);
    }

    @Override
    public Map<Integer, Integer> currentCarried() {
        return CarriedSnapshots.combine(
                toMap(client.getItemContainer(InventoryID.INVENTORY)),
                toMap(client.getItemContainer(InventoryID.EQUIPMENT)),
                pouch.contents(),
                charged.contents(),
                stored.contents(),
                planks.contents());
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
