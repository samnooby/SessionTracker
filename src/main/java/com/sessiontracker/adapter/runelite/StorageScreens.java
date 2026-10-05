package com.sessiontracker.adapter.runelite;

import java.util.HashSet;
import java.util.Set;
import net.runelite.api.gameval.InterfaceID;

/**
 * Screens that move items between the inventory and somewhere they are kept, without being the
 * bank: deposit boxes, the tool leprechaun, the seed vault, group ironman shared storage and the
 * Chambers of Xeric storage units. What goes in or comes out while one is open is moved, not used
 * or gained.
 */
final class StorageScreens {

    private static final Set<Integer> GROUP_IDS = new HashSet<>();

    static {
        int[] ids = {
            InterfaceID.BANK_DEPOSITBOX,
            InterfaceID.FARMING_TOOLS,
            InterfaceID.SEED_VAULT_DEPOSIT, InterfaceID.SEED_VAULT,
            InterfaceID.SHARED_BANK,
            InterfaceID.RAIDS_STORAGE_PRIVATE, InterfaceID.RAIDS_STORAGE_SHARED,
        };
        for (int id : ids) {
            GROUP_IDS.add(id);
        }
    }

    private StorageScreens() {
    }

    static boolean isStorage(int groupId) {
        return GROUP_IDS.contains(groupId);
    }
}
