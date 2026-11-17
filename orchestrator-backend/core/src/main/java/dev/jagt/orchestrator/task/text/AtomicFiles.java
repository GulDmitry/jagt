package dev.jagt.orchestrator.task.text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class AtomicFiles {

  private AtomicFiles() {}

  public static void write(Path file, byte[] content) throws IOException {
    Path parent = Files.createDirectories(file.toAbsolutePath().getParent());
    Path temp = Files.createTempFile(parent, ".atomic", ".tmp");
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
