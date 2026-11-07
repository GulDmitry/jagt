package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Stamps every minute that jagt is alive, and keeps the stamp the previous process left: when jagt
 * went down.
 */
@Service
@Slf4j
public class AliveStamp implements Job {

  private final Path file;
  private final Clock clock;
  private final long lastAliveBeforeStart;

  public AliveStamp(OrchestratorPaths paths, Clock clock) {
    this.file = paths.alive();
    this.clock = clock;
    this.lastAliveBeforeStart = read(file);
  }

  @Override
  public String id() {
    return "alive-stamp";
  }

  @Override
  public String describe() {
    return "stamp that jagt is alive, so the next start knows when it went down";
  }

  @Override
  public Duration every() {
    return Duration.ofMinutes(1);
  }

  /**
   * Epoch millis of the last stamp before this process started; 0 where none was left or it could
   * not be read.
   */
  public long lastAliveBeforeStart() {
    return lastAliveBeforeStart;
  }

  @Override
  public void run() {
    try {
      Files.writeString(file, clock.instant().toString());
    } catch (IOException e) {
      log.atWarn()
          .setMessage("alive stamp not written")
          .addKeyValue("path", file)
          .addKeyValue("cause", e.toString())
          .addKeyValue("effect", "the next start reads jagt down since the last stamp written")
          .log();
    }
  }

  private static long read(Path file) {
    if (!Files.isRegularFile(file)) {
      return 0;
    }
    try {
      return Instant.parse(Files.readString(file).strip()).toEpochMilli();
    } catch (IOException | DateTimeParseException e) {
      log.atWarn()
          .setMessage("alive stamp unread")
          .addKeyValue("path", file)
          .addKeyValue("cause", e.toString())
          .addKeyValue("effect", "this start reads no last alive stamp")
          .log();
      return 0;
    }
  }
}
