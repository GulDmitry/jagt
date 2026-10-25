package dev.jagt.orchestrator.task;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class AtomicFiles {

    private AtomicFiles() {
    }

    public static void write(Path file, byte[] content) throws IOException {
        Path temp = Files.createTempFile(file.toAbsolutePath().getParent(), ".atomic", ".tmp");
        try {
            Files.write(temp, content);
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException failed) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException cleanup) {
                failed.addSuppressed(cleanup);
            }
            throw failed;
        }
    }
}
