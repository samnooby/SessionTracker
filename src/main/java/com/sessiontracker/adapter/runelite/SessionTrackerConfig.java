package com.sessiontracker.adapter.runelite;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("sessiontracker")
public interface SessionTrackerConfig extends Config {

    String AUTO_START_TRACKING = "autoStartTracking";

    @ConfigItem(
        keyName = AUTO_START_TRACKING,
        name = "Auto-start tracking on login",
        description = "Start a session automatically when you log in, instead of clicking "
                + "Start tracking. Stopping tracking manually stays stopped until you log in again."
    )
    default boolean autoStartTracking() {
        return true;
    }

    @ConfigItem(
        keyName = "bankDetection",
        name = "Auto-end trip at bank",
        description = "End the current trip automatically when the bank interface opens"
    )
    default boolean bankDetection() {
        return true;
    }

    @ConfigItem(
        keyName = "nameAfterFirstKill",
        name = "Name trip after first monster killed",
        description = "Auto-name the session's category after the first monster you kill"
    )
    default boolean nameAfterFirstKill() {
        return true;
    }

    @ConfigItem(
        keyName = "nameAfterFirstGather",
        name = "Name trip after first item gathered",
        description = "Auto-name the session's category after the first resource you gather "
                + "(e.g. Oak logs, Tuna). If both naming options are on, whichever you do first wins."
    )
    default boolean nameAfterFirstGather() {
        return true;
    }

    @ConfigItem(
        keyName = "onGroundThreshold",
        name = "On-ground value threshold",
        description = "Hide left-on-ground items below this gp value in the readout"
    )
    default int onGroundThreshold() {
        return 0;
    }

    @ConfigItem(
        keyName = "trackOpenBags",
        name = "Track open storage bags",
        description = "Count what an open herb sack, gem bag, coal bag, fish barrel or log basket "
                + "collects as you gather it, read from the game's own chat messages. Turn this "
                + "off if a game update ever makes the counts look wrong."
    )
    default boolean trackOpenBags() {
        return true;
    }

    @ConfigItem(
        keyName = "showItemIcons",
        name = "Show item icons",
        description = "Show loot and supplies as a grid of item icons with the count in the "
                + "corner, like your inventory, instead of rows of names. Hover an icon for the "
                + "name, count and value."
    )
    default boolean showItemIcons() {
        return true;
    }
}
