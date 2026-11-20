package fitness.gradle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** The value a function last measured, one file per function id. */
final class LastValues {

  private final Path directory;

  LastValues(Path directory) {
    this.directory = directory;
  }

  void record(String id, long value) {
    try {
      Files.createDirectories(directory);
      Files.writeString(directory.resolve(id), Long.toString(value), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  Optional<Long> read(String id) {
    Path file = directory.resolve(id);
    if (!Files.exists(file)) {
      return Optional.empty();
    }
    try {
      return Optional.of(Long.parseLong(Files.readString(file, StandardCharsets.UTF_8).strip()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
