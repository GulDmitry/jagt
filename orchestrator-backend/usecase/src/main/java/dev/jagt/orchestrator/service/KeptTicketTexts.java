package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.task.AtomicFiles;
import dev.jagt.orchestrator.task.TaskName;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** The ticket text kept per task beside `state.json`, and when its ticket was last read. */
@Component
@RequiredArgsConstructor
@Slf4j
public class KeptTicketTexts {

  private final OrchestratorPaths paths;
  private final Map<String, Long> reads = new ConcurrentHashMap<>();

  public void markRead(String taskId, long asOf) {
    reads.put(taskId, asOf);
  }

  public boolean readSince(String taskId, long since) {
    return Files.isRegularFile(file(taskId)) || reads.getOrDefault(taskId, -1L) >= since;
  }

  public void keep(String taskId, String text) {
    try {
      Path file = file(taskId);
      Files.createDirectories(file.getParent());
      AtomicFiles.write(file, text.getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      log.atWarn()
          .setMessage("ticket text keep failed")
          .addKeyValue("task", taskId)
          .addKeyValue("cause", Causes.of(e))
          .log();
    }
  }

  public Optional<String> of(String taskId) {
    try {
      Path file = file(taskId);
      return Files.isRegularFile(file)
          ? Optional.of(Files.readString(file, StandardCharsets.UTF_8))
          : Optional.empty();
    } catch (IOException e) {
      log.atWarn()
          .setMessage("ticket text read failed")
          .addKeyValue("task", taskId)
          .addKeyValue("cause", Causes.of(e))
          .log();
      return Optional.empty();
    }
  }

  public void forget(String taskId) {
    reads.remove(taskId);
    try {
      Files.deleteIfExists(file(taskId));
    } catch (IOException e) {
      log.atWarn()
          .setMessage("ticket text forget failed")
          .addKeyValue("task", taskId)
          .addKeyValue("cause", Causes.of(e))
          .addKeyValue("effect", "a later task on this ticket reads the old text")
          .log();
    }
  }

  private Path file(String taskId) {
    return paths.ticketTexts().resolve(TaskName.slug(taskId) + ".md");
  }
}
