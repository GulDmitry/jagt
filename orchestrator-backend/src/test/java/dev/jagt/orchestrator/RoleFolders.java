package dev.jagt.orchestrator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public final class RoleFolders {

  private RoleFolders() {}

  public static List<String> ids() {
    try (Stream<Path> folders = Files.list(Path.of("..", "roles"))) {
      return folders
          .filter(Files::isDirectory)
          .filter(folder -> !folder.getFileName().toString().equals("session"))
          .map(folder -> folder.getFileName().toString())
          .toList();
    } catch (IOException unread) {
      throw new UncheckedIOException(unread);
    }
  }
}
