package com.goodrunetracker.adapter.runelite;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Launches a development RuneLite client with this plugin loaded. Run from your IDE. */
public final class GoodRuneTrackerPluginRun {

    public static void main(String[] args) throws Exception {
        ExternalPluginManager.loadBuiltin(GoodRuneTrackerPlugin.class);
        RuneLite.main(args);
    }
}
