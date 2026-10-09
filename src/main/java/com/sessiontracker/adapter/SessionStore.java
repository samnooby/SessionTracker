package com.sessiontracker.adapter;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.runelite.client.util.Filepath;

/**
 * Reads/writes sessions as one JSON file per session under a per-account directory.
 *
 * <p>Each account's sessions are read from disk once and then served from memory; saves and
 * deletes write through to both. The panels query history often (every expand, tab switch and
 * detail view), some of it on the Swing and game threads, so this keeps disk I/O and JSON
 * parsing off those threads after the first load. This store is the only writer of its files;
 * edits made behind its back on disk are not seen until the next client start.
 *
 * <p>All file access goes through RuneLite's {@link Filepath}, which keeps every path inside
 * {@code root} (the plugin's data directory in the client).
 */
public final class SessionStore {

    private final Filepath root;
    private final Gson gson;
    private final Map<String, List<StoredSession>> cache = new HashMap<>();

    public SessionStore(Filepath root, Gson gson) {
        this.root = root;
        this.gson = gson;
    }

    public synchronized void save(StoredSession session) {
        String json = gson.toJson(session);
        try {
            Filepath dir = root.joinSegment(session.accountHash);
            dir.createDirectories();
            Filepath file = dir.joinSegment(session.id + ".json");
            Filepath tmp = dir.createTempFile(session.id, ".json.tmp");
            tmp.write(json);
            try {
                tmp.moveTo(file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                tmp.moveTo(file, StandardCopyOption.REPLACE_EXISTING);
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
        try {
            root.joinSegment(accountHash).joinSegment(sessionId + ".json").deleteIfExists();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete session " + sessionId, e);
        }
        cached(accountHash).removeIf(s -> s.id.equals(sessionId));
    }

    /** The account's sessions, in no particular order. The list is the caller's to reorder. */
    public synchronized List<StoredSession> load(String accountHash) {
        return new ArrayList<>(cached(accountHash));
    }

    /**
     * An independent copy of one session, or null if there is none: safe to take over as the
     * live active session and mutate on the game thread while the panels keep reading the cache.
     */
    public synchronized StoredSession copyOf(String accountHash, String sessionId) {
        for (StoredSession s : cached(accountHash)) {
            if (s.id.equals(sessionId)) {
                return gson.fromJson(gson.toJson(s), StoredSession.class);
            }
        }
        return null;
    }

    private List<StoredSession> cached(String accountHash) {
        return cache.computeIfAbsent(accountHash, this::readFromDisk);
    }

    private List<StoredSession> readFromDisk(String accountHash) {
        Filepath dir = root.joinSegment(accountHash);
        List<StoredSession> sessions = new ArrayList<>();
        if (!dir.isDirectory()) {
            return sessions;
        }
        try {
            List<Filepath> files;
            try (Stream<Filepath> stream = dir.walk(1)) {
                files = stream.filter(p -> p.isFile() && p.getFileName().endsWith(".json"))
                        .collect(Collectors.toList());
            }
            for (Filepath file : files) {
                try (Reader reader = file.openBufferedReader()) {
                    StoredSession session = gson.fromJson(reader, StoredSession.class);
                    if (session != null) {
                        sessions.add(session);
                    }
                } catch (IOException | JsonParseException e) {
                    // Skip a corrupt or unreadable session file rather than failing the whole load.
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load sessions for " + accountHash, e);
        }
        return sessions;
    }
}
