package dev.jagt.orchestrator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

final class DocFiles {

  private DocFiles() {}

  static List<Path> markdownIn(Path directory) {
    try (Stream<Path> files = Files.list(directory)) {
      return files.filter(path -> path.toString().endsWith(".md")).toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static List<Path> specs() {
    try (Stream<Path> capabilities = Files.list(Path.of("../openspec/specs"))) {
      return capabilities
          .map(capability -> capability.resolve("spec.md"))
          .filter(Files::isRegularFile)
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
