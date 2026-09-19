package com.sessiontracker.adapter;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads/writes sessions as one JSON file per session under a per-account directory.
 *
 * <p>Each account's sessions are read from disk once and then served from memory; saves and
 * deletes write through to both. The panels query history often (every expand, tab switch and
 * detail view), some of it on the Swing and game threads, so this keeps disk I/O and JSON
 * parsing off those threads after the first load. This store is the only writer of its files;
 * edits made behind its back on disk are not seen until the next client start.
 */
public final class SessionStore {

    private final Path root;
    private final Gson gson;
    private final Map<String, List<StoredSession>> cache = new HashMap<>();

    public SessionStore(Path root, Gson gson) {
        this.root = root;
        this.gson = gson;
    }

    public synchronized void save(StoredSession session) {
        String json = gson.toJson(session);
        try {
            Path dir = root.resolve(session.accountHash);
            Files.createDirectories(dir);
            Path file = dir.resolve(session.id + ".json");
            Path tmp = Files.createTempFile(dir, session.id, ".json.tmp");
            Files.write(tmp, json.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save session " + session.id, e);
        }
        // Cache a copy of what was written, not the caller's object: the tracker keeps
        // mutating its active session on the game thread while the panels read history on
        // the Swing thread, and they must not share a trip list.
        List<StoredSession> sessions = cached(session.accountHash);
        sessions.removeIf(s -> s.id.equals(session.id));
        sessions.add(gson.fromJson(json, StoredSession.class));
    }

    public synchronized void delete(String accountHash, String sessionId) {
        Path file = root.resolve(accountHash).resolve(sessionId + ".json");
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete session " + sessionId, e);
        }
        cached(accountHash).removeIf(s -> s.id.equals(sessionId));
    }

    /** The account's sessions, in no particular order. The list is the caller's to reorder. */
    public synchronized List<StoredSession> load(String accountHash) {
        return new ArrayList<>(cached(accountHash));
    }

    private List<StoredSession> cached(String accountHash) {
        return cache.computeIfAbsent(accountHash, this::readFromDisk);
    }

    private List<StoredSession> readFromDisk(String accountHash) {
        Path dir = root.resolve(accountHash);
        List<StoredSession> sessions = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return sessions;
        }
        try {
            List<Path> files;
            try (Stream<Path> stream = Files.list(dir)) {
                files = stream.filter(p -> p.toString().endsWith(".json"))
                        .collect(Collectors.toList());
            }
            for (Path file : files) {
                try {
                    String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                    StoredSession session = gson.fromJson(json, StoredSession.class);
                    if (session != null) {
                        sessions.add(session);
                    }
                } catch (IOException | com.google.gson.JsonSyntaxException e) {
                    // Skip a corrupt or unreadable session file rather than failing the whole load.
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load sessions for " + accountHash, e);
        }
        return sessions;
    }
}
