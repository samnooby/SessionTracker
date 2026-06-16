package com.goodrunetracker.adapter.runelite;

import com.goodrunetracker.adapter.RunePouch;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.Varbits;

/** Reads the rune pouch's slot varbits and resolves rune item ids via the game cache enum. */
public final class RunePouchReader {

    // The rune pouch's per-slot type varbit holds an index; this cache enum maps it to a rune
    // item id (the same enum RuneLite's own runepouch plugin reads).
    private static final int RUNE_POUCH_RUNE_ENUM = 982;

    private static final int[] TYPE_VARBITS = {
        Varbits.RUNE_POUCH_RUNE1, Varbits.RUNE_POUCH_RUNE2,
        Varbits.RUNE_POUCH_RUNE3, Varbits.RUNE_POUCH_RUNE4,
    };
    private static final int[] AMOUNT_VARBITS = {
        Varbits.RUNE_POUCH_AMOUNT1, Varbits.RUNE_POUCH_AMOUNT2,
        Varbits.RUNE_POUCH_AMOUNT3, Varbits.RUNE_POUCH_AMOUNT4,
    };

    private final Client client;

    public RunePouchReader(Client client) {
        this.client = client;
    }

    /** Current rune-pouch contents as runeItemId -&gt; quantity (empty if no pouch / empty pouch). */
    public Map<Integer, Integer> contents() {
        int[] types = new int[TYPE_VARBITS.length];
        int[] amounts = new int[AMOUNT_VARBITS.length];
        for (int i = 0; i < types.length; i++) {
            types[i] = client.getVarbitValue(TYPE_VARBITS[i]);
            amounts[i] = client.getVarbitValue(AMOUNT_VARBITS[i]);
        }
        return RunePouch.contents(types, amounts, this::itemIdForType);
    }

    private int itemIdForType(int typeValue) {
        if (typeValue <= 0) {
            return 0;
        }
        EnumComposition e = client.getEnum(RUNE_POUCH_RUNE_ENUM);
        return e == null ? 0 : e.getIntValue(typeValue);
    }

    /** True if {@code varbitId} is one of the rune-pouch slot varbits (type or amount). */
    public static boolean isRunePouchVarbit(int varbitId) {
        for (int v : TYPE_VARBITS) {
            if (v == varbitId) {
                return true;
            }
        }
        for (int v : AMOUNT_VARBITS) {
            if (v == varbitId) {
                return true;
            }
        }
        return false;
    }
}
