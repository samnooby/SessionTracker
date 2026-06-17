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
    private final RunePouchReader pouch;
    private final ChargedItemReader charged;

    public ClientCarriedSnapshotSupplier(Client client) {
        this.client = client;
        this.pouch = new RunePouchReader(client);
        this.charged = new ChargedItemReader(client);
    }

    @Override
    public Map<Integer, Integer> currentCarried() {
        return CarriedSnapshots.combine(
                toMap(client.getItemContainer(InventoryID.INVENTORY)),
                toMap(client.getItemContainer(InventoryID.EQUIPMENT)),
                pouch.contents(),
                charged.contents());
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
