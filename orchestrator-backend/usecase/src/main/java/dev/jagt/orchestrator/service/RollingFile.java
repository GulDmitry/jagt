package dev.jagt.orchestrator.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Past {@link #MAX_BYTES} the file rolls into one {@code .1}, replacing the last. */
public final class RollingFile {

    static final long MAX_BYTES = 1024L * 1024;

    private final Path file;

    public RollingFile(Path file) {
        this.file = file;
    }

    public Path path() {
        return file;
    }

    public synchronized void append(String lines) throws IOException {
        Files.writeString(file, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        if (Files.size(file) > MAX_BYTES) {
            Files.move(file, file.resolveSibling(file.getFileName() + ".1"), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
