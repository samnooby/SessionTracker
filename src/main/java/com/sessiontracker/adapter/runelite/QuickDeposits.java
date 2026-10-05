package com.sessiontracker.adapter.runelite;

import java.util.HashSet;
import java.util.Set;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.ObjectID;

/**
 * Objects that bank what the player hands them without the bank interface opening: every bank
 * deposit box and chest (an item used on one goes straight in), the Ape Atoll and Tombs of Amascut
 * deposit pots, and Guardians of the Rift's deposit pool (Deposit-runes). Clicking one only says a
 * deposit may follow; the player's hand-it-over animation says it actually happened.
 */
final class QuickDeposits {

    private static final Set<Integer> OBJECT_IDS = new HashSet<>();

    static {
        int[] ids = {
            ObjectID.BANK_DEPOSIT_BOX, ObjectID.BANK_DEPOSIT_BOX_2, ObjectID.BANK_DEPOSIT_CHEST,
            ObjectID.KR_BANK_DEPOSIT_BOX, ObjectID.SARIM_DEPOSIT_BOX, ObjectID.DIARY_GUILD_DEPOSIT_BOX,
            ObjectID.SWAN_BANK_DEPOSIT_BOX, ObjectID.BURGH_BANK_DEPOSIT_BOX, ObjectID.AHOY_BANK_DEPOSIT_BOX,
            ObjectID.WINT_DEPOSIT_BOX, ObjectID.TZHAAR_DEPOSIT_BOX, ObjectID.CORSCURS_BANK_DEPOSIT_BOX,
            ObjectID.TOB_SURFACE_DEPOSIT_BOX, ObjectID.BRIMSTONE_DEPOSITBOX, ObjectID.GAUNTLET_DEPOSIT_BOX,
            ObjectID.DARKM_DEPOSIT_BOX, ObjectID.CLAN_MEDIEVAL_DEPOSIT_BOX,
            ObjectID.STORAGE_POSTBOX01_TALKASTI01, ObjectID.CONCH_GROVE_BANK_DEPOSIT_BOX,
            ObjectID.MM_ACTIVE_DEPOSIT_BOX, ObjectID.TOA_POTTERY_BANKDEPOSIT,
            ObjectID.GOTR_DEPOSITCHEST,
        };
        for (int id : ids) {
            OBJECT_IDS.add(id);
        }
    }

    /**
     * Give up on a clicked deposit the player never reached after this many ticks (a long walk
     * across the GOTR arena is well inside it).
     */
    static final int REACH_TICKS = 30;

    private QuickDeposits() {
    }

    static boolean isDepositObject(int objectId) {
        return OBJECT_IDS.contains(objectId);
    }

    /** The animation the player plays when handing items over: a reach, standing or on the move. */
    static boolean isDepositAnimation(int animationId) {
        return animationId == AnimationID.HUMAN_LEVERDOWN
                || animationId == AnimationID.HUMAN_LEVERDOWN_WALKMERGE;
    }
}
