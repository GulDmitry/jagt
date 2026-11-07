package dev.jagt.orchestrator.eval;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

final class RoleCases {

  private static final String ROLE = "eval.role";

  private RoleCases() {}

  static <T> List<T> of(String role, Function<String, T> parse) {
    try (Stream<Path> files =
        Files.list(Path.of("..", "roles", System.getProperty(ROLE, role), "eval"))) {
      return files
          .filter(file -> file.getFileName().toString().endsWith(".yml"))
          .sorted()
          .map(file -> parse.apply(read(file)))
          .toList();
    } catch (IOException unreadable) {
      throw new UncheckedIOException(
          new IOException("the role's eval cases unreadable: " + unreadable, unreadable));
    }
  }

  private static String read(Path file) {
    try {
      return Files.readString(file);
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }
}
