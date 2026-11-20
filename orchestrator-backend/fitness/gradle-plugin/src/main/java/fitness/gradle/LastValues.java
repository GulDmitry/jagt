package fitness.gradle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
      Path temporary = Files.createTempFile(directory, id, ".tmp");
      Files.writeString(temporary, Long.toString(value), StandardCharsets.UTF_8);
      Files.move(temporary, directory.resolve(id), StandardCopyOption.ATOMIC_MOVE);
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
    } catch (NumberFormatException e) {
      return Optional.empty();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
