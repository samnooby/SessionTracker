package com.sessiontracker.adapter;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Reads/writes sessions as one JSON file per session under a per-account directory. */
public final class SessionStore {

    private final Path root;
    private final Gson gson;

    public SessionStore(Path root, Gson gson) {
        this.root = root;
        this.gson = gson;
    }

    public void save(StoredSession session) {
        try {
            Path dir = root.resolve(session.accountHash);
            Files.createDirectories(dir);
            Path file = dir.resolve(session.id + ".json");
            Path tmp = Files.createTempFile(dir, session.id, ".json.tmp");
            Files.write(tmp, gson.toJson(session).getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save session " + session.id, e);
        }
    }

    public List<StoredSession> load(String accountHash) {
        Path dir = root.resolve(accountHash);
        if (!Files.isDirectory(dir)) {
            return new ArrayList<>();
        }
        List<StoredSession> sessions = new ArrayList<>();
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
