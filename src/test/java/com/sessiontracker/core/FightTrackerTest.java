package com.sessiontracker.core;

import static org.junit.Assert.*;
import java.util.OptionalLong;
import org.junit.Test;

public class FightTrackerTest {

    @Test
    public void timesAKillFromTheFirstHitToTheDeath() {
        FightTracker fights = new FightTracker();
        fights.hit(7, 1_000);
        fights.hit(7, 3_400);
        assertEquals(OptionalLong.of(5_000), fights.death(7, 6_000));
        assertEquals(5_000, fights.takeUptime(10_000));
    }

    @Test
    public void aDeathThePlayerNeverHitIsNotTimed() {
        FightTracker fights = new FightTracker();
        assertFalse(fights.death(7, 6_000).isPresent());
        assertEquals(0, fights.takeUptime(10_000));
    }

    @Test
    public void overlappingFightsAreTimedApartButCountedOnceForUptime() {
        FightTracker fights = new FightTracker();
        fights.hit(1, 0);
        fights.hit(2, 2_000);
        assertEquals(OptionalLong.of(4_000), fights.death(1, 4_000));
        assertEquals(OptionalLong.of(4_000), fights.death(2, 6_000));
        // Busy from 0 to 6s, not 4s + 4s.
        assertEquals(6_000, fights.takeUptime(20_000));
    }

    @Test
    public void separateFightsAddTheirUptime() {
        FightTracker fights = new FightTracker();
        fights.hit(1, 0);
        fights.death(1, 3_000);
        fights.hit(2, 10_000);
        fights.death(2, 12_000);
        assertEquals(5_000, fights.takeUptime(20_000));
    }

    @Test
    public void anAbandonedFightIsDroppedAndCountsUpToTheLastHit() {
        FightTracker fights = new FightTracker();
        fights.hit(1, 0);
        fights.hit(1, 2_000);
        fights.expire(2_000 + FightTracker.ABANDON_AFTER_MILLIS);
        assertFalse(fights.death(1, 40_000).isPresent());
        assertEquals(2_000, fights.takeUptime(50_000));
    }

    @Test
    public void aQuietFightUnderTheTimeoutKeepsRunning() {
        FightTracker fights = new FightTracker();
        fights.hit(1, 0);
        fights.expire(FightTracker.ABANDON_AFTER_MILLIS - 1);
        assertEquals(OptionalLong.of(35_000), fights.death(1, 35_000));
    }

    @Test
    public void anNpcLeavingAliveEndsItsFightWithoutAKill() {
        FightTracker fights = new FightTracker();
        fights.hit(1, 0);
        fights.hit(1, 1_200);
        fights.despawn(1);
        assertFalse(fights.death(1, 5_000).isPresent());
        assertEquals(1_200, fights.takeUptime(9_000));
    }

    @Test
    public void takingUptimeMidFightSplitsItAtThatMoment() {
        FightTracker fights = new FightTracker();
        fights.hit(1, 1_000);
        assertEquals(4_000, fights.takeUptime(5_000));
        fights.death(1, 8_000);
        assertEquals(3_000, fights.takeUptime(9_000));
        assertEquals(0, fights.takeUptime(12_000));
    }

    @Test
    public void resetForgetsFightsAndUptime() {
        FightTracker fights = new FightTracker();
        fights.hit(1, 0);
        fights.hit(2, 0);
        fights.death(1, 2_000);
        fights.reset();
        assertFalse(fights.death(2, 3_000).isPresent());
        assertEquals(0, fights.takeUptime(4_000));
    }
}
