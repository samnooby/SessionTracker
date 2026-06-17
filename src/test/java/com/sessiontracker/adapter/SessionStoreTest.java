package com.sessiontracker.adapter;

import static org.junit.Assert.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.Test;

public class SessionStoreTest {

    private StoredSession sampleSession(String id, String accountHash) {
        StoredTrip trip = new StoredTrip();
        trip.id = "trip-1";
        trip.startMillis = 0;
        trip.endMillis = 60_000;
        trip.died = false;
        trip.kills = new HashMap<>();
        trip.kills.put("Demonic gorilla", 2);
        trip.dropped = new HashMap<>();
        trip.pickedUp = new HashMap<>();
        trip.missed = new HashMap<>();
        trip.suppliesUsed = new HashMap<>();
        trip.xpGained = new HashMap<>();
        trip.unitPrices = new HashMap<>();
        trip.dropped.put("item:560", 100);

        StoredSession session = new StoredSession();
        session.id = id;
        session.accountHash = accountHash;
        session.category = "Demonic Gorillas";
        session.name = "evening";
        session.startMillis = 0;
        session.endMillis = 60_000;
        session.trips = new ArrayList<>();
        session.trips.add(trip);
        return session;
    }

    @Test
    public void savesAndLoadsASessionPerAccount() throws Exception {
        Path root = Files.createTempDirectory("grt-store");
        SessionStore store = new SessionStore(root);

        store.save(sampleSession("s1", "acct-A"));

        List<StoredSession> loaded = store.load("acct-A");
        assertEquals(1, loaded.size());
        StoredSession s = loaded.get(0);
        assertEquals("s1", s.id);
        assertEquals("Demonic Gorillas", s.category);
        assertEquals(1, s.trips.size());
        assertEquals(Integer.valueOf(100), s.trips.get(0).dropped.get("item:560"));
    }

    @Test
    public void loadIsolatesByAccount() throws Exception {
        Path root = Files.createTempDirectory("grt-store");
        SessionStore store = new SessionStore(root);
        store.save(sampleSession("s1", "acct-A"));
        store.save(sampleSession("s2", "acct-B"));

        assertEquals(1, store.load("acct-A").size());
        assertEquals(1, store.load("acct-B").size());
    }

    @Test
    public void loadOfUnknownAccountIsEmpty() throws Exception {
        Path root = Files.createTempDirectory("grt-store");
        SessionStore store = new SessionStore(root);
        assertTrue(store.load("nobody").isEmpty());
    }

    @Test
    public void loadSkipsCorruptFilesAndReturnsTheValidOnes() throws Exception {
        java.nio.file.Path root = java.nio.file.Files.createTempDirectory("grt-store");
        SessionStore store = new SessionStore(root);
        store.save(sampleSession("good", "acct-A"));

        // Write a corrupt JSON file alongside the good one in the same account dir.
        java.nio.file.Path dir = root.resolve("acct-A");
        java.nio.file.Files.write(dir.resolve("broken.json"),
                "{ this is not valid json".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        java.util.List<StoredSession> loaded = store.load("acct-A");
        assertEquals(1, loaded.size());
        assertEquals("good", loaded.get(0).id);
    }

    @Test
    public void loadIgnoresNonJsonFiles() throws Exception {
        java.nio.file.Path root = java.nio.file.Files.createTempDirectory("grt-store");
        SessionStore store = new SessionStore(root);
        store.save(sampleSession("good", "acct-A"));
        java.nio.file.Path dir = root.resolve("acct-A");
        java.nio.file.Files.write(dir.resolve("notes.txt"),
                "hello".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertEquals(1, store.load("acct-A").size());
    }
}
