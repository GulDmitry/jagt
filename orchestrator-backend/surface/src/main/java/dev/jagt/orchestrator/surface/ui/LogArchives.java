package dev.jagt.orchestrator.surface.ui;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The dated archives of one log: kept for {@link #MAX_AGE}, and no more than {@link #MAX_BYTES} in
 * total.
 */
final class LogArchives {

  static final Duration MAX_AGE = Duration.ofDays(14);
  static final long MAX_BYTES = 200L * 1024 * 1024;

  private static final DateTimeFormatter STAMP =
      DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss", Locale.ROOT).withZone(ZoneOffset.UTC);
  private static final String STAMP_SHAPE = "\\d{4}-\\d{2}-\\d{2}-\\d{6}";

  private LogArchives() {}

  static Path archiveName(Path previous, String name) throws IOException {
    Instant written = Files.getLastModifiedTime(previous).toInstant();
    return previous.resolveSibling(name + "." + STAMP.format(written));
  }

  static boolean isArchive(Path sibling, String name) {
    return sibling.getFileName().toString().matches(Pattern.quote(name + ".") + STAMP_SHAPE);
  }

  static void prune(Path directory, String name) throws IOException {
    Instant cutoff = Instant.now(Clock.system(ZoneOffset.UTC)).minus(MAX_AGE);
    List<Path> oldestFirst;
    try (Stream<Path> siblings = Files.list(directory)) {
      oldestFirst =
          siblings
              .filter(sibling -> isArchive(sibling, name))
              .filter(Files::isRegularFile)
              .sorted(Comparator.comparing(LogArchives::modified))
              .toList();
    }
    long total = 0;
    for (Path archive : oldestFirst) {
      total += Files.size(archive);
    }
    for (Path archive : oldestFirst) {
      if (modified(archive).isBefore(cutoff) || total > MAX_BYTES) {
        total -= Files.size(archive);
        Files.deleteIfExists(archive);
      }
    }
  }

  private static Instant modified(Path file) {
    try {
      return Files.getLastModifiedTime(file).toInstant();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
