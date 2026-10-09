package com.sessiontracker.adapter;

import java.io.IOException;
import java.nio.file.Files;
import net.runelite.client.util.Filepath;

/** Fresh temp directories, handed out as the {@link Filepath} root a {@link SessionStore} writes under. */
public final class TempRoots {

    private TempRoots() {
    }

    public static Filepath create(String prefix) throws IOException {
        return Filepath.Unchecked.getRooted(Files.createTempDirectory(prefix));
    }
}
