package dev.jagt.orchestrator.service.project;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.service.Causes;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.task.text.AtomicFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.UnaryOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Changes the configuration file the human keeps and reads it back: a result that does not read is
 * replaced by the last good file, and every failure is a {@link ConfigWriteFailed}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ConfigWriter {

  private final ConfigService configService;
  private final OrchestratorPaths paths;

  /** Sets the value at {@code path}, e.g. {@code orchestrator, codeReview, postReviewReplies}. */
  public synchronized String set(List<String> path, Object value) {
    return rewrite(
        null,
        file -> SettingLines.changed(file, path, value),
        "Changed " + String.join(".", path.subList(1, path.size())) + " in jagt.yml");
  }

  /** {@code readBack} is the project the result must still hold, or null to read the file whole. */
  synchronized String rewrite(
      @Nullable String readBack, UnaryOperator<String> change, String done) {
    Path file = real(paths.configFile());
    createWhenAbsent(file);
    if (!Files.isWritable(file)) {
      throw failed(ConfigWriteFailed.Reason.NOT_WRITABLE, file, "", null);
    }
    try {
      String before = Files.readString(file, StandardCharsets.UTF_8);
      AtomicFiles.write(file, change.apply(before).getBytes(StandardCharsets.UTF_8));
      try {
        if (readBack == null) {
          configService.load();
        } else {
          configService.project(readBack);
        }
      } catch (RuntimeException unreadable) {
        putBack(file, before, unreadable);
      }
      return done;
    } catch (IOException e) {
      throw failed(ConfigWriteFailed.Reason.WRITE_FAILED, file, Causes.of(e), e);
    }
  }

  private static void createWhenAbsent(Path file) {
    if (Files.exists(file)) {
      return;
    }
    try {
      Files.writeString(file, "orchestrator:\n  projects: {}\n", StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw failed(ConfigWriteFailed.Reason.NOT_WRITABLE, file, Causes.of(e), e);
    }
  }

  private static void putBack(Path file, String before, RuntimeException unreadable) {
    try {
      AtomicFiles.write(file, before.getBytes(StandardCharsets.UTF_8));
    } catch (IOException cannotRestore) {
      throw failed(
          ConfigWriteFailed.Reason.UNREADABLE_NOT_RESTORED,
          file,
          Causes.of(cannotRestore),
          cannotRestore);
    }
    throw failed(
        ConfigWriteFailed.Reason.UNREADABLE_RESTORED, file, Causes.of(unreadable), unreadable);
  }

  private static ConfigWriteFailed failed(
      ConfigWriteFailed.Reason reason, Path file, String detail, @Nullable Throwable cause) {
    ConfigWriteFailed failure = new ConfigWriteFailed(reason, file, detail, cause);
    log.atWarn()
        .setMessage("config write failed")
        .addKeyValue("file", failure.file().getFileName())
        .addKeyValue("kind", failure.reason().name())
        .addKeyValue("cause", Causes.of(failure))
        .setCause(cause)
        .log();
    return failure;
  }

  /** Where a path really leads, so a link to a configured file is the same file. */
  static Path real(Path path) {
    try {
      return path.toRealPath();
    } catch (IOException e) {
      return path;
    }
  }
}
