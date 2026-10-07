package dev.jagt.orchestrator.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FileStamps {

    private FileStamps() {
    }

    /** When {@code file} last changed, in epoch millis; 0 where it cannot be read, which no real stamp is. */
    public static long modified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException unreadable) {
            return 0;
        }
    }
}
