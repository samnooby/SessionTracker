package com.goodrunetracker.adapter.runelite;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("goodrunetracker")
public interface GoodRuneTrackerConfig extends Config {

    @ConfigItem(
        keyName = "bankDetection",
        name = "Auto-end trip at bank",
        description = "End the current trip automatically when the bank interface opens"
    )
    default boolean bankDetection() {
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
}
