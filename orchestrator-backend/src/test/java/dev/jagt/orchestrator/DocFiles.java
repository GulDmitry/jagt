package dev.jagt.orchestrator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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

  static Map<Path, String> contentsIn(Path directory, String suffix) {
    return filesEnding(directory, suffix).stream()
        .collect(Collectors.toMap(path -> path, DocFiles::read));
  }

  private static List<Path> filesEnding(Path directory, String suffix) {
    try (Stream<Path> files = Files.list(directory)) {
      return files.filter(path -> path.toString().endsWith(suffix)).toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String read(Path file) {
    try {
      return Files.readString(file);
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
