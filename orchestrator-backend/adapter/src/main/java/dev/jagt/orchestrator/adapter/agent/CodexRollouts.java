package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.service.FileStamps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * One {@code rollout-*.jsonl} per session under {@code <home>/sessions/YYYY/MM/DD}; the newest is
 * the one going.
 */
final class CodexRollouts {

  private CodexRollouts() {}

  static Optional<Path> newest(Path codexHome) {
    Path sessions = codexHome.resolve("sessions");
    if (!Files.isDirectory(sessions)) {
      return Optional.empty();
    }
    try (Stream<Path> files = Files.walk(sessions, 4)) {
      return files
          .filter(CodexRollouts::isRollout)
          .max(Comparator.comparingLong(FileStamps::modified));
    } catch (IOException | RuntimeException unreadable) {
      return Optional.empty();
    }
  }

  private static boolean isRollout(Path file) {
    String name = file.getFileName().toString();
    return name.startsWith("rollout-") && name.endsWith(".jsonl");
  }
}
