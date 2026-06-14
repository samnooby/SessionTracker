package com.goodrunetracker.adapter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Reads/writes sessions as one JSON file per session under a per-account directory. */
public final class SessionStore {

    private final Path root;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public SessionStore(Path root) {
        this.root = root;
    }

    public void save(StoredSession session) {
        try {
            Path dir = root.resolve(session.accountHash);
            Files.createDirectories(dir);
            Path file = dir.resolve(session.id + ".json");
            Files.write(file, gson.toJson(session).getBytes(StandardCharsets.UTF_8));
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
                String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                sessions.add(gson.fromJson(json, StoredSession.class));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load sessions for " + accountHash, e);
        }
        return sessions;
    }
}
