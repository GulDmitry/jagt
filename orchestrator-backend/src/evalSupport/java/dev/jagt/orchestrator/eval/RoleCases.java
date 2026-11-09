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
    return in(Path.of("..", "roles", System.getProperty(ROLE, role)), parse);
  }

  static <T> List<T> in(Path folder, Function<String, T> parse) {
    return Stream.concat(
            cases(folder.resolve("eval")).stream(), cases(folder.resolve("eval.local")).stream())
        .map(file -> parse.apply(read(file)))
        .toList();
  }

  private static List<Path> cases(Path directory) {
    if (!Files.isDirectory(directory)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(directory)) {
      return files.filter(file -> file.getFileName().toString().endsWith(".yml")).sorted().toList();
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
