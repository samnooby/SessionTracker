package com.sessiontracker.adapter.runelite;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("sessiontracker")
public interface SessionTrackerConfig extends Config {

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
        keyName = "showItemIcons",
        name = "Show item icons",
        description = "Show item icons beside loot/supply rows in the Sessions trip detail"
    )
    default boolean showItemIcons() {
        return true;
    }
}
