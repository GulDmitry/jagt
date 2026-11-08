package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.TrackerAssistant;
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

/**
 * Each task's ticket as first read, kept beside `state.json`: the model's copy is not byte-stable,
 * so a second read would change the round's inputs.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TicketTexts {

  private final TrackerAssistant assistant;
  private final UsageTracker usage;
  private final OrchestratorPaths paths;
  private final Map<String, Long> reads = new ConcurrentHashMap<>();

  /** A failed read is booked too: asking again every tick is paid for every tick. */
  public void read(String taskId, String ticketRef, long asOf) {
    Answer<String> answer = assistant.readTicketText(ticketRef);
    usage.chargeTask(taskId, answer.usage());
    reads.put(taskId, asOf);
    answer.facts().ifPresent(text -> keep(taskId, text));
  }

  public boolean readSince(String taskId, long since) {
    return Files.isRegularFile(file(taskId)) || reads.getOrDefault(taskId, -1L) >= since;
  }

  private void keep(String taskId, String text) {
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

  private Path file(String taskId) {
    return paths.ticketTexts().resolve(TaskName.slug(taskId) + ".md");
  }
}
