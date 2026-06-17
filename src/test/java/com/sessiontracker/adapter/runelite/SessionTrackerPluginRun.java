package com.sessiontracker.adapter.runelite;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Launches a development RuneLite client with this plugin loaded. Run from your IDE. */
public final class SessionTrackerPluginRun {

    public static void main(String[] args) throws Exception {
        ExternalPluginManager.loadBuiltin(SessionTrackerPlugin.class);
        RuneLite.main(args);
    }
}
