package dev.jagt.orchestrator.service.launch;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.task.launch.LaunchRequest;
import dev.jagt.orchestrator.task.launch.PendingStart;
import dev.jagt.orchestrator.task.launch.PendingStart.Placement;
import dev.jagt.orchestrator.task.text.AtomicFiles;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * The starts begun and not yet finished, kept in a file beside {@code state.json}. A start this
 * process still runs is no leftover: only entries it did not begin are interrupted.
 */
@Service
@Slf4j
public class PendingStarts {

  private static final Pattern PLAIN_KEY = Pattern.compile("[\\w.-]+");
  private static final TypeReference<List<PendingStart>> ENTRIES = new TypeReference<>() {};

  private final OrchestratorPaths paths;
  private final ObjectMapper mapper;
  private final Clock clock;
  private final Set<String> running = new HashSet<>();
  private boolean reported;

  public PendingStarts(OrchestratorPaths paths, ObjectMapper mapper, Clock clock) {
    this.paths = paths;
    this.mapper = mapper;
    this.clock = clock;
  }

  public String keyFor(LaunchRequest request) {
    String ref = request.ref();
    return ref != null && PLAIN_KEY.matcher(ref).matches()
        ? ref
        : "start-" + UUID.randomUUID().toString().substring(0, 8);
  }

  public synchronized void begin(String key, LaunchRequest request, Placement placement) {
    List<PendingStart> kept = new ArrayList<>(read());
    kept.removeIf(entry -> entry.key().equals(key));
    kept.add(new PendingStart(key, request, clock.millis(), placement));
    running.add(key);
    write(kept);
  }

  public synchronized void end(String key) {
    running.remove(key);
    List<PendingStart> kept = new ArrayList<>(read());
    if (kept.removeIf(entry -> entry.key().equals(key))) {
      write(kept);
    }
  }

  public synchronized List<PendingStart> interrupted() {
    return read().stream().filter(entry -> !running.contains(entry.key())).toList();
  }

  public synchronized Optional<PendingStart> find(String key) {
    return interrupted().stream().filter(entry -> entry.key().equals(key)).findFirst();
  }

  private List<PendingStart> read() {
    Path file = paths.layout().pendingStarts();
    if (!Files.exists(file)) {
      reported = true;
      return List.of();
    }
    try {
      List<PendingStart> entries = mapper.readValue(Files.readString(file), ENTRIES);
      reportOnce(entries);
      return entries;
    } catch (IOException | JacksonException unread) {
      log.atWarn()
          .setMessage("pending starts unread")
          .addKeyValue("file", file)
          .addKeyValue("cause", unread.toString())
          .addKeyValue("effect", "no interrupted start is listed")
          .log();
      return List.of();
    }
  }

  private void reportOnce(List<PendingStart> entries) {
    if (reported) {
      return;
    }
    reported = true;
    for (PendingStart start : entries) {
      log.atWarn()
          .setMessage("task start interrupted")
          .addKeyValue("key", start.key())
          .addKeyValue("cause", "the server stopped before the start finished")
          .addKeyValue("effect", "the board's head keeps it for resume or discard")
          .log();
    }
  }

  private void write(List<PendingStart> entries) {
    Path file = paths.layout().pendingStarts();
    try {
      if (entries.isEmpty()) {
        Files.deleteIfExists(file);
      } else {
        AtomicFiles.write(file, mapper.writeValueAsBytes(entries));
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot write " + file, e);
    }
  }
}
