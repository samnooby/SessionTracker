package com.sessiontracker.adapter.runelite;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ObjectID;
import org.junit.Test;

public class QuickDepositsTest {

    @Test
    public void recognisesBankDepositBoxesChestsPotsAndTheGotrPool() {
        assertTrue(QuickDeposits.isDepositObject(ObjectID.BANK_DEPOSIT_BOX));
        assertTrue(QuickDeposits.isDepositObject(ObjectID.SARIM_DEPOSIT_BOX));
        assertTrue(QuickDeposits.isDepositObject(ObjectID.CONCH_GROVE_BANK_DEPOSIT_BOX));
        assertTrue(QuickDeposits.isDepositObject(ObjectID.BANK_DEPOSIT_CHEST));
        assertTrue(QuickDeposits.isDepositObject(ObjectID.TOA_POTTERY_BANKDEPOSIT));
        assertTrue(QuickDeposits.isDepositObject(ObjectID.MM_ACTIVE_DEPOSIT_BOX));
        assertTrue(QuickDeposits.isDepositObject(ObjectID.GOTR_DEPOSITCHEST));
    }

    @Test
    public void ignoresDepositsThatDoNotBank() {
        // Trouble Brewing's deposit box and the Motherlode-style sacks take items without banking.
        assertFalse(QuickDeposits.isDepositObject(ObjectID.GAME_BREW_DEPOSIT_BOX));
        assertFalse(QuickDeposits.isDepositObject(ObjectID.BLAST_MINING_SACK_DEPOSIT));
        assertFalse(QuickDeposits.isDepositObject(ObjectID.MM_INACTIVE_DEPOSIT_BOX));
    }

    @Test
    public void onlyTheHandOverAnimationsConfirmADeposit() {
        assertTrue(QuickDeposits.isDepositAnimation(AnimationID.HUMAN_LEVERDOWN));
        assertTrue(QuickDeposits.isDepositAnimation(AnimationID.HUMAN_LEVERDOWN_WALKMERGE));
        assertFalse(QuickDeposits.isDepositAnimation(-1)); // idle
        assertFalse(QuickDeposits.isDepositAnimation(AnimationID.HUMAN_EAT));
    }

    @Test
    public void storageScreensCoverDepositBoxLeprechaunSeedVaultGroupStorageAndCox() {
        assertTrue(StorageScreens.isStorage(InterfaceID.BANK_DEPOSITBOX));
        assertTrue(StorageScreens.isStorage(InterfaceID.FARMING_TOOLS));
        assertTrue(StorageScreens.isStorage(InterfaceID.SEED_VAULT));
        assertTrue(StorageScreens.isStorage(InterfaceID.SEED_VAULT_DEPOSIT));
        assertTrue(StorageScreens.isStorage(InterfaceID.SHARED_BANK));
        assertTrue(StorageScreens.isStorage(InterfaceID.RAIDS_STORAGE_PRIVATE));
        assertTrue(StorageScreens.isStorage(InterfaceID.RAIDS_STORAGE_SHARED));
        // The bank and GE have their own handling (the bank can end the trip).
        assertFalse(StorageScreens.isStorage(InterfaceID.BANKMAIN));
        assertFalse(StorageScreens.isStorage(InterfaceID.GE_OFFERS));
    }
}
