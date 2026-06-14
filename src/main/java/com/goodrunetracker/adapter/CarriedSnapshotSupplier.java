package com.goodrunetracker.adapter;

import java.util.Map;

/** Returns the current combined inventory+equipment as raw itemId -> quantity. */
public interface CarriedSnapshotSupplier {
    Map<Integer, Integer> currentCarried();
}
